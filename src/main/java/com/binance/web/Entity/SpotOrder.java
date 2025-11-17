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
@Table(name = "spot_order", uniqueConstraints = @UniqueConstraint(columnNames = { "account_binance_id",
		"id_orden_binance" }))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpotOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// -------- RELACIÓN CON LA CUENTA BINANCE --------

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "account_binance_id", nullable = false)
	private AccountBinance cuentaBinance;

	// -------- IDENTIFICADORES --------

	@Column(name = "id_orden_binance", nullable = false)
	private Long idOrdenBinance; // orderId oficial de Binance

	@Column(name = "id_orden_cliente")
	private String idOrdenCliente; // clientOrderId (si existe)

	// -------- INFO GENERAL --------

	@Column(nullable = false)
	private String simbolo; // Ej: BTCUSDT, TRXUSDT

	@Column(nullable = false)
	private String tipoOperacion; // COMPRA o VENTA

	// -------- DATOS DE LA CRIPTO Y MONTOS --------

	@Column(nullable = false)
	private String cripto; // Ej: BTC, TRX, SOL

	@Column(nullable = false)
	private Double cantidadCripto; // Cuánta cripto se compró/vendió

	@Column(nullable = false)
	private Double totalUsdt; // Total recibido/pagado en USDT

	@Column(nullable = false)
	private Double tasaUsdt; // Precio unitario en USDT

	// -------- COMISIÓN Y FECHA --------

	@Column(nullable = false)
	private Double comisionUsdt; // Comisión total en USDT

	@Column(nullable = false)
	private LocalDateTime fechaOperacion; // Fecha y hora de ejecución

	@Column(columnDefinition = "TEXT")
	private String detalleBinanceJson; // JSON original por si se necesita
}
