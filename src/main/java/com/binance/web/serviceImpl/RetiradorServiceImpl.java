package com.binance.web.serviceImpl;

import com.binance.web.Entity.*;
import com.binance.web.Repository.*;
import com.binance.web.dto.*;
import com.binance.web.service.RetiradorService;
import com.binance.web.service.TelegramService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RetiradorServiceImpl implements RetiradorService {

    private static final double PAGO_CAJERO       = 2_000.0;
    private static final double PAGO_CORRESPONSAL  = 3_000.0;
    private static final double PAGO_COMPLETO      = 4_000.0;
    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    private final RetiradorRepository      retiradorRepository;
    private final SolicitudRetiroRepository solicitudRepository;
    private final AccountCopRepository     accountCopRepository;
    private final EfectivoRepository       efectivoRepository;
    private final TelegramService          telegramService;

    public RetiradorServiceImpl(RetiradorRepository retiradorRepository,
                                SolicitudRetiroRepository solicitudRepository,
                                AccountCopRepository accountCopRepository,
                                EfectivoRepository efectivoRepository,
                                TelegramService telegramService) {
        this.retiradorRepository  = retiradorRepository;
        this.solicitudRepository  = solicitudRepository;
        this.accountCopRepository = accountCopRepository;
        this.efectivoRepository   = efectivoRepository;
        this.telegramService      = telegramService;
    }

    @Value("${app.telegram.chat-id:}")
    private String telegramChatId;

    @Value("${app.telegram.group-chat-id:}")
    private String telegramGroupChatId;

    // ═══════════════════════════════════════════════════════════════
    // CRUD retiradores
    // ═══════════════════════════════════════════════════════════════

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
        retiradorRepository.deleteById(id);
    }

    // ═══════════════════════════════════════════════════════════════
    // Solicitud con retirador pre-asignado
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public SolicitudRetiro crearSolicitud(SolicitudRetiroRequestDto request) {
        Retirador retirador = findById(request.getRetiradorId());

        SolicitudRetiro solicitud = new SolicitudRetiro();
        solicitud.setRetirador(retirador);
        solicitud.setFechaCreacion(LocalDateTime.now(ZONE_BOGOTA));
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);

        List<DetalleRetiro> detalles = buildDetalles(request.getDetalles(), solicitud);
        double totalMonto    = detalles.stream().mapToDouble(DetalleRetiro::totalDetalle).sum();
        double pagoRetirador = detalles.stream().mapToDouble(d -> calcularPago(d.getTipoRetiro())).sum();

        solicitud.setTotalMonto(totalMonto);
        solicitud.setPagoRetirador(pagoRetirador);
        solicitud.setDetalles(detalles);

        SolicitudRetiro saved = solicitudRepository.save(solicitud);
        notificarTelegram(saved, retirador);
        return saved;
    }

    @Override
    @Transactional
    public SolicitudRetiro confirmarSolicitud(Long solicitudId) {
        SolicitudRetiro solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + solicitudId));

        if (solicitud.getEstado() == EstadoSolicitud.COMPLETADO)
            throw new IllegalStateException("La solicitud ya fue confirmada.");
        if (solicitud.getRetirador() == null)
            throw new IllegalStateException("La solicitud aún no tiene un retirador asignado.");

        for (DetalleRetiro detalle : solicitud.getDetalles()) {
            AccountCop cuenta = detalle.getCuentaCop();
            cuenta.setBalance(cuenta.getBalance() - detalle.totalDetalle());
            accountCopRepository.save(cuenta);
        }

        Retirador retirador = solicitud.getRetirador();
        if (retirador.getEfectivo() != null) {
            Efectivo caja = retirador.getEfectivo();
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

        double totalMonto    = detalles.stream().mapToDouble(DetalleRetiro::totalDetalle).sum();
        double pagoRetirador = detalles.stream().mapToDouble(d -> calcularPago(d.getTipoRetiro())).sum();

        solicitud.setTotalMonto(totalMonto);
        solicitud.setPagoRetirador(pagoRetirador);
        solicitud.setDetalles(detalles);

        SolicitudRetiro saved = solicitudRepository.save(solicitud);

        // Notificar al grupo de Telegram directamente
        notificarGeneralTelegram(saved);
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
        // Notificar al retirador individualmente
        notificarTelegram(saved, retirador);
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
            if (cuenta.getBalance() < dto.getMonto())
                throw new IllegalStateException("Saldo insuficiente en la cuenta COP");
            cuenta.setBalance(cuenta.getBalance() - dto.getMonto());
            accountCopRepository.save(cuenta);

        } else if ("CAJA".equalsIgnoreCase(dto.getFuente())) {
            Efectivo caja = efectivoRepository.findById(dto.getCajaId())
                    .orElseThrow(() -> new RuntimeException("Caja no encontrada: " + dto.getCajaId()));
            if (caja.getSaldo() < dto.getMonto())
                throw new IllegalStateException("Saldo insuficiente en la caja");
            caja.setSaldo(caja.getSaldo() - dto.getMonto());
            efectivoRepository.save(caja);

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
            Double total     = (Double) rows.get(i)[1];
            Retirador r      = retiradorMap.get(retiradorId);
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
            case CAJERO       -> PAGO_CAJERO;
            case CORRESPONSAL -> PAGO_CORRESPONSAL;
            case COMPLETO     -> PAGO_COMPLETO;
        };
    }

    private LocalDateTime[] rangoDeSemana() {
        LocalDate hoy   = LocalDate.now(ZONE_BOGOTA);
        LocalDate lunes = hoy.with(java.time.DayOfWeek.MONDAY);
        LocalDate domingo = lunes.plusDays(6);
        return new LocalDateTime[]{
            lunes.atStartOfDay(),
            domingo.atTime(23, 59, 59)
        };
    }

    private void notificarTelegram(SolicitudRetiro solicitud, Retirador retirador) {
        if (telegramChatId == null || telegramChatId.isBlank()) {
            log.warn("[Telegram] Chat ID no configurado — notificación omitida.");
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("🔔 *Nueva Solicitud de Retiro #").append(solicitud.getId()).append("*\n");
            sb.append("👤 *Retirador:* ").append(retirador.getNombre());
            if (retirador.getTelegramUsername() != null && !retirador.getTelegramUsername().isBlank()) {
                sb.append(" (@").append(retirador.getTelegramUsername()).append(")");
            }
            sb.append("\n");
            sb.append("💰 *Monto Total:* $").append(String.format("%,.0f", solicitud.getTotalMonto())).append(" COP\n");
            sb.append("💵 *Pago Retirador:* $").append(String.format("%,.0f", solicitud.getPagoRetirador())).append(" COP\n");
            sb.append("🏦 *Detalles:*");
            for (DetalleRetiro d : solicitud.getDetalles()) {
                sb.append("\n  - ").append(d.getCuentaCop().getName())
                  .append(" (").append(d.getCuentaCop().getBankType().name()).append(") | ")
                  .append(d.getTipoRetiro().name())
                  .append(" | Cajero: $").append(String.format("%,.0f", d.getMontoCajero()))
                  .append(" | Corresponsal: $").append(String.format("%,.0f", d.getMontoCorresponsal()));
            }

            telegramService.sendMessage(telegramChatId, sb.toString());
            log.info("[Retiro] Notificación directa enviada a Telegram — solicitud #{}", solicitud.getId());
        } catch (Exception e) {
            log.error("[Retiro] Error al notificar Telegram: {}", e.getMessage());
        }
    }

    /**
     * Envía al GRUPO de retiradores un mensaje con botón inline "✅ Aceptar".
     * El primer retirador que pulse el botón quedará asignado (lógica en TelegramWebhookService).
     * Guarda el message_id devuelto para poder editar el mensaje después.
     */
    private void notificarGeneralTelegram(SolicitudRetiro solicitud) {
        if (telegramGroupChatId == null || telegramGroupChatId.isBlank()) {
            log.warn("[Retiro General] group-chat-id no configurado — notificación omitida.");
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("🔔 *Nueva Solicitud de Retiro #").append(solicitud.getId()).append("*\n");
            sb.append("💰 *Monto Total:* $").append(String.format("%,.0f", solicitud.getTotalMonto())).append(" COP\n");
            sb.append("💵 *Pago Retirador:* $").append(String.format("%,.0f", solicitud.getPagoRetirador())).append(" COP\n");
            sb.append("🏦 *Detalles:*");
            for (DetalleRetiro d : solicitud.getDetalles()) {
                sb.append("\n  • ").append(d.getCuentaCop().getName())
                  .append(" (").append(d.getCuentaCop().getBankType().name()).append(") | ")
                  .append(d.getTipoRetiro().name())
                  .append(" | Cajero: $").append(String.format("%,.0f", d.getMontoCajero()))
                  .append(" | Corresponsal: $").append(String.format("%,.0f", d.getMontoCorresponsal()));
            }
            sb.append("\n\n_Presiona el botón para tomar este retiro._");

            // Enviar al grupo con botón inline
            Integer messageId = telegramService.sendMessageWithButton(
                telegramGroupChatId,
                sb.toString(),
                "✅ Aceptar",
                "accept:" + solicitud.getId()
            );

            // Guardar el message_id para poder editar el mensaje cuando alguien acepte
            if (messageId != null) {
                solicitud.setTelegramMessageId(messageId);
                solicitudRepository.save(solicitud);
            }

            log.info("[Retiro General #{}] Mensaje con botón enviado al grupo (message_id={})",
                solicitud.getId(), messageId);
        } catch (Exception e) {
            log.error("[Retiro General] Error al notificar Telegram: {}", e.getMessage());
        }
    }
}
