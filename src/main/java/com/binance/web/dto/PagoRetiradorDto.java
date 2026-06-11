package com.binance.web.dto;

public class PagoRetiradorDto {

    /** "COP" o "CAJA" */
    private String fuente;

    /** ID de la cuenta COP (si fuente = COP) */
    private Integer cuentaCopId;

    /** ID de la caja/efectivo (si fuente = CAJA) */
    private Integer cajaId;

    /** Monto a pagar */
    private Double monto;

    public String getFuente()          { return fuente; }
    public void   setFuente(String f)  { this.fuente = f; }

    public Integer getCuentaCopId()              { return cuentaCopId; }
    public void    setCuentaCopId(Integer id)   { this.cuentaCopId = id; }

    public Integer getCajaId()               { return cajaId; }
    public void    setCajaId(Integer id)     { this.cajaId = id; }

    public Double getMonto()             { return monto; }
    public void   setMonto(Double m)     { this.monto = m; }
}
