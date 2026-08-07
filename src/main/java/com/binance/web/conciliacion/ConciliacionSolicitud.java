package com.binance.web.conciliacion;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Cola de "solicitudes de conciliación" pendientes: se crea una fila cada vez
 * que se activa una cuenta Bancolombia en "Cuentas P2P" (ver
 * AccountCopController.toggleActivaParaP2P / ConciliacionBancariaServiceImpl
 * .solicitarConciliacion).
 *
 * El bot de conciliación (conciliacion_bancaria.py) NO puede enterarse de
 * esto por Telegram: un bot nunca recibe, vía getUpdates, los mensajes que él
 * mismo mandó con sendMessage, así que "escuchar" un aviso propio por
 * Telegram nunca iba a funcionar. En su lugar, el bot hace polling contra
 * GET /conciliacion/pendiente, que entrega (y marca "consumida") la solicitud
 * más antigua sin atender.
 */
@Entity
@Table(name = "conciliacion_solicitud")
public class ConciliacionSolicitud {

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
