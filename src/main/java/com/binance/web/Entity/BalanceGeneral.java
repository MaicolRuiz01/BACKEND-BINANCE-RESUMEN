package com.binance.web.Entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BalanceGeneral {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	@Column(nullable = false)
    private LocalDate date;

    private Double saldo;
    private Double tasaPromedioDelDia;
    private Double tasaVentaDelDia;

    private Double totalP2PdelDia;
    private Double comisionesP2PdelDia;
    private Double cuatroPorMilDeVentas;
    private Double utilidadP2P;
    
    private Double totalVentasGeneralesDelDia;
    private Double utilidadVentasGenerales;
    
    private Double efectivoDelDia;
    private Double saldoClientes;
    
    private Double comisionTrust;
    private Double saldoCuentasBinance;
    private Double proveedores;
    private Double cuentasCop;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String detalleCriptosJson;
    private Double netoNoAsignadasUsdt;
    
    private Double saldosVES;
}
