package com.binance.web.Entity;

import jakarta.persistence.*;

@Entity
@Table(name = "brebe_key")
public class BrebeKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "llave", nullable = false, length = 500)
    private String llave;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_cop_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private AccountCop accountCop;

    public BrebeKey() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getLlave() { return llave; }
    public void setLlave(String llave) { this.llave = llave; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public AccountCop getAccountCop() { return accountCop; }
    public void setAccountCop(AccountCop accountCop) { this.accountCop = accountCop; }
}
