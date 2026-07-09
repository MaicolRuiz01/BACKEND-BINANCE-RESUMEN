package com.binance.web.serviceImpl;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.Efectivo;
import com.binance.web.Entity.Gasto;
import com.binance.web.Entity.Movimiento;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.EfectivoRepository;
import com.binance.web.Repository.GastoRepository;
import com.binance.web.Repository.MovimientoRepository;
import com.binance.web.service.GastoService;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Service
public class GastoServiceImplement implements GastoService{

    @Autowired
    private GastoRepository gastoRepository;
    @Autowired
    private AccountCopRepository accountCopRepository;
    @Autowired
    private EfectivoRepository efectivoRepository;
    @Autowired
    private MovimientoRepository movimientoRepository;

    public List<Gasto> findAll() {
        return gastoRepository.findAll();
    }

    public Optional<Gasto> findById(Integer id) {
        return gastoRepository.findById(id);
    }

    public Gasto save(Gasto gasto) {
        return gastoRepository.save(gasto);
    }

    public void deleteById(Integer id) {
        gastoRepository.deleteById(id);
    }

    @Override
    @Transactional
    public Gasto saveGasto(Gasto nuevoGasto) {
        // === Candado anti-duplicado ===
        // Si el gasto trae una clave de idempotencia y ya existe uno con esa clave,
        // NO se crea otro ni se vuelve a restar el saldo: se devuelve el ya guardado.
        // Esto neutraliza los clics repetidos cuando la app parece no responder.
        String key = nuevoGasto.getIdempotencyKey();
        if (key != null && !key.isBlank()) {
            Optional<Gasto> existente = gastoRepository.findByIdempotencyKey(key);
            if (existente.isPresent()) {
                return existente.get();
            }
        }

        Double monto = nuevoGasto.getMonto();
        double comision = monto * 0.004;

        // Siempre asigna la fecha del sistema
        nuevoGasto.setFecha(LocalDateTime.now());

        if (nuevoGasto.getCuentaPago() != null) {
            AccountCop cuenta = accountCopRepository.findById(nuevoGasto.getCuentaPago().getId())
                .orElseThrow(() -> new RuntimeException("Cuenta COP no encontrada"));
            // 4x1000: diferido en BANCOLOMBIA (lo cobra el scheduler al día siguiente),
            // inmediato en los demás bancos.
            boolean esBanco = "BANCOLOMBIA".equalsIgnoreCase(String.valueOf(cuenta.getBankType()));
            double deduccionHoy = esBanco ? monto : (monto + comision);
            // Resta ATÓMICA en la BD: no se pierde aunque el sync P2P actualice el saldo a la vez.
            accountCopRepository.restarSaldo(cuenta.getId(), deduccionHoy);

            // Movimiento GASTO: lo hace visible en el historial y deja el 4x1000 pendiente
            // (comisionAplicada=false) para que el scheduler lo difiera en Bancolombia.
            Movimiento mov = movimientoRepository.save(Movimiento.builder()
                    .tipo("GASTO")
                    .fecha(nuevoGasto.getFecha())
                    .monto(monto)
                    .cuentaOrigen(cuenta)
                    .comision(comision)
                    .comisionAplicada(!esBanco)
                    .build());
            nuevoGasto.setMovimientoId(mov.getId());
        }

        if (nuevoGasto.getPagoEfectivo() != null) {
            Efectivo caja = efectivoRepository.findById(nuevoGasto.getPagoEfectivo().getId())
                .orElseThrow(() -> new RuntimeException("Caja no encontrada"));

            caja.setSaldo(caja.getSaldo() - monto);
            efectivoRepository.save(caja);

            // Gasto en efectivo: sin 4x1000, pero se registra el movimiento para el historial de la caja.
            Movimiento mov = movimientoRepository.save(Movimiento.builder()
                    .tipo("GASTO")
                    .fecha(nuevoGasto.getFecha())
                    .monto(monto)
                    .caja(caja)
                    .comision(0.0)
                    .comisionAplicada(true)
                    .build());
            nuevoGasto.setMovimientoId(mov.getId());
        }

        return gastoRepository.save(nuevoGasto);
    }

    @Override
    @Transactional
    public void eliminarGasto(Integer id) {
        Gasto gasto = gastoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Gasto no encontrado"));

        Double monto = gasto.getMonto() != null ? gasto.getMonto() : 0.0;
        double comision = monto * 0.004;

        // Movimiento asociado (si el gasto es de antes de este cambio, movimientoId es null).
        Movimiento mov = gasto.getMovimientoId() != null
                ? movimientoRepository.findById(gasto.getMovimientoId()).orElse(null)
                : null;

        // Devuelve EXACTAMENTE lo que se había restado:
        //  - Cuenta COP: monto + 4x1000 SOLO si el 4x1000 ya se había aplicado
        //    (en Bancolombia diferido, si el scheduler aún no corrió, solo se devuelve el monto).
        //    Gastos viejos (sin movimiento) devuelven monto*1.004, como antes.
        if (gasto.getCuentaPago() != null) {
            boolean comisionYaAplicada = (mov == null) || !Boolean.FALSE.equals(mov.getComisionAplicada());
            double aReversar = monto + (comisionYaAplicada ? comision : 0.0);
            accountCopRepository.sumarSaldo(gasto.getCuentaPago().getId(), aReversar);
        }

        if (gasto.getPagoEfectivo() != null) {
            Efectivo caja = efectivoRepository.findById(gasto.getPagoEfectivo().getId())
                .orElseThrow(() -> new RuntimeException("Caja no encontrada"));

            caja.setSaldo(caja.getSaldo() + monto);
            efectivoRepository.save(caja);
        }

        // Borrar el movimiento asociado para que no quede colgado ni el scheduler lo procese.
        if (mov != null) {
            movimientoRepository.delete(mov);
        }

        gastoRepository.delete(gasto);
    }
    
    @Override
    public Double totalGastosHoyCuentaCop(Integer cuentaId) {
        LocalDate hoy = LocalDate.now();
        LocalDateTime inicio = hoy.atStartOfDay();
        LocalDateTime fin = hoy.plusDays(1).atStartOfDay();

        List<Gasto> gastos = gastoRepository
                .findByCuentaPago_IdAndFechaBetween(cuentaId, inicio, fin);

        return gastos.stream()
                .mapToDouble(g -> g.getMonto() != null ? g.getMonto() : 0.0)
                .sum();
    }
    
    @Override
    public Double totalGastosHoyCaja(Integer cajaId) {
        LocalDate hoy = LocalDate.now();
        LocalDateTime inicio = hoy.atStartOfDay();
        LocalDateTime fin = hoy.plusDays(1).atStartOfDay();

        List<Gasto> gastos = gastoRepository
                .findByPagoEfectivo_IdAndFechaBetween(cajaId, inicio, fin);

        return gastos.stream()
                .mapToDouble(g -> g.getMonto() != null ? g.getMonto() : 0.0)
                .sum();
    }



}
