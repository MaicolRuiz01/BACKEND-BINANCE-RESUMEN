package com.binance.web.serviceImpl;

import com.binance.web.Entity.*;
import com.binance.web.Repository.*;
import com.binance.web.dto.*;
import com.binance.web.service.RetiradorService;
import com.binance.web.service.TelegramService;
import com.binance.web.util.CupoDiarioRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import com.binance.web.util.HttpClientFactory;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RetiradorServiceImpl implements RetiradorService {

    private static final double PAGO_CAJERO = 2.0; // $2.000 COP
    private static final double PAGO_CORRESPONSAL = 3.0; // $3.000 COP
    private static final double PAGO_COMPLETO = 4.0; // $4.000 COP (cajero + corresponsal)
    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    private final RetiradorRepository retiradorRepository;
    private final SolicitudRetiroRepository solicitudRepository;
    private final AccountCopRepository accountCopRepository;
    private final EfectivoRepository efectivoRepository;
    private final MovimientoRepository movimientoRepository;
    private final RestTemplate restTemplate = HttpClientFactory.timed();
    private final TelegramService telegramService;

    // Mapa para recordar el message_id del último recordatorio enviado a cada
    // retirador (ID_Retirador -> Message_ID)
    private final Map<Long, Integer> lastReminderMessageIds = new ConcurrentHashMap<>();

    public RetiradorServiceImpl(RetiradorRepository retiradorRepository,
            SolicitudRetiroRepository solicitudRepository,
            AccountCopRepository accountCopRepository,
            EfectivoRepository efectivoRepository,
            MovimientoRepository movimientoRepository,
            TelegramService telegramService) {
        this.retiradorRepository = retiradorRepository;
        this.solicitudRepository = solicitudRepository;
        this.accountCopRepository = accountCopRepository;
        this.efectivoRepository = efectivoRepository;
        this.movimientoRepository = movimientoRepository;
        this.telegramService = telegramService;
    }

    @Value("${app.n8n.webhook-url:}")
    private String n8nWebhookUrl;

    @Value("${app.n8n.webhook-general-url:}")
    private String n8nWebhookGeneralUrl;

    @Value("${app.telegram.group-chat-id:}")
    private String telegramGroupChatId;

    @Override
    public List<Retirador> findAll() {
        return retiradorRepository.findAll();
    }

    @Override
    public Retirador findById(Long id) {
        return retiradorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Retirador no encontrado: " + id));
    }

    @Override
    @Transactional
    public Retirador save(Retirador retirador) {
        if (retirador.getNombre() == null || retirador.getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del retirador es requerido");
        }
        if (retirador.getSaldoPendiente() == null) {
            retirador.setSaldoPendiente(0.0);
        }

        boolean esNuevo = (retirador.getId() == null);

        // Si tiene efectivo_id, validar que exista
        if (retirador.getEfectivo() != null && retirador.getEfectivo().getId() != null) {
            efectivoRepository.findById(retirador.getEfectivo().getId())
                    .orElseThrow(() -> new RuntimeException(
                            "Caja no encontrada con ID: " + retirador.getEfectivo().getId()));
        }

        Retirador saved = retiradorRepository.save(retirador);

        // ── Auto-crear caja si es nuevo y no tiene una asignada ──
        if (esNuevo && saved.getEfectivo() == null) {
            Efectivo caja = new Efectivo();
            caja.setName("Caja - " + saved.getNombre());
            caja.setSaldo(0.0);
            caja.setSaldoInicialDelDia(0.0);
            Efectivo cajaSaved = efectivoRepository.save(caja);

            saved.setEfectivo(cajaSaved);
            saved = retiradorRepository.save(saved);
            log.info("[Retirador] Caja auto-creada '{}' para {}", cajaSaved.getName(), saved.getNombre());
        }

        return saved;
    }

    @Override
    public void delete(Long id) {
        Retirador retirador = findById(id);

        List<SolicitudRetiro> solicitudes = solicitudRepository.findByRetiradorIdOrderByFechaCreacionDesc(id);
        for (SolicitudRetiro s : solicitudes) {
            if (s.getEstado() == EstadoSolicitud.COMPLETADO) {
                throw new RuntimeException("No se puede eliminar el retirador porque ya tiene retiros completados.");
            } else {
                s.setRetirador(null);
                s.setEstado(EstadoSolicitud.SIN_ASIGNAR);
                solicitudRepository.save(s);
            }
        }

        Efectivo caja = retirador.getEfectivo();
        if (caja != null) {
            retirador.setEfectivo(null);
            caja.setRetirador(null);
            retiradorRepository.save(retirador); // Guardar para romper la llave foranea
            efectivoRepository.delete(caja); // Eliminar la caja huerfana
        }

        retiradorRepository.delete(retirador);
    }

    @Override
    @Transactional
    public SolicitudRetiro crearSolicitud(SolicitudRetiroRequestDto request) {
        Retirador retirador = findById(request.getRetiradorId());

        SolicitudRetiro solicitud = new SolicitudRetiro();
        solicitud.setRetirador(retirador);
        solicitud.setFechaCreacion(LocalDateTime.now(ZONE_BOGOTA));
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);

        List<DetalleRetiro> detalles = buildDetalles(request.getDetalles(), solicitud);
        validarSaldoSuficiente(detalles);
        double totalMonto = detalles.stream().mapToDouble(DetalleRetiro::totalDetalle).sum();
        double pagoRetirador = detalles.stream().mapToDouble(d -> calcularPago(d.getTipoRetiro())).sum();

        solicitud.setTotalMonto(totalMonto);
        solicitud.setPagoRetirador(pagoRetirador);
        solicitud.setDetalles(detalles);

        SolicitudRetiro saved = solicitudRepository.save(solicitud);
        notificarN8n(saved, retirador, n8nWebhookUrl);
        return saved;
    }

    /**
     * Verifica que cada cuenta tenga saldo DISPONIBLE suficiente para cubrir el
     * monto que se quiere solicitar en retiro. No descuenta nada aquí — el
     * descuento real ocurre únicamente cuando el retirador confirma el retiro
     * (confirmarSolicitud), esto solo evita crear/notificar una solicitud por
     * encima de lo disponible.
     *
     * "Disponible" = saldo de la cuenta MENOS lo que ya está comprometido en
     * otras solicitudes SIN_ASIGNAR o PENDIENTE de esa misma cuenta (dinero
     * que ya se pidió retirar pero el retirador todavía no confirmó). Esto
     * evita que se pueda mandar dos veces una solicitud sobre el mismo dinero
     * antes de que la primera se confirme.
     */
    private void validarSaldoSuficiente(List<DetalleRetiro> detalles) {
        for (DetalleRetiro detalle : detalles) {
            AccountCop cuenta = detalle.getCuentaCop();
            double comprometido = solicitudRepository.sumComprometidoPorCuenta(cuenta.getId());
            double disponible = cuenta.getBalance() - comprometido;
            if (disponible < detalle.totalDetalle()) {
                throw new IllegalArgumentException(
                        "Saldo insuficiente en la cuenta " + cuenta.getName() + " (saldo: $"
                                + String.format("%,.0f", cuenta.getBalance())
                                + ", ya comprometido en retiros pendientes de confirmar: $"
                                + String.format("%,.0f", comprometido)
                                + ", disponible: $" + String.format("%,.0f", disponible)
                                + ", solicitado: $" + String.format("%,.0f", detalle.totalDetalle()) + ")");
            }

            validarCupoDiario(detalle, cuenta);
        }
    }

    /**
     * Límite diario por tipo de retiro, por cuenta (ej: Bancolombia hoy = $2.700
     * cajero / $10.000 corresponsal — ver CupoDiarioRules). Se valida por
     * separado: en un retiro COMPLETO, el monto de cajero se descuenta del cupo
     * de cajero y el de corresponsal del cupo de corresponsal, cada uno con su
     * propio tope (no es un tope combinado).
     *
     * Igual que con el saldo, se resta lo que ya está comprometido en otras
     * solicitudes SIN_ASIGNAR/PENDIENTE de esa cuenta para no permitir que dos
     * solicitudes sin confirmar, sumadas, superen el cupo del día.
     */
    private void validarCupoDiario(DetalleRetiro detalle, AccountCop cuenta) {
        if (cuenta.getBankType() == null) {
            // Cuenta sin banco configurado (ej: cuentas de prueba viejas) — no hay
            // regla de cupo diario que aplicarle, no bloqueamos por esto.
            return;
        }
        CupoDiarioRules.asegurarCupoHoy(cuenta);
        accountCopRepository.save(cuenta);

        if (detalle.getMontoCajero() != null && detalle.getMontoCajero() > 0) {
            double comprometidoCajero = solicitudRepository.sumMontoCajeroComprometidoPorCuenta(cuenta.getId());
            double cupoHoy = cuenta.getCupoCajeroDisponibleHoy() != null ? cuenta.getCupoCajeroDisponibleHoy() : 0.0;
            double disponibleCajero = cupoHoy - comprometidoCajero;
            if (detalle.getMontoCajero() > disponibleCajero) {
                throw new IllegalArgumentException(
                        "Cupo diario de CAJERO agotado en la cuenta " + cuenta.getName() + " (cupo de hoy: $"
                                + String.format("%,.0f", cupoHoy)
                                + ", ya comprometido en solicitudes pendientes: $"
                                + String.format("%,.0f", comprometidoCajero)
                                + ", disponible: $" + String.format("%,.0f", disponibleCajero)
                                + ", solicitado: $" + String.format("%,.0f", detalle.getMontoCajero()) + ")");
            }
        }

        if (detalle.getMontoCorresponsal() != null && detalle.getMontoCorresponsal() > 0) {
            double comprometidoCorresponsal = solicitudRepository.sumMontoCorresponsalComprometidoPorCuenta(cuenta.getId());
            double cupoHoy = cuenta.getCupoCorresponsalDisponibleHoy() != null ? cuenta.getCupoCorresponsalDisponibleHoy() : 0.0;
            double disponibleCorresponsal = cupoHoy - comprometidoCorresponsal;
            if (detalle.getMontoCorresponsal() > disponibleCorresponsal) {
                throw new IllegalArgumentException(
                        "Cupo diario de CORRESPONSAL agotado en la cuenta " + cuenta.getName() + " (cupo de hoy: $"
                                + String.format("%,.0f", cupoHoy)
                                + ", ya comprometido en solicitudes pendientes: $"
                                + String.format("%,.0f", comprometidoCorresponsal)
                                + ", disponible: $" + String.format("%,.0f", disponibleCorresponsal)
                                + ", solicitado: $" + String.format("%,.0f", detalle.getMontoCorresponsal()) + ")");
            }
        }
    }

    @Override
    public List<CuentaComprometidoDto> obtenerMontosComprometidos() {
        List<DetalleRetiro> detalles = solicitudRepository.findDetallesComprometidos();
        Map<Integer, List<DetalleRetiro>> porCuenta = detalles.stream()
                .collect(Collectors.groupingBy(d -> d.getCuentaCop().getId()));

        List<CuentaComprometidoDto> result = new ArrayList<>();
        for (Map.Entry<Integer, List<DetalleRetiro>> entry : porCuenta.entrySet()) {
            double total = entry.getValue().stream().mapToDouble(DetalleRetiro::totalDetalle).sum();
            // Desglosado por canal, para poder netear el cupo diario de cajero y de
            // corresponsal por separado (un retiro pendiente por cajero no debe
            // descontar cupo de corresponsal, y viceversa).
            double totalCajero = entry.getValue().stream()
                    .mapToDouble(d -> d.getMontoCajero() != null ? d.getMontoCajero() : 0.0)
                    .sum();
            double totalCorresponsal = entry.getValue().stream()
                    .mapToDouble(d -> d.getMontoCorresponsal() != null ? d.getMontoCorresponsal() : 0.0)
                    .sum();
            List<SolicitudComprometidaDto> solicitudes = entry.getValue().stream()
                    .map(d -> new SolicitudComprometidaDto(
                            d.getSolicitud().getId(),
                            d.totalDetalle(),
                            d.getSolicitud().getEstado().name(),
                            d.getSolicitud().getFechaCreacion(),
                            d.getSolicitud().getRetirador() != null
                                    ? d.getSolicitud().getRetirador().getNombre()
                                    : null))
                    .collect(Collectors.toList());
            result.add(new CuentaComprometidoDto(entry.getKey(), total, totalCajero, totalCorresponsal, solicitudes));
        }
        return result;
    }

    @Override
    @Transactional
    public SolicitudRetiro confirmarSolicitud(Long solicitudId) {
        SolicitudRetiro solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + solicitudId));

        if (solicitud.getEstado() == EstadoSolicitud.COMPLETADO)
            throw new IllegalStateException("La solicitud ya fue confirmada.");
        if (solicitud.getEstado() == EstadoSolicitud.CANCELADO)
            throw new IllegalStateException("Esta solicitud fue cancelada, no se puede confirmar.");
        if (solicitud.getRetirador() == null)
            throw new IllegalStateException("La solicitud aún no tiene un retirador asignado.");

        // Validar saldo Y cupo diario suficiente en TODAS las cuentas antes de restar nada.
        // El cupo se revalida acá (no solo al crear la solicitud) porque puede haber
        // pasado tiempo entre que se creó y se confirma, y mientras tanto el cupo
        // pudo haberse gastado por otro retiro (directo o de otra solicitud).
        for (DetalleRetiro detalle : solicitud.getDetalles()) {
            AccountCop cuenta = detalle.getCuentaCop();
            if (cuenta.getBalance() < detalle.totalDetalle()) {
                throw new IllegalStateException(
                        "Saldo insuficiente en la cuenta " + cuenta.getName() + " (disponible: $"
                                + String.format("%,.0f", cuenta.getBalance()) + ", requerido: $"
                                + String.format("%,.0f", detalle.totalDetalle()) + ")");
            }

            // Cuentas sin banco configurado (ej: cuentas de prueba viejas) no tienen
            // regla de cupo diario que aplicarles — no bloqueamos por esto.
            if (cuenta.getBankType() != null) {
                CupoDiarioRules.asegurarCupoHoy(cuenta);
                if (detalle.getMontoCajero() != null && detalle.getMontoCajero() > 0) {
                    double disp = cuenta.getCupoCajeroDisponibleHoy() != null ? cuenta.getCupoCajeroDisponibleHoy() : 0.0;
                    if (detalle.getMontoCajero() > disp) {
                        throw new IllegalStateException(
                                "Cupo diario de CAJERO agotado en la cuenta " + cuenta.getName() + " (disponible: $"
                                        + String.format("%,.0f", disp) + ", requerido: $"
                                        + String.format("%,.0f", detalle.getMontoCajero()) + ")");
                    }
                }
                if (detalle.getMontoCorresponsal() != null && detalle.getMontoCorresponsal() > 0) {
                    double disp = cuenta.getCupoCorresponsalDisponibleHoy() != null ? cuenta.getCupoCorresponsalDisponibleHoy() : 0.0;
                    if (detalle.getMontoCorresponsal() > disp) {
                        throw new IllegalStateException(
                                "Cupo diario de CORRESPONSAL agotado en la cuenta " + cuenta.getName() + " (disponible: $"
                                        + String.format("%,.0f", disp) + ", requerido: $"
                                        + String.format("%,.0f", detalle.getMontoCorresponsal()) + ")");
                    }
                }
            }
        }

        Retirador retirador = solicitud.getRetirador();
        Efectivo caja = retirador.getEfectivo();
        LocalDateTime ahora = LocalDateTime.now(ZONE_BOGOTA);

        for (DetalleRetiro detalle : solicitud.getDetalles()) {
            AccountCop cuenta = detalle.getCuentaCop();

            // 4x1000 sobre la salida de cuenta COP. En BANCOLOMBIA se DIFIERE al día siguiente
            // (el scheduler lo aplica sobre los movimientos con comisionAplicada=false); en los
            // demás bancos se descuenta al instante. Mismo criterio que el retiro manual.
            double montoDet = detalle.totalDetalle();
            boolean esBanco = "BANCOLOMBIA".equalsIgnoreCase(String.valueOf(cuenta.getBankType()));
            double deduccionHoy = esBanco ? montoDet : (montoDet + montoDet * 0.004);
            cuenta.setBalance(cuenta.getBalance() - deduccionHoy);

            if (cuenta.getBankType() != null) {
                if (detalle.getMontoCajero() != null && detalle.getMontoCajero() > 0) {
                    double disp = cuenta.getCupoCajeroDisponibleHoy() != null ? cuenta.getCupoCajeroDisponibleHoy() : 0.0;
                    cuenta.setCupoCajeroDisponibleHoy(disp - detalle.getMontoCajero());
                }
                if (detalle.getMontoCorresponsal() != null && detalle.getMontoCorresponsal() > 0) {
                    double disp = cuenta.getCupoCorresponsalDisponibleHoy() != null ? cuenta.getCupoCorresponsalDisponibleHoy() : 0.0;
                    cuenta.setCupoCorresponsalDisponibleHoy(disp - detalle.getMontoCorresponsal());
                }
                // Sincronizar el campo legacy (suma de ambos cupos).
                double cajeroRest = cuenta.getCupoCajeroDisponibleHoy() != null ? cuenta.getCupoCajeroDisponibleHoy() : 0.0;
                double corresponsalRest = cuenta.getCupoCorresponsalDisponibleHoy() != null ? cuenta.getCupoCorresponsalDisponibleHoy() : 0.0;
                cuenta.setCupoDisponibleHoy(cajeroRest + corresponsalRest);
            }

            accountCopRepository.save(cuenta);

            // Registrar el/los movimiento(s) de RETIRO para que aparezcan en el historial de la
            // cuenta y de la caja. Antes NO se creaba movimiento → el saldo se movía "invisible".
            if (detalle.getMontoCajero() != null && detalle.getMontoCajero() > 0) {
                movimientoRepository.save(Movimiento.builder()
                        .tipo("RETIRO CAJERO")
                        .fecha(ahora)
                        .monto(detalle.getMontoCajero())
                        .cuentaOrigen(cuenta)
                        .caja(caja)
                        .comision(detalle.getMontoCajero() * 0.004)
                        .comisionAplicada(!esBanco)
                        .build());
            }
            if (detalle.getMontoCorresponsal() != null && detalle.getMontoCorresponsal() > 0) {
                movimientoRepository.save(Movimiento.builder()
                        .tipo("RETIRO CORRESPONSAL")
                        .fecha(ahora)
                        .monto(detalle.getMontoCorresponsal())
                        .cuentaOrigen(cuenta)
                        .caja(caja)
                        .comision(detalle.getMontoCorresponsal() * 0.004)
                        .comisionAplicada(!esBanco)
                        .build());
            }
        }

        if (caja != null) {
            caja.setSaldo(caja.getSaldo() + solicitud.getTotalMonto());
            efectivoRepository.save(caja);
        }

        retirador.setSaldoPendiente(retirador.getSaldoPendiente() + solicitud.getPagoRetirador());
        retiradorRepository.save(retirador);

        solicitud.setEstado(EstadoSolicitud.COMPLETADO);
        return solicitudRepository.save(solicitud);
    }

    @Override
    public List<SolicitudRetiro> historialPorRetirador(Long retiradorId) {
        return solicitudRepository.findByRetiradorIdOrderByFechaCreacionDesc(retiradorId);
    }

    @Override
    @Transactional
    public SolicitudRetiro reenviarSolicitud(Long solicitudId) {
        SolicitudRetiro solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + solicitudId));

        if (solicitud.getEstado() == EstadoSolicitud.COMPLETADO) {
            throw new IllegalStateException("La solicitud ya fue completada, no se puede reenviar.");
        }
        if (solicitud.getEstado() == EstadoSolicitud.CANCELADO) {
            throw new IllegalStateException("Esta solicitud fue cancelada, no se puede reenviar.");
        }
        if (solicitud.getRetirador() == null) {
            throw new IllegalStateException(
                    "Esta solicitud no tiene un retirador asignado (es una solicitud general sin asignar).");
        }

        notificarN8n(solicitud, solicitud.getRetirador(), n8nWebhookUrl);
        return solicitud;
    }

    @Override
    @Transactional
    public SolicitudRetiro cancelarSolicitud(Long solicitudId) {
        SolicitudRetiro solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + solicitudId));

        if (solicitud.getEstado() == EstadoSolicitud.COMPLETADO) {
            throw new IllegalStateException(
                    "No se puede cancelar: el retiro ya fue completado y el dinero ya se movió.");
        }
        if (solicitud.getEstado() == EstadoSolicitud.CANCELADO) {
            throw new IllegalStateException("Esta solicitud ya estaba cancelada.");
        }

        // No hay que revertir ningún saldo: el dinero solo se descuenta al
        // confirmar (COMPLETADO), así que cancelar antes de eso no toca cuentas ni
        // caja.
        Retirador retirador = solicitud.getRetirador();

        solicitud.setEstado(EstadoSolicitud.CANCELADO);
        solicitudRepository.save(solicitud);

        // Borra el mensaje del grupo por completo (esté o no ya tomada), como si la
        // solicitud nunca hubiera existido — nada de dejarlo editado con texto de
        // "cancelada" ni con botones activos sueltos.
        if (solicitud.getTelegramMessageId() != null
                && telegramGroupChatId != null && !telegramGroupChatId.isBlank()) {
            telegramService.deleteMessage(telegramGroupChatId, solicitud.getTelegramMessageId());
        }

        // Borra el mensaje privado original ("Nueva Solicitud de Retiro...") por
        // completo, en vez de editarlo, para que no quede nada visible ni botones
        // activos con los que se pueda interactuar después de cancelada.
        if (retirador != null && retirador.getTelegramChatId() != null) {
            if (solicitud.getTelegramPrivateMessageId() != null) {
                telegramService.deleteMessage(
                        String.valueOf(retirador.getTelegramChatId()),
                        solicitud.getTelegramPrivateMessageId());
            } else {
                // No teníamos guardado el message_id (solicitud antigua) — no hay nada que
                // borrar, así que avisamos aparte con un mensaje nuevo.
                telegramService.sendMessage(String.valueOf(retirador.getTelegramChatId()),
                        "🚫 *Solicitud #" + solicitud.getId() + " cancelada.*");
            }
        }

        log.info("[Retiro] Solicitud #{} cancelada manualmente desde la app.", solicitudId);
        return solicitud;
    }

    // ═══════════════════════════════════════════════════════════════
    // Solicitud general (sin retirador)
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public SolicitudRetiro crearSolicitudGeneral(SolicitudGeneralRequestDto request) {
        SolicitudRetiro solicitud = new SolicitudRetiro();
        solicitud.setRetirador(null);
        solicitud.setFechaCreacion(LocalDateTime.now(ZONE_BOGOTA));
        solicitud.setEstado(EstadoSolicitud.SIN_ASIGNAR);

        // Convertir DetalleDto de SolicitudGeneralRequestDto al formato interno
        List<DetalleRetiro> detalles = new ArrayList<>();
        for (SolicitudGeneralRequestDto.DetalleDto dto : request.getDetalles()) {
            AccountCop cuenta = accountCopRepository.findById(dto.getCuentaCopId())
                    .orElseThrow(() -> new RuntimeException("Cuenta COP no encontrada: " + dto.getCuentaCopId()));
            DetalleRetiro d = new DetalleRetiro();
            d.setSolicitud(solicitud);
            d.setCuentaCop(cuenta);
            d.setTipoRetiro(dto.getTipoRetiro());
            d.setMontoCajero(dto.getMontoCajero());
            d.setMontoCorresponsal(dto.getMontoCorresponsal());
            detalles.add(d);
        }

        validarSaldoSuficiente(detalles);
        double totalMonto = detalles.stream().mapToDouble(DetalleRetiro::totalDetalle).sum();
        double pagoRetirador = detalles.stream().mapToDouble(d -> calcularPago(d.getTipoRetiro())).sum();

        solicitud.setTotalMonto(totalMonto);
        solicitud.setPagoRetirador(pagoRetirador);
        solicitud.setDetalles(detalles);

        SolicitudRetiro saved = solicitudRepository.save(solicitud);

        // Notificar al grupo de Telegram con botón inline
        notificarGeneralN8n(saved);
        return saved;
    }

    @Override
    @Transactional
    public SolicitudRetiro asignarRetirador(Long solicitudId, AsignarRetiradorDto dto) {
        SolicitudRetiro solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + solicitudId));

        if (solicitud.getEstado() != EstadoSolicitud.SIN_ASIGNAR)
            throw new IllegalStateException("La solicitud ya fue asignada o está completada.");

        Retirador retirador = findById(dto.getRetiradorId());

        // Si viene de Telegram, validar que el username coincida (opcional pero seguro)
        if (dto.getTelegramUsername() != null && !dto.getTelegramUsername().isBlank()) {
            if (retirador.getTelegramUsername() == null ||
                    !retirador.getTelegramUsername().equalsIgnoreCase(dto.getTelegramUsername())) {
                throw new IllegalArgumentException(
                        "El usuario de Telegram '" + dto.getTelegramUsername() + "' no coincide con el retirador.");
            }
        }

        solicitud.setRetirador(retirador);
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        SolicitudRetiro saved = solicitudRepository.save(solicitud);

        log.info("[Solicitud General #{}] Asignada a {}", solicitudId, retirador.getNombre());
        // Notificar al retirador individualmente (webhook existente)
        notificarN8n(saved, retirador, n8nWebhookUrl);
        return saved;
    }

    @Override
    public List<SolicitudRetiro> getSolicitudesSinAsignar() {
        return solicitudRepository.findByEstadoOrderByFechaCreacionDesc(EstadoSolicitud.SIN_ASIGNAR);
    }

    // ═══════════════════════════════════════════════════════════════
    // Pago al retirador
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public Retirador pagarRetirador(Long retiradorId, PagoRetiradorDto dto) {
        Retirador retirador = findById(retiradorId);

        if (dto.getMonto() == null || dto.getMonto() <= 0)
            throw new IllegalArgumentException("El monto debe ser mayor a 0");
        if (dto.getMonto() > retirador.getSaldoPendiente())
            throw new IllegalArgumentException("El monto supera el saldo pendiente del retirador");

        if ("COP".equalsIgnoreCase(dto.getFuente())) {
            AccountCop cuenta = accountCopRepository.findById(dto.getCuentaCopId())
                    .orElseThrow(() -> new RuntimeException("Cuenta COP no encontrada: " + dto.getCuentaCopId()));
            // 4x1000 sobre la salida de cuenta COP: diferido en BANCOLOMBIA, inmediato en los demás.
            boolean esBanco = "BANCOLOMBIA".equalsIgnoreCase(String.valueOf(cuenta.getBankType()));
            double comision = dto.getMonto() * 0.004;
            double deduccionHoy = esBanco ? dto.getMonto() : (dto.getMonto() + comision);
            if (cuenta.getBalance() < deduccionHoy)
                throw new IllegalStateException("Saldo insuficiente en la cuenta COP");
            cuenta.setBalance(cuenta.getBalance() - deduccionHoy);
            accountCopRepository.save(cuenta);

            movimientoRepository.save(Movimiento.builder()
                    .tipo("PAGO RETIRADOR")
                    .fecha(LocalDateTime.now(ZONE_BOGOTA))
                    .monto(dto.getMonto())
                    .cuentaOrigen(cuenta)
                    .comision(comision)
                    .comisionAplicada(!esBanco)
                    .build());

        } else if ("CAJA".equalsIgnoreCase(dto.getFuente())) {
            Efectivo caja = efectivoRepository.findById(dto.getCajaId())
                    .orElseThrow(() -> new RuntimeException("Caja no encontrada: " + dto.getCajaId()));
            if (caja.getSaldo() < dto.getMonto())
                throw new IllegalStateException("Saldo insuficiente en la caja");
            caja.setSaldo(caja.getSaldo() - dto.getMonto());
            efectivoRepository.save(caja);

            // Pago en efectivo: sin 4x1000, pero se registra el movimiento para el historial.
            movimientoRepository.save(Movimiento.builder()
                    .tipo("PAGO RETIRADOR")
                    .fecha(LocalDateTime.now(ZONE_BOGOTA))
                    .monto(dto.getMonto())
                    .caja(caja)
                    .comision(0.0)
                    .comisionAplicada(true)
                    .build());

        } else {
            throw new IllegalArgumentException("Fuente inválida. Use 'COP' o 'CAJA'");
        }

        retirador.setSaldoPendiente(retirador.getSaldoPendiente() - dto.getMonto());
        Retirador saved = retiradorRepository.save(retirador);
        log.info("[Pago retirador] {} pagado: ${} desde {}", retirador.getNombre(), dto.getMonto(), dto.getFuente());
        return saved;
    }

    // ═══════════════════════════════════════════════════════════════
    // Ranking semanal
    // ═══════════════════════════════════════════════════════════════

    @Override
    public List<RankingRetiradorDto> getRankingSemana() {
        LocalDateTime[] rango = rangoDeSemana();
        List<Object[]> rows = solicitudRepository.rankingPorMonto(rango[0], rango[1]);

        Map<Long, Retirador> retiradorMap = retiradorRepository.findAll()
                .stream().collect(Collectors.toMap(Retirador::getId, r -> r));

        List<RankingRetiradorDto> ranking = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Long retiradorId = (Long) rows.get(i)[0];
            Double total = (Double) rows.get(i)[1];
            Retirador r = retiradorMap.get(retiradorId);
            if (r != null) {
                ranking.add(new RankingRetiradorDto(retiradorId, r.getNombre(), total, i + 1));
            }
        }
        return ranking;
    }

    @Override
    @Transactional
    public Retirador aplicarBono(Long retiradorId, double monto) {
        Retirador retirador = findById(retiradorId);
        retirador.setSaldoPendiente(retirador.getSaldoPendiente() + monto);
        Retirador saved = retiradorRepository.save(retirador);
        log.info("[Bono] ${} aplicado a {}", monto, retirador.getNombre());
        return saved;
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers privados
    // ═══════════════════════════════════════════════════════════════

    private List<DetalleRetiro> buildDetalles(
            List<SolicitudRetiroRequestDto.DetalleDto> dtos, SolicitudRetiro solicitud) {
        List<DetalleRetiro> result = new ArrayList<>();
        for (SolicitudRetiroRequestDto.DetalleDto dto : dtos) {
            AccountCop cuenta = accountCopRepository.findById(dto.getCuentaCopId())
                    .orElseThrow(() -> new RuntimeException("Cuenta COP no encontrada: " + dto.getCuentaCopId()));
            DetalleRetiro d = new DetalleRetiro();
            d.setSolicitud(solicitud);
            d.setCuentaCop(cuenta);
            d.setTipoRetiro(dto.getTipoRetiro());
            d.setMontoCajero(dto.getMontoCajero());
            d.setMontoCorresponsal(dto.getMontoCorresponsal());
            result.add(d);
        }
        return result;
    }

    private double calcularPago(TipoRetiro tipo) {
        return switch (tipo) {
            case CAJERO -> PAGO_CAJERO;
            case CORRESPONSAL -> PAGO_CORRESPONSAL;
            case COMPLETO -> PAGO_COMPLETO;
        };
    }

    private LocalDateTime[] rangoDeSemana() {
        LocalDate hoy = LocalDate.now(ZONE_BOGOTA);
        LocalDate lunes = hoy.with(java.time.DayOfWeek.MONDAY);
        LocalDate domingo = lunes.plusDays(6);
        return new LocalDateTime[] {
                lunes.atStartOfDay(),
                domingo.atTime(23, 59, 59)
        };
    }

    private void notificarN8n(SolicitudRetiro solicitud, Retirador retirador, String url) {
        if (url == null || url.isBlank()) {
            log.warn("[Retiro] n8n webhook no configurado — notificación omitida.");
            return;
        }
        try {
            List<Map<String, Object>> cuentas = new ArrayList<>();
            for (DetalleRetiro d : solicitud.getDetalles()) {
                Map<String, Object> item = new HashMap<>();
                item.put("cuenta", d.getCuentaCop().getName());
                item.put("banco", d.getCuentaCop().getBankType().name());
                item.put("tipo", d.getTipoRetiro().name());
                item.put("montoCajero", d.getMontoCajero());
                item.put("montoCorresponsal", d.getMontoCorresponsal());
                cuentas.add(item);
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("solicitudId", solicitud.getId());
            payload.put("retirador", retirador.getNombre());
            payload.put("totalMonto", solicitud.getTotalMonto());
            payload.put("pagoRetirador", solicitud.getPagoRetirador());
            payload.put("cuentas", cuentas);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForObject(url, new HttpEntity<>(payload, headers), String.class);
            log.info("[Retiro] Notificación enviada a n8n — solicitud #{}", solicitud.getId());
        } catch (Exception e) {
            log.error("[Retiro] Error al notificar n8n: {}", e.getMessage());
        }

        // Notificación adicional directa por Telegram al retirador (si tiene chat
        // vinculado, es decir, si ya le dio /start al bot)
        boolean telegramEnviado = false;
        try {
            if (retirador.getTelegramChatId() != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("🔔 *Nueva Solicitud de Retiro #").append(solicitud.getId()).append("*\n");
                sb.append("👤 *Retirador:* ").append(retirador.getNombre());
                if (retirador.getTelegramUsername() != null && !retirador.getTelegramUsername().isBlank()) {
                    sb.append(" (@").append(retirador.getTelegramUsername()).append(")");
                }
                sb.append("\n");
                sb.append("💰 *Total:* $").append(String.format("%,.0f", solicitud.getTotalMonto()))
                        .append(" COP\n");
                sb.append("🏦 ");
                for (int i = 0; i < solicitud.getDetalles().size(); i++) {
                    DetalleRetiro d = solicitud.getDetalles().get(i);
                    if (i > 0)
                        sb.append(", ");
                    sb.append(d.getCuentaCop().getName())
                            .append(" (").append(d.getCuentaCop().getBankType().name()).append(")");
                }
                Integer privateMessageId = telegramService.sendMessageWithTwoButtons(
                        String.valueOf(retirador.getTelegramChatId()),
                        sb.toString(),
                        "✅ Ya hice el retiro", "completed:" + solicitud.getId(),
                        "❌ Cancelar", "cancel:" + solicitud.getId());
                // Guardamos el message_id para poder editar este mensaje después
                // (ej: quitarle los botones si se cancela desde la app).
                solicitud.setTelegramPrivateMessageId(privateMessageId);
                solicitudRepository.save(solicitud);
                telegramEnviado = true;
            } else {
                log.warn(
                        "[Retiro] Solicitud #{}: {} (@{}) todavía no ha vinculado su Telegram (no le ha dado /start al bot) — no se pudo notificar directamente. Usa 'Reenviar' cuando lo haga.",
                        solicitud.getId(), retirador.getNombre(), retirador.getTelegramUsername());
            }
        } catch (Exception e) {
            log.error("[Retiro] Error al notificar por Telegram: {}", e.getMessage());
        }
        solicitud.setTelegramNotificado(telegramEnviado);
    }

    /**
     * Notifica la solicitud general por los dos canales: webhook de n8n (como
     * antes)
     * y mensaje al GRUPO de Telegram con botón inline "✅ Aceptar" (el primer
     * retirador
     * que pulse el botón queda asignado, lógica en TelegramWebhookService).
     * Guarda el message_id devuelto por Telegram para poder editar el mensaje
     * después.
     */
    private void notificarGeneralN8n(SolicitudRetiro solicitud) {
        List<Map<String, Object>> cuentas = new ArrayList<>();
        for (DetalleRetiro d : solicitud.getDetalles()) {
            Map<String, Object> item = new HashMap<>();
            item.put("cuenta", d.getCuentaCop().getName());
            item.put("banco", d.getCuentaCop().getBankType().name());
            item.put("tipo", d.getTipoRetiro().name());
            item.put("montoCajero", d.getMontoCajero());
            item.put("montoCorresponsal", d.getMontoCorresponsal());
            cuentas.add(item);
        }

        if (n8nWebhookGeneralUrl == null || n8nWebhookGeneralUrl.isBlank()) {
            log.warn("[Retiro General] n8n webhook-general no configurado — notificación omitida.");
        } else {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("solicitudId", solicitud.getId());
                payload.put("totalMonto", solicitud.getTotalMonto());
                payload.put("pagoRetirador", solicitud.getPagoRetirador());
                payload.put("cuentas", cuentas);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                restTemplate.postForObject(n8nWebhookGeneralUrl,
                        new HttpEntity<>(payload, headers), String.class);
                log.info("[Retiro General #{}] Notificación n8n enviada", solicitud.getId());
            } catch (Exception e) {
                log.error("[Retiro General] Error al notificar n8n: {}", e.getMessage());
            }
        }

        if (telegramGroupChatId == null || telegramGroupChatId.isBlank()) {
            log.warn("[Retiro General] group-chat-id no configurado — notificación por Telegram omitida.");
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("🔔 *Solicitud de Retiro #").append(solicitud.getId()).append("*\n");
            sb.append("💰 *Total:* $").append(String.format("%,.0f", solicitud.getTotalMonto())).append(" COP\n");
            sb.append("🏦 ");
            for (int i = 0; i < solicitud.getDetalles().size(); i++) {
                DetalleRetiro d = solicitud.getDetalles().get(i);
                if (i > 0)
                    sb.append(", ");
                sb.append(d.getCuentaCop().getName())
                        .append(" (").append(d.getCuentaCop().getBankType().name()).append(")");
            }

            // Enviar al grupo con botón inline
            Integer messageId = telegramService.sendMessageWithButton(
                    telegramGroupChatId,
                    sb.toString(),
                    "✅ Aceptar",
                    "accept:" + solicitud.getId());

            // Guardar el message_id para poder editar el mensaje cuando alguien acepte
            if (messageId != null) {
                solicitud.setTelegramMessageId(messageId);
                solicitudRepository.save(solicitud);
            }

            log.info("[Retiro General #{}] Mensaje con botón enviado al grupo (message_id={})",
                    solicitud.getId(), messageId);
        } catch (Exception e) {
            log.error("[Retiro General] Error al notificar por Telegram: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public void reenviarSolicitudGeneral(SolicitudRetiro solicitud) {
        // Borrar el mensaje anterior si existe
        if (solicitud.getTelegramMessageId() != null && telegramGroupChatId != null && !telegramGroupChatId.isBlank()) {
            telegramService.deleteMessage(telegramGroupChatId, solicitud.getTelegramMessageId());
        }

        // Volver a notificar (esto guarda un nuevo telegramMessageId)
        notificarGeneralN8n(solicitud);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Recordatorio de caja: se dispara puntualmente (al confirmar un retiro o
    // al registrar una entrega parcial), NUNCA por un ciclo programado. Así el
    // botón "Entregar efectivo" queda siempre como el ÚLTIMO mensaje del chat,
    // sin duplicarse ni saturar la conversación.
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void enviarRecordatorioCaja(Retirador retirador) {
        if (retirador.getTelegramChatId() == null) {
            return;
        }

        // Siempre limpiamos el recordatorio anterior (si existe), sin importar
        // si ahora hay o no saldo pendiente.
        Integer lastMessageId = lastReminderMessageIds.remove(retirador.getId());
        if (lastMessageId != null) {
            telegramService.deleteMessage(String.valueOf(retirador.getTelegramChatId()), lastMessageId);
        }

        if (retirador.getEfectivo() == null || retirador.getEfectivo().getSaldo() <= 0) {
            // Caja en $0: no hace falta ningún recordatorio nuevo.
            return;
        }

        String msj = String.format("💰 Caja: *$%,.0f*", retirador.getEfectivo().getSaldo());
        java.util.LinkedHashMap<String, String> buttonsData = new java.util.LinkedHashMap<>();
        buttonsData.put("✅ Entregar efectivo", "entregar_start");
        buttonsData.put("🧾 Registrar gasto", "gasto_start");
        buttonsData.put("📊 Movimientos", "movimientos_start");
        Integer newMessageId = telegramService.sendMessageWithButtons(
                String.valueOf(retirador.getTelegramChatId()), msj, buttonsData);

        // Guardamos el ID del nuevo recordatorio
        if (newMessageId != null) {
            lastReminderMessageIds.put(retirador.getId(), newMessageId);
        }

        log.info("[Recordatorio Caja] Mensaje enviado a {} por saldo de {}", retirador.getNombre(),
                retirador.getEfectivo().getSaldo());
    }
}
