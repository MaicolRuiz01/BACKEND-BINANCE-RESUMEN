package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name="pago_proveedor")
public class PagoProveedor {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	private Double amount;
	private LocalDateTime fecha;
	
	@ManyToOne
    @JoinColumn(name = "account_cop_id", nullable = false)
    private AccountCop accountCop;  // Cuenta desde la que se realiza el pago

    @ManyToOne
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier; 
    
    public PagoProveedor(Double amount, LocalDateTime fecha, AccountCop accountCop, Supplier supplier) {
        this.amount = amount;
        this.fecha= fecha;
        this.accountCop = accountCop;
        this.supplier = supplier;
    }

}
