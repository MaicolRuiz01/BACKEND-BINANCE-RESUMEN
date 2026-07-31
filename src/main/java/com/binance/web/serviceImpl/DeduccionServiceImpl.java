package com.binance.web.serviceImpl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.Deduccion;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.DeduccionRepository;
import com.binance.web.model.DeduccionDto;
import com.binance.web.service.AccountCopService;
import com.binance.web.service.DeduccionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Deducciones: ventas P2P registradas a mano (órdenes que quedaron en "modo restricción").
 *
 * Efecto sobre el saldo — el mismo que hace la asignación de una venta P2P real:
 *   · suma los pesos al saldo de la cuenta COP,
 *   · le consume cupo del día a esa cuenta.
 * El USDT NO se toca: el saldo cripto se lee en vivo desde Binance, así que restarlo acá
 * lo descuadraría dos veces.
 *
 * Editar y eliminar SIEMPRE revierten primero el efecto anterior y luego aplican el nuevo,
 * para que no queden saldos arrastrados de un valor viejo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeduccionServiceImpl implements DeduccionService {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private final DeduccionRepository deduccionRepository;
    private final AccountBinanceRepository accountBinanceRepository;
    private final AccountCopService accountCopService;

    @Override
    public List<DeduccionDto> listar() {
        return deduccionRepository.findAllByOrderByFechaDesc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DeduccionDto crear(DeduccionDto dto) {
        // Idempotencia: si el operario hizo doble clic, devolvemos la que ya se creó
        // en vez de sumar el saldo por segunda vez.
        if (dto.getIdempotencyKey() != null && !dto.getIdempotencyKey().isBlank()) {
            var existente = deduccionRepository.findByIdempotencyKey(dto.getIdempotencyKey());
            if (existente.isPresent()) return toDto(existente.get());
        }

        validar(dto);

        Deduccion d = new Deduccion();
        d.setAccountBinance(buscarBinance(dto.getAccountBinanceId()));
        d.setAccountCop(buscarCop(dto.getAccountCopId()));
        d.setDollarsUs(dto.getDollarsUs());
        d.setTasa(dto.getTasa());
        d.setPesosCop(dto.getPesosCop());
        d.setNota(dto.getNota());
        d.setFecha(dto.getFecha() != null ? dto.getFecha() : LocalDateTime.now(ZONE));
        d.setIdempotencyKey(dto.getIdempotencyKey());

        aplicarSaldo(d.getAccountCop(), monto(d.getPesosCop()));

        Deduccion guardada = deduccionRepository.save(d);
        log.info("[Deduccion] Creada #{}: {} COP → cuenta {}",
                guardada.getId(), monto(guardada.getPesosCop()), guardada.getAccountCop().getName());
        return toDto(guardada);
    }

    @Override
    @Transactional
    public DeduccionDto actualizar(Integer id, DeduccionDto dto) {
        Deduccion d = deduccionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Deducción no encontrada: " + id));

        validar(dto);

        // 1) Revertir el efecto de los valores ANTERIORES (cuenta y monto viejos).
        revertirSaldo(d.getAccountCop(), monto(d.getPesosCop()));

        // 2) Aplicar los valores nuevos.
        d.setAccountBinance(buscarBinance(dto.getAccountBinanceId()));
        d.setAccountCop(buscarCop(dto.getAccountCopId()));
        d.setDollarsUs(dto.getDollarsUs());
        d.setTasa(dto.getTasa());
        d.setPesosCop(dto.getPesosCop());
        d.setNota(dto.getNota());
        if (dto.getFecha() != null) d.setFecha(dto.getFecha());

        aplicarSaldo(d.getAccountCop(), monto(d.getPesosCop()));

        Deduccion guardada = deduccionRepository.save(d);
        log.info("[Deduccion] Actualizada #{}", guardada.getId());
        return toDto(guardada);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        Deduccion d = deduccionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Deducción no encontrada: " + id));

        revertirSaldo(d.getAccountCop(), monto(d.getPesosCop()));
        deduccionRepository.delete(d);
        log.info("[Deduccion] Eliminada #{} (saldo revertido)", id);
    }

    // ── Saldos ────────────────────────────────────────────────────

    /** Suma los pesos a la cuenta COP y le consume cupo del día (igual que una venta P2P). */
    private void aplicarSaldo(AccountCop acc, double pesos) {
        if (acc == null || pesos == 0) return;
        acc.setBalance((acc.getBalance() != null ? acc.getBalance() : 0.0) + pesos);
        acc.setCupoDisponibleHoy((acc.getCupoDisponibleHoy() != null ? acc.getCupoDisponibleHoy() : 0.0) - pesos);
        accountCopService.saveAccountCopSafe(acc);
    }

    /** Deshace exactamente lo que hizo aplicarSaldo. */
    private void revertirSaldo(AccountCop acc, double pesos) {
        if (acc == null || pesos == 0) return;
        acc.setBalance((acc.getBalance() != null ? acc.getBalance() : 0.0) - pesos);
        acc.setCupoDisponibleHoy((acc.getCupoDisponibleHoy() != null ? acc.getCupoDisponibleHoy() : 0.0) + pesos);
        accountCopService.saveAccountCopSafe(acc);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private double monto(Double v) {
        return v != null ? v : 0.0;
    }

    private void validar(DeduccionDto dto) {
        if (dto.getAccountCopId() == null) {
            throw new IllegalArgumentException("Debe indicar la cuenta COP a la que cayó el dinero");
        }
        if (dto.getPesosCop() == null || dto.getPesosCop() <= 0) {
            throw new IllegalArgumentException("Los pesos COP deben ser mayores que 0");
        }
        if (dto.getDollarsUs() != null && dto.getDollarsUs() < 0) {
            throw new IllegalArgumentException("El monto en USDT no puede ser negativo");
        }
        if (dto.getTasa() != null && dto.getTasa() < 0) {
            throw new IllegalArgumentException("La tasa no puede ser negativa");
        }
    }

    private AccountBinance buscarBinance(Integer id) {
        if (id == null) return null;
        return accountBinanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta Binance no encontrada: " + id));
    }

    private AccountCop buscarCop(Integer id) {
        AccountCop acc = accountCopService.findByIdAccountCop(id);
        if (acc == null) throw new IllegalArgumentException("Cuenta COP no encontrada: " + id);
        return acc;
    }

    private DeduccionDto toDto(Deduccion d) {
        DeduccionDto dto = new DeduccionDto();
        dto.setId(d.getId());
        if (d.getAccountBinance() != null) {
            dto.setAccountBinanceId(d.getAccountBinance().getId());
            dto.setAccountBinanceNombre(d.getAccountBinance().getName());
        }
        if (d.getAccountCop() != null) {
            dto.setAccountCopId(d.getAccountCop().getId());
            dto.setAccountCopNombre(d.getAccountCop().getName());
        }
        dto.setDollarsUs(d.getDollarsUs());
        dto.setTasa(d.getTasa());
        dto.setPesosCop(d.getPesosCop());
        dto.setFecha(d.getFecha());
        dto.setNota(d.getNota());
        return dto;
    }
}
