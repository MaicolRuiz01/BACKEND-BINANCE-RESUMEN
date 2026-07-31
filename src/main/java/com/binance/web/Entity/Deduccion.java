package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Deducción: venta P2P registrada A MANO.
 *
 * Existe para el caso en que una venta P2P queda en "modo restricción" en Binance: el dinero
 * sí llegó a una cuenta COP, pero la orden nunca se completa ni aparece en el sync automático,
 * así que no se puede registrar como una SaleP2P normal.
 *
 * A propósito NO se guarda en la tabla de ventas P2P: es un registro aparte, para que las
 * ventas P2P sigan siendo el reflejo fiel de lo que devuelve Binance y no se mezclen con
 * ajustes manuales. Sí afecta el saldo: al crearla suma los pesos a la cuenta COP indicada
 * (y le consume cupo del día), igual que haría la asignación de una venta real.
 *
 * El USDT no se toca: el saldo cripto se lee en vivo desde Binance.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "deduccion")
public class Deduccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Cuenta Binance de la que salió el USDT. */
    @ManyToOne
    @JoinColumn(name = "account_binance_id")
    private AccountBinance accountBinance;

    /** Cuenta COP a la que efectivamente cayó el dinero. */
    @ManyToOne
    @JoinColumn(name = "account_cop_id")
    private AccountCop accountCop;

    /** USDT vendidos. */
    private Double dollarsUs;

    /** Tasa aplicada (COP por USDT). */
    private Double tasa;

    /** Pesos que realmente cayeron. Normalmente dollarsUs × tasa, pero se puede corregir
     *  a mano cuando llega una cifra distinta por comisiones o redondeo. */
    private Double pesosCop;

    private LocalDateTime fecha;

    /** Nota opcional del operario (p. ej. el número de orden restringida). */
    @Column(length = 500)
    private String nota;

    /**
     * Clave de idempotencia generada por el frontend en cada modal de "Nueva deducción".
     * Evita que un doble clic cree dos deducciones y sume el saldo dos veces.
     */
    @Column(unique = true)
    private String idempotencyKey;
}
