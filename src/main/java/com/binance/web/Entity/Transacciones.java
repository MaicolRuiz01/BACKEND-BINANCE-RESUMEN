package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
    name = "transacciones",
    indexes = {
        // existsByTxId se consulta por cada traspaso detectado, en cada ciclo de importación.
        // Sin índice se recorría la tabla completa cada vez.
        @Index(name = "idx_transacciones_tx_id", columnList = "txId"),
        @Index(name = "idx_transacciones_fecha", columnList = "fecha")
    }
)
public class Transacciones {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	private Double cantidad;
	private LocalDateTime fecha;
	@ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_to_id")
    private AccountBinance cuentaTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_from_id")
    private AccountBinance cuentaFrom;
    private String tipo;
    private String idtransaccion;
    private String txId;
}
