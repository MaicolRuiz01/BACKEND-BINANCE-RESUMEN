package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "spot_order",
  uniqueConstraints = @UniqueConstraint(columnNames = {"account_binance_id","orderId"}))
@Data 
@NoArgsConstructor 
@AllArgsConstructor
public class SpotOrder {
  @Id 
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY) 
  @JoinColumn(name="account_binance_id", nullable=false)
  private AccountBinance account;       // BINANCE

  private Long   orderId;               // de Binance
  private String clientOrderId;

  private String symbol;                // p.ej. TRXUSDT
  private String baseAsset;             // TRX
  private String quoteAsset;            // USDT

  private String side;                  // BUY / SELL
  private String type;                  // MARKET / LIMIT
  private String status;                // FILLED, etc.

  private Double executedBaseQty;       // suma ejecutada en BASE
  private Double executedQuoteQty;      // suma ejecutada en QUOTE (cummulativeQuoteQty)
  private Double avgPrice;              // tasa promedio (quote/base)

  private Double feeTotalUsdt;          // comisión total convertida a USDT
  private LocalDateTime filledAt;       // fecha/hora de ejecución (Bogotá)

  // JSON con el detalle por asset (opcional pero útil para auditoría)
  @Column(columnDefinition="TEXT")
  private String feeBreakdownJson;      // [{"asset":"BNB","qty":0.00123}, ...]
}
