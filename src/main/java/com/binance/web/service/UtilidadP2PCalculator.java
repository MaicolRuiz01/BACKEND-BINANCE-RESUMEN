package com.binance.web.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.binance.web.Entity.AverageRate;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Entity.SaleP2pAccountCop;
import com.binance.web.Repository.AverageRateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cálculo de la utilidad de una venta P2P, en UN SOLO lugar.
 *
 * Utilidad = pesos recibidos − costo del USDT vendido − impuesto
 *   · costo    = (dólares + comisión) × tasa promedio de compra vigente
 *   · impuesto = 4x1000 sobre lo que se asignó a efectivo (detalle sin cuenta COP)
 *
 * Antes esta fórmula vivía solo dentro de saveUtilitydefinitive(), un método que NADIE llamaba:
 * las ventas quedaban guardadas con utilidad = 0 sin importar por dónde se hubieran asignado.
 * Centralizarlo evita que vuelva a pasar y que las dos rutas de asignación (pre-asignación
 * automática y asignación manual) calculen cosas distintas.
 *
 * Defensivo: si todavía no hay una tasa promedio con la cual comparar, devuelve 0 en vez de
 * inventar un número. Es preferible una utilidad en cero (visiblemente pendiente) que una cifra
 * incorrecta que se tome por buena.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UtilidadP2PCalculator {

    private final AverageRateRepository averageRateRepository;

    /**
     * Calcula y asigna la utilidad de la venta. Debe llamarse DESPUÉS de haber cargado los
     * detalles de cuentas COP, porque el impuesto depende de ellos.
     */
    public void calcularYAsignar(SaleP2P sale) {
        if (sale == null) return;
        try {
            sale.setUtilidad(calcular(sale));
        } catch (Exception e) {
            log.warn("[UtilidadP2P] No se pudo calcular la utilidad de la venta {}: {}",
                    sale.getNumberOrder(), e.getMessage());
            if (sale.getUtilidad() == null) sale.setUtilidad(0.0);
        }
    }

    /** Igual que el anterior pero con una tasa promedio ya conocida (para recálculos en bloque). */
    public double calcularCon(SaleP2P sale, Double averageRate) {
        if (sale == null || averageRate == null || averageRate <= 0) return 0.0;
        double pesos     = valor(sale.getPesosCop());
        double dolares   = valor(sale.getDollarsUs());
        double comision  = valor(sale.getCommission());
        return pesos - ((dolares + comision) * averageRate) - impuesto(sale);
    }

    public double calcular(SaleP2P sale) {
        Double tasaPromedio = tasaPromedioVigente(sale);
        if (tasaPromedio == null || tasaPromedio <= 0) {
            log.debug("[UtilidadP2P] Venta {} sin tasa promedio de referencia → utilidad 0",
                    sale.getNumberOrder());
            return 0.0;
        }
        return calcularCon(sale, tasaPromedio);
    }

    /**
     * Tasa promedio con la que se compró el USDT que se está vendiendo. Se busca la última
     * anterior a la fecha de la venta; si no hay (por ejemplo la primera venta del sistema),
     * se cae a la más reciente que exista.
     */
    private Double tasaPromedioVigente(SaleP2P sale) {
        AverageRate ar = null;
        if (sale.getDate() != null) {
            ar = averageRateRepository.findTopByFechaBeforeOrderByFechaDesc(sale.getDate()).orElse(null);
        }
        if (ar == null) {
            ar = averageRateRepository.findTopByOrderByFechaDesc().orElse(null);
        }
        return ar != null ? ar.getAverageRate() : null;
    }

    /** 4x1000 sobre los montos que NO fueron a una cuenta COP (es decir, salieron en efectivo). */
    private double impuesto(SaleP2P sale) {
        List<SaleP2pAccountCop> detalles = sale.getAccountCopsDetails();
        if (detalles == null || detalles.isEmpty()) return 0.0;

        double total = 0.0;
        for (SaleP2pAccountCop d : detalles) {
            if (d.getAccountCop() == null) total += valor(d.getAmount()) * 0.004;
        }
        return total;
    }

    private double valor(Double v) {
        return v != null ? v : 0.0;
    }
}
