package com.binance.web.Spot;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name="spot")
public class Spot {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
    private Long orderId;  // ID de la orden
    private String clientOrderId;  // ID de la orden del cliente
    private String price;  // Precio de la orden
    private String origQty;  // Cantidad original
    private String executedQty;  // Cantidad ejecutada
    private String cummulativeQuoteQty;  // Cantidad acumulada en la divisa cotizada
    private String status;  // Estado de la orden (FILLED, CANCELED, PENDING)
    private String timeInForce;  // Tiempo de vigencia de la orden (GTC, IOC, etc.)
    private String type;  // Tipo de orden (MARKET, LIMIT)
    private String side;  // Lado de la orden (BUY, SELL)
    private String stopPrice;  // Precio de activación (si es una orden stop)
    private String icebergQty;  // Cantidad iceberg (si existe)
    private Long time;  // Tiempo de creación de la orden (timestamp)
    private Long updateTime;  // Última actualización de la orden (timestamp)
    private Boolean isWorking;  // Indica si la orden sigue activa
    private Long workingTime;  // El tiempo de la orden mientras está trabajando
    private String origQuoteOrderQty;  // Cantidad original de la orden en la divisa cotizada
    private String selfTradePreventionMode;  // Modo de prevención de auto-intercambio (EXPIRE_MAKER)
	

}
