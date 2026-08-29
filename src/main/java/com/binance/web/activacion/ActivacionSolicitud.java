package com.binance.web.activacion;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Cola de "solicitudes de activación de monitoreo" pendientes: se crea una
 * fila cada vez que se pide que el bot de Movimientos (Automatizacion
 * Bancolombia / iniciar.py) empiece a vigilar una cuenta en vivo — ver
 * AccountCopController.solicitarActivacionManual / ActivacionServiceImpl.
 *
 * Mismo patrón que ConciliacionSolicitud (ver ese archivo para la
 * explicación completa de por qué es polling y no un mensaje de Telegram),
 * pero para una acción distinta: esto no pide un chequeo puntual de una
 * sesión de Chrome aparte, pide arrancar el MONITOREO CONTINUO de la cuenta
 * (MonitorThread, compartido con el menú de Telegram — ver
 * Movimientos/Pochonance/pochonance_activador.py). El propio MonitorThread,
 * al conectar por primera vez, ya reporta si la cuenta sirve o no (eventos
 * "conexion_exitosa" / "error_login" en /movimientos/evento) — por eso no
 * hace falta una cola de conciliación aparte para cuentas que se activan así.
 */
@Entity
@Table(name = "activacion_solicitud")
public class ActivacionSolicitud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String cuenta;

    private LocalDateTime creadaEn;

    private boolean consumida;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCuenta() { return cuenta; }
    public void setCuenta(String cuenta) { this.cuenta = cuenta; }
    public LocalDateTime getCreadaEn() { return creadaEn; }
    public void setCreadaEn(LocalDateTime creadaEn) { this.creadaEn = creadaEn; }
    public boolean isConsumida() { return consumida; }
    public void setConsumida(boolean consumida) { this.consumida = consumida; }
}
