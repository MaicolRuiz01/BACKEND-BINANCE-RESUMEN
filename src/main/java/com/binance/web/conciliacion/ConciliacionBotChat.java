package com.binance.web.conciliacion;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Fila única (id fijo = 1) con el chat_id de Telegram del bot de conciliación
 * bancaria (Automatizacion Bancolombia / conciliacion_bancaria.py), registrado
 * por el bot mismo vía POST /conciliacion/registrar-chat la primera vez que
 * corre en un computador nuevo (ver _chat_id() en el script).
 *
 * Pochonance usa este chat_id para avisarle al bot, por Telegram, qué cuenta
 * conciliar apenas se activa en "Cuentas P2P" — ver
 * ConciliacionBancariaServiceImpl.solicitarConciliacion.
 */
@Entity
@Table(name = "conciliacion_bot_chat")
public class ConciliacionBotChat {

    @Id
    private Integer id;

    private Long chatId;

    private LocalDateTime registradoEn;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }
    public LocalDateTime getRegistradoEn() { return registradoEn; }
    public void setRegistradoEn(LocalDateTime registradoEn) { this.registradoEn = registradoEn; }
}
