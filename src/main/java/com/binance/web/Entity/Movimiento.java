package com.binance.web.Entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE) // evita que lo llames por accidente
@Builder(toBuilder = true)
public class Movimiento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String  tipo;
    private LocalDateTime fecha;
    private Double  monto;

    // nuevos campos C2C
    private Double  usdt;
    private Double  tasaOrigen;
    private Double  tasaDestino;
    private Double  pesosOrigen;
    private Double  pesosDestino;

    @ManyToOne private AccountCop cuentaOrigen;
    @ManyToOne private AccountCop cuentaDestino;
    @ManyToOne private Efectivo   caja;          // caja origen (en traspasos entre cajas)
    @ManyToOne private Efectivo   cajaDestino;   // caja destino (traspaso entre cajas)

    private Double  comision;

    @ManyToOne private Cliente    pagoCliente;
    @ManyToOne private Cliente    clienteOrigen;
    @ManyToOne private Supplier   proveedorOrigen;
    @ManyToOne private Supplier   pagoProveedor;
    
    // ====== NUEVO: Auditoría de Ajustes ======
    private String  motivo;            // por qué se ajusta
    private String  actor;             // usuario que lo hizo (username/email)
    private Double  saldoAnterior;
    private Double  saldoNuevo;
    private Double  diferencia;

    // Polimorfismo "suave" del destino del ajuste (uno de estos tres se completa)
    @ManyToOne private Cliente    ajusteCliente;
    @ManyToOne private Supplier   ajusteProveedor;
    @ManyToOne private AccountCop ajusteCuentaCop;

    // Si quieres permitir reversas:
    private Integer reversaDeMovimientoId; // null si no es reversa

    /**
     * Si este movimiento viene de confirmar una SolicitudRetiro (RETIRO CAJERO /
     * RETIRO CORRESPONSAL creados en confirmarInterno), acá queda el id de esa
     * solicitud. Null en cualquier otro tipo de movimiento. Sirve para poder
     * borrar/editar el mensaje de Telegram ("✅ Retiro completado...") cuando
     * alguien borra o edita este movimiento desde la plataforma — sin esto no
     * había forma de saber a qué mensaje de Telegram correspondía cada retiro.
     */
    private Long solicitudRetiroId;

    /**
     * Para retiros: ¿ya se descontó el 4x1000 de la cuenta?
     *  - Nequi/Daviplata: true (se descuenta al instante).
     *  - Bancolombia: false al crearse (se descuenta al día siguiente por el scheduler).
     *  - null en movimientos viejos/otros tipos → se trata como "ya aplicado".
     */
    private Boolean comisionAplicada;

    // ====== NUEVO: Histórico de caja ======
    /**
     * Saldo de la caja (campo "caja", es decir la caja ORIGEN/principal de este
     * movimiento) INMEDIATAMENTE DESPUÉS de aplicar este movimiento. Se guarda al
     * crear el movimiento, y se recalcula (junto con el de todos los movimientos
     * posteriores en el tiempo de esa misma caja) si luego se edita o elimina este
     * movimiento — así siempre queda un histórico confiable de cuánto tenía la
     * caja en cada momento, para resolver discusiones de "se perdió dinero".
     * Null en movimientos que no tocan ninguna caja, o en registros viejos aún no
     * recalculados (ver backfill).
     */
    private Double saldoCajaResultante;

    /**
     * Igual que {@link #saldoCajaResultante} pero para la caja DESTINO
     * (solo aplica a TRANSFERENCIA CAJA, que mueve dos cajas a la vez).
     */
    private Double saldoCajaDestinoResultante;
}
