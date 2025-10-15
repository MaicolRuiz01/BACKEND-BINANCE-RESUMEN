package com.binance.web.Entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    private Double saldo; // saldo total del día
    private Double tasaPromedioDelDia;
    private Double tasaVentaDelDia;

    private Double totalP2PdelDia;
    private Double comisionesP2PdelDia;
    private Double cuatroPorMilDeVentas;
    private Double utilidadP2P;
    
    private Double totalVentasGeneralesDelDia;
    private Double utilidadVentasGenerales;
    
    private Double efectivoDelDia; //este va a hacer la suma de las cajas
    private Double saldoClientes; //saldo total de clientes
    
    private Double comisionTrust; //comisiones del las selldollars
    private Double saldoCuentasBinance;
    private Double proveedores;
    private Double cuentasCop;

}
