package com.binance.web.serviceImpl;

import com.binance.web.Entity.*;
import com.binance.web.Repository.*;
import com.binance.web.dto.SolicitudRetiroRequestDto;
import com.binance.web.service.RetiradorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RetiradorServiceImpl implements RetiradorService {

    private static final double PAGO_CAJERO       = 2_000.0;
    private static final double PAGO_CORRESPONSAL  = 3_000.0;
    private static final double PAGO_COMPLETO      = 4_000.0;
    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    private final RetiradorRepository retiradorRepository;
    private final SolicitudRetiroRepository solicitudRepository;
    private final AccountCopRepository accountCopRepository;
    private final EfectivoRepository efectivoRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public RetiradorServiceImpl(RetiradorRepository retiradorRepository,
                                SolicitudRetiroRepository solicitudRepository,
                                AccountCopRepository accountCopRepository,
                                EfectivoRepository efectivoRepository) {
        this.retiradorRepository = retiradorRepository;
        this.solicitudRepository = solicitudRepository;
        this.accountCopRepository = accountCopRepository;
        this.efectivoRepository = efectivoRepository;
    }

    @Value("${app.n8n.webhook-url:}")
    private String n8nWebhookUrl;

    // ── CRUD ──────────────────────────────────────────────────────

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
        // ✅ Validar nombre
        if (retirador.getNombre() == null || retirador.getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del retirador es requerido");
        }
        
        // ✅ Validar que si tiene efectivo_id, el efectivo exista en la BD
        if (retirador.getEfectivo() != null && retirador.getEfectivo().getId() != null) {
            efectivoRepository.findById(retirador.getEfectivo().getId())
                .orElseThrow(() -> new RuntimeException(
                    "Caja no encontrada con ID: " + retirador.getEfectivo().getId()
                ));
        }
        
        if (retirador.getSaldoPendiente() == null) {
            retirador.setSaldoPendiente(0.0);
        }
        
        log.info("Guardando retirador: {} con caja_id: {}", 
            retirador.getNombre(), 
            retirador.getEfectivo() != null ? retirador.getEfectivo().getId() : "null");
        
        return retiradorRepository.save(retirador);
    }

    @Override
    public void delete(Long id) {
        retiradorRepository.deleteById(id);
    }

    // ── Solicitudes ───────────────────────────────────────────────

    @Override
    @Transactional
    public SolicitudRetiro crearSolicitud(SolicitudRetiroRequestDto request) {
        Retirador retirador = findById(request.getRetiradorId());

        SolicitudRetiro solicitud = new SolicitudRetiro();
        solicitud.setRetirador(retirador);
        solicitud.setFechaCreacion(LocalDateTime.now(ZONE_BOGOTA));
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);

        double totalMonto = 0;
        double pagoRetirador = 0;
        List<DetalleRetiro> detalles = new ArrayList<>();

        for (SolicitudRetiroRequestDto.DetalleDto dto : request.getDetalles()) {
            AccountCop cuenta = accountCopRepository.findById(dto.getCuentaCopId())
                    .orElseThrow(() -> new RuntimeException("Cuenta COP no encontrada: " + dto.getCuentaCopId()));

            DetalleRetiro detalle = new DetalleRetiro();
            detalle.setSolicitud(solicitud);
            detalle.setCuentaCop(cuenta);
            detalle.setTipoRetiro(dto.getTipoRetiro());
            detalle.setMontoCajero(dto.getMontoCajero());
            detalle.setMontoCorresponsal(dto.getMontoCorresponsal());

            totalMonto += detalle.totalDetalle();
            pagoRetirador += calcularPago(dto.getTipoRetiro());
            detalles.add(detalle);
        }

        solicitud.setTotalMonto(totalMonto);
        solicitud.setPagoRetirador(pagoRetirador);
        solicitud.setDetalles(detalles);

        SolicitudRetiro saved = solicitudRepository.save(solicitud);

        // Notificación a n8n → Telegram
        notificarN8n(saved, retirador);

        return saved;
    }

    @Override
    @Transactional
    public SolicitudRetiro confirmarSolicitud(Long solicitudId) {
        SolicitudRetiro solicitud = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada: " + solicitudId));

        if (solicitud.getEstado() == EstadoSolicitud.COMPLETADO) {
            throw new IllegalStateException("La solicitud ya fue confirmada.");
        }

        // 1. Descontar de cada cuenta COP
        for (DetalleRetiro detalle : solicitud.getDetalles()) {
            AccountCop cuenta = detalle.getCuentaCop();
            double totalDetalle = detalle.totalDetalle();
            cuenta.setBalance(cuenta.getBalance() - totalDetalle);
            accountCopRepository.save(cuenta);
        }

        // 2. Acreditar total a la caja del retirador
        Retirador retirador = solicitud.getRetirador();
        if (retirador.getEfectivo() != null) {
            Efectivo caja = retirador.getEfectivo();
            caja.setSaldo(caja.getSaldo() + solicitud.getTotalMonto());
            efectivoRepository.save(caja);
        }

        // 3. Sumar pago al saldo pendiente del retirador
        retirador.setSaldoPendiente(retirador.getSaldoPendiente() + solicitud.getPagoRetirador());
        retiradorRepository.save(retirador);

        // 4. Marcar como completado
        solicitud.setEstado(EstadoSolicitud.COMPLETADO);
        return solicitudRepository.save(solicitud);
    }

    @Override
    public List<SolicitudRetiro> historialPorRetirador(Long retiradorId) {
        return solicitudRepository.findByRetiradorIdOrderByFechaCreacionDesc(retiradorId);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private double calcularPago(TipoRetiro tipo) {
        return switch (tipo) {
            case CAJERO       -> PAGO_CAJERO;
            case CORRESPONSAL -> PAGO_CORRESPONSAL;
            case COMPLETO     -> PAGO_COMPLETO;
        };
    }

    private void notificarN8n(SolicitudRetiro solicitud, Retirador retirador) {
        if (n8nWebhookUrl == null || n8nWebhookUrl.isBlank()) {
            log.warn("[Retiro] n8n webhook no configurado — notificación omitida.");
            return;
        }
        try {
            List<Map<String, Object>> cuentas = new ArrayList<>();
            for (DetalleRetiro d : solicitud.getDetalles()) {
                Map<String, Object> item = new HashMap<>();
                item.put("cuenta",       d.getCuentaCop().getName());
                item.put("banco",        d.getCuentaCop().getBankType().name());
                item.put("tipo",         d.getTipoRetiro().name());
                item.put("montoCajero",  d.getMontoCajero());
                item.put("montoCorresponsal", d.getMontoCorresponsal());
                cuentas.add(item);
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("solicitudId",    solicitud.getId());
            payload.put("retirador",      retirador.getNombre());
            payload.put("totalMonto",     solicitud.getTotalMonto());
            payload.put("pagoRetirador",  solicitud.getPagoRetirador());
            payload.put("cuentas",        cuentas);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForObject(n8nWebhookUrl, new HttpEntity<>(payload, headers), String.class);
            log.info("[Retiro] Notificación enviada a n8n — solicitud #{}", solicitud.getId());
        } catch (Exception e) {
            log.error("[Retiro] Error al notificar n8n: {}", e.getMessage());
        }
    }
}
