package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
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
	
	

}
