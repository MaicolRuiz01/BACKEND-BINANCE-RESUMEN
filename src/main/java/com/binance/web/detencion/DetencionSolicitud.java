package com.binance.web.detencion;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Cola de "solicitudes de detención de monitoreo" pendientes: se crea una
 * fila cada vez que una cuenta deja de estar seleccionada en P2P
 * (activaParaP2P pasa a false) y hay que avisarle al bot de Movimientos
 * (Automatizacion Bancolombia / iniciar.py) que pare esa sesión puntual —
 * ver CuentaP2PSyncServiceImpl, que es el único lugar que encola esto.
 *
 * Mismo patrón exacto que ActivacionSolicitud (ver ese archivo para el
 * porqué es polling y no un mensaje de Telegram), pero en la dirección
 * contraria: esto no pide arrancar el MonitorThread de una cuenta, pide
 * cerrarlo — el bot ya tiene la función correcta para esto (MonitorThread
 * .stop(), llamado en hilo aparte, ver el botón "Detener todo" en
 * iniciar.py) y sólo hace falta que el backend le avise CUÁL cuenta parar
 * y CUÁNDO.
 */
@Entity
@Table(name = "detencion_solicitud")
public class DetencionSolicitud {

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
