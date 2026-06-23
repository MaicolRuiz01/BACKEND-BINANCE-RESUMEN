package com.binance.web.Entity;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

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

@Entity
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

    /** @deprecated — usar cupoCajeroDisponibleHoy + cupoCorresponsalDisponibleHoy */
    @Column(name = "cupo_disponible_hoy")
    private Double cupoDisponibleHoy;

    @Column(name = "cupo_fecha")
    private LocalDate cupoFecha;

    @Column(name = "cupo_cajero_disponible_hoy")
    private Double cupoCajeroDisponibleHoy;

    @Column(name = "cupo_corresponsal_disponible_hoy")
    private Double cupoCorresponsalDisponibleHoy;

    @Column(name = "numero_cuenta")
    private String numeroCuenta;

    @Column(name = "cedula")
    private String cedula;

    @Column(name = "activa_para_p2p", nullable = false)
    private Boolean activaParaP2P = false;

    /**
     * Tipo de cupo que se respeta al asignar esta cuenta en P2P.
     * Valores: "CAJERO", "CORRESPONSAL", "AMBOS" (default).
     */
    @Column(name = "cupo_tipo_p2p")
    private String cupoTipoP2P = "AMBOS";

    // ==================== CONSTRUCTORS ====================

    public AccountCop() {}

    public AccountCop(Integer id, String name, Double balance, BankType bankType,
            List<SaleP2pAccountCop> saleP2pDetails, List<SellDollarsAccountCop> sellDollars,
            Double saldoInicialDelDia, Double cupoDiarioMax, Double cupoDisponibleHoy,
            LocalDate cupoFecha, Double cupoCajeroDisponibleHoy, Double cupoCorresponsalDisponibleHoy,
            String numeroCuenta, String cedula, Boolean activaParaP2P) {
        this.id = id;
        this.name = name;
        this.balance = balance;
        this.bankType = bankType;
        this.saleP2pDetails = saleP2pDetails;
        this.sellDollars = sellDollars;
        this.saldoInicialDelDia = saldoInicialDelDia;
        this.cupoDiarioMax = cupoDiarioMax;
        this.cupoDisponibleHoy = cupoDisponibleHoy;
        this.cupoFecha = cupoFecha;
        this.cupoCajeroDisponibleHoy = cupoCajeroDisponibleHoy;
        this.cupoCorresponsalDisponibleHoy = cupoCorresponsalDisponibleHoy;
        this.numeroCuenta = numeroCuenta;
        this.cedula = cedula;
        this.activaParaP2P = activaParaP2P;
    }

    // ==================== GETTERS ====================

    public Integer getId() { return id; }
    public String getName() { return name; }
    public Double getBalance() { return balance; }
    public BankType getBankType() { return bankType; }
    public List<SaleP2pAccountCop> getSaleP2pDetails() { return saleP2pDetails; }
    public List<SellDollarsAccountCop> getSellDollars() { return sellDollars; }
    public Double getSaldoInicialDelDia() { return saldoInicialDelDia; }
    public Double getCupoDiarioMax() { return cupoDiarioMax; }
    public Double getCupoDisponibleHoy() { return cupoDisponibleHoy; }
    public LocalDate getCupoFecha() { return cupoFecha; }
    public Double getCupoCajeroDisponibleHoy() { return cupoCajeroDisponibleHoy; }
    public Double getCupoCorresponsalDisponibleHoy() { return cupoCorresponsalDisponibleHoy; }
    public String getNumeroCuenta() { return numeroCuenta; }
    public String getCedula() { return cedula; }
    public Boolean getActivaParaP2P() { return activaParaP2P; }
    public String getCupoTipoP2P() { return cupoTipoP2P; }

    // ==================== SETTERS ====================

    public void setId(Integer id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setBalance(Double balance) { this.balance = balance; }
    public void setBankType(BankType bankType) { this.bankType = bankType; }
    public void setSaleP2pDetails(List<SaleP2pAccountCop> saleP2pDetails) { this.saleP2pDetails = saleP2pDetails; }
    public void setSellDollars(List<SellDollarsAccountCop> sellDollars) { this.sellDollars = sellDollars; }
    public void setSaldoInicialDelDia(Double saldoInicialDelDia) { this.saldoInicialDelDia = saldoInicialDelDia; }
    public void setCupoDiarioMax(Double cupoDiarioMax) { this.cupoDiarioMax = cupoDiarioMax; }
    public void setCupoDisponibleHoy(Double cupoDisponibleHoy) { this.cupoDisponibleHoy = cupoDisponibleHoy; }
    public void setCupoFecha(LocalDate cupoFecha) { this.cupoFecha = cupoFecha; }
    public void setCupoCajeroDisponibleHoy(Double cupoCajeroDisponibleHoy) { this.cupoCajeroDisponibleHoy = cupoCajeroDisponibleHoy; }
    public void setCupoCorresponsalDisponibleHoy(Double cupoCorresponsalDisponibleHoy) { this.cupoCorresponsalDisponibleHoy = cupoCorresponsalDisponibleHoy; }
    public void setNumeroCuenta(String numeroCuenta) { this.numeroCuenta = numeroCuenta; }
    public void setCedula(String cedula) { this.cedula = cedula; }
    public void setActivaParaP2P(Boolean activaParaP2P) { this.activaParaP2P = activaParaP2P; }
    public void setCupoTipoP2P(String cupoTipoP2P) { this.cupoTipoP2P = cupoTipoP2P; }

    // ==================== equals / hashCode / toString ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountCop)) return false;
        AccountCop that = (AccountCop) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AccountCop{id=" + id + ", name='" + name + "', balance=" + balance
                + ", bankType=" + bankType + "}";
    }
}
