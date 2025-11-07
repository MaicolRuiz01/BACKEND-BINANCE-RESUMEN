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
    @ManyToOne private Efectivo   caja;

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

    // Polimorfismo “suave” del destino del ajuste (uno de estos tres se completa)
    @ManyToOne private Cliente    ajusteCliente;
    @ManyToOne private Supplier   ajusteProveedor;
    @ManyToOne private AccountCop ajusteCuentaCop;

    // Si quieres permitir reversas:
    private Integer reversaDeMovimientoId; // null si no es reversa
}
