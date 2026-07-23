package com.binance.web.Entity;

/**
 * En qué está trabajando el operador durante su jornada.
 *
 * VENTA_USDT → está vendiendo USDT por P2P. Se vigila que entren ventas en curso:
 *              si pasan 5 min sin ninguna, se avisa por Telegram (y se repite cada 5 min
 *              mientras siga sin entrar nada). Sirve para cachar anuncio caído o cuenta restringida.
 *
 * CAJA       → está cuadrando caja/haciendo cuentas. No hay alarma (puede demorarse), pero
 *              cada hora se manda un aviso de estado con cuánto lleva.
 */
public enum ModoJornada {
    VENTA_USDT,
    CAJA
}
