package com.binance.web.Entity;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "account_cop")
public class AccountCop {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	private String name;
	private Double balance;
	
	@Enumerated(EnumType.STRING)
    @Column(name = "bank_type", nullable = false)
    private BankType bankType;
	
	@OneToMany(mappedBy = "accountCop", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonIgnore
	private List<SaleP2pAccountCop> saleP2pDetails;
	
	@OneToMany(mappedBy = "accountCop", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonIgnore
    private List<SellDollarsAccountCop> sellDollars;
	@Column(name = "saldo_inicial_del_dia")
    private Double saldoInicialDelDia;
	@JsonIgnore
	@Column(name = "cupo_diario_max")
    private Double cupoDiarioMax;

    @Column(name = "cupo_disponible_hoy")
    private Double cupoDisponibleHoy;

    @Column(name = "cupo_fecha")
    private LocalDate cupoFecha;
    
    @Column(name = "numero_cuenta")
    private String numeroCuenta;

    @Column(name = "cedula")
    private String cedula;
}
