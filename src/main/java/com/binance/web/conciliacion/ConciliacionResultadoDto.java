package com.binance.web.conciliacion;

import java.util.List;

/**
 * Cuerpo del POST que manda el bot de conciliación bancaria (Automatizacion
 * Bancolombia / conciliacion_bancaria.py) al terminar una tanda de revisión.
 *
 * Una fila por cuenta procesada. El bot NO manda el "desfase" — eso lo calcula
 * Pochonance con su propio saldo en vivo (ver ConciliacionBancariaServiceImpl),
 * porque entre que el bot lee el saldo de Pochonance en pantalla y termina de
 * revisar todas las cuentas del banco (puede tardar varios minutos), el saldo
 * real ya pudo haber cambiado por una venta P2P nueva.
 */
public class ConciliacionResultadoDto {

    private List<Item> resultados;

    public List<Item> getResultados() { return resultados; }
    public void setResultados(List<Item> resultados) { this.resultados = resultados; }

    public static class Item {

        /** Nombre de la cuenta tal como lo tiene el bot (de Bitwarden) — se empareja
         *  contra AccountCop.name (BANCOLOMBIA), ignorando tildes/mayúsculas. */
        private String cuenta;

        /** true = el bot logró loguearse en Bancolombia y leer el saldo (la cuenta
         *  NO está bloqueada, aunque el saldo no cuadre con Pochonance).
         *  false = no pudo loguearse o leer el saldo (posible bloqueo u otra falla). */
        private Boolean disponible;

        /** Saldo real de Bancolombia (disponible - cobros en proceso), YA escalado
         *  ÷1000 igual que los saldos de Pochonance. Null si disponible = false. */
        private Double saldoRealBanco;

        /** Lo que el bot alcanzó a leer en la propia página de Pochonance — SOLO para
         *  detectar anomalías de scraping (si difiere mucho del balance real guardado
         *  en Pochonance, probablemente el bot leyó la tarjeta equivocada). No se usa
         *  para calcular el desfase real. */
        private Double saldoPochonanceSegunBot;

        /** Detalle del error si disponible = false. */
        private String error;

        public String getCuenta() { return cuenta; }
        public void setCuenta(String cuenta) { this.cuenta = cuenta; }
        public Boolean getDisponible() { return disponible; }
        public void setDisponible(Boolean disponible) { this.disponible = disponible; }
        public Double getSaldoRealBanco() { return saldoRealBanco; }
        public void setSaldoRealBanco(Double saldoRealBanco) { this.saldoRealBanco = saldoRealBanco; }
        public Double getSaldoPochonanceSegunBot() { return saldoPochonanceSegunBot; }
        public void setSaldoPochonanceSegunBot(Double saldoPochonanceSegunBot) { this.saldoPochonanceSegunBot = saldoPochonanceSegunBot; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}
