package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DIAGNÓSTICO de la tasa promedio — tabla de solo lectura para revisar qué está pasando.
 *
 * No participa en ningún cálculo ni en ninguna regla de negocio: es un registro de lo que
 * ocurrió en cada recálculo. Existe porque la tasa promedio vive en una fila de AverageRate
 * que se SOBRESCRIBE en cada compra, así que no queda rastro de cómo evolucionó ni con qué
 * números se hizo cada paso, y no había forma de auditar por qué el número sale distinto a
 * lo esperado.
 *
 * Se escribe una fila por evento y NUNCA se modifica. Si escribir el diagnóstico falla, la
 * asignación de la compra sigue su curso: esto no puede bloquear una operación de dinero.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tasa_promedio_diagnostico")
public class TasaPromedioDiagnostico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Momento exacto del recálculo. */
    private LocalDateTime fecha;

    /**
     * Qué provocó el cálculo:
     *  APERTURA_SESION → no había sesión abierta y esta compra abrió una nueva (usa saldo externo)
     *  LICUA_SESION    → ya había sesión abierta y la compra se licuó contra la misma base
     */
    @Column(length = 32)
    private String evento;

    // ── La compra que lo causó ────────────────────────────────────

    @Column(name = "buy_dollars_id")
    private Integer buyDollarsId;

    @Column(name = "compra_usdt")
    private Double compraUsdt;

    @Column(name = "compra_tasa")
    private Double compraTasa;

    /** compraUsdt × compraTasa. Se guarda calculado para no tener que rehacerlo al revisar. */
    @Column(name = "compra_pesos")
    private Double compraPesos;

    // ── Los dos datos CLAVE para el diagnóstico ───────────────────

    /**
     * Número crudo que devolvió getTotalExternalBalance(): el valor de TODO el portafolio en
     * dólares, incluyendo monedas distintas de USDT.
     * Es el principal sospechoso: si entre dos compras este valor se mueve sin que se haya
     * comprado ni vendido USDT, queda demostrado que la base del cálculo no es el inventario.
     */
    @Column(name = "saldo_externo_leido")
    private Double saldoExternoLeido;

    /** USDT de otras compras aún sin asignar, que se descuentan para no contarlas dos veces. */
    @Column(name = "otros_pendientes_usdt")
    private Double otrosPendientesUsdt;

    // ── Base del cálculo ──────────────────────────────────────────

    /** Inventario que se tomó como base (tras descontar la compra y los pendientes). */
    @Column(name = "saldo_base_usdt")
    private Double saldoBaseUsdt;

    /** Tasa a la que se valoró ese inventario base. */
    @Column(name = "tasa_base")
    private Double tasaBase;

    /** saldoBaseUsdt × tasaBase. */
    @Column(name = "pesos_base")
    private Double pesosBase;

    /** true si la base quedó recortada a cero (el saldo era menor que la compra).
     *  Cuando esto pasa, el promedio se vuelve simplemente la tasa de la compra. */
    @Column(name = "base_recortada_a_cero")
    private Boolean baseRecortadaACero;

    // ── Acumulados de la sesión (ya incluyendo esta compra) ────────

    @Column(name = "usdt_acum_sesion")
    private Double usdtAcumSesion;

    @Column(name = "pesos_acum_sesion")
    private Double pesosAcumSesion;

    // ── Resultado ─────────────────────────────────────────────────

    @Column(name = "tasa_anterior")
    private Double tasaAnterior;

    @Column(name = "tasa_resultante")
    private Double tasaResultante;

    /** Denominador de la división final. */
    @Column(name = "total_usdt")
    private Double totalUsdt;

    /** Numerador de la división final. */
    @Column(name = "total_pesos")
    private Double totalPesos;

    // ── Referencias ───────────────────────────────────────────────

    /** Fila de AverageRate (sesión) sobre la que se escribió el resultado. */
    @Column(name = "average_rate_id")
    private Integer averageRateId;

    /** Si la sesión quedó abierta tras este evento. */
    @Column(name = "sesion_abierta")
    private Boolean sesionAbierta;

    /** Día contable al que se asoció la sesión. */
    @Column(name = "inicio_dia")
    private LocalDateTime inicioDia;
}
