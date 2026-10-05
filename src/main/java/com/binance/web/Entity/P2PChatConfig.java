package com.binance.web.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Interruptor global del envío automático por chat de la cuenta COP asignada a cada venta
 * en curso (ver P2PChatService). Fila única (id = 1). Por defecto encendido.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "p2p_chat_config")
public class P2PChatConfig {

    @Id
    private Integer id;

    @Column(name = "auto_envio_cuenta", nullable = false)
    private Boolean autoEnvioCuenta = true;
}
