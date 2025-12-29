package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class VentaVES {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Fecha/hora de la operación
     */
    @Column(name = "date", nullable = false)
    private LocalDateTime date;

    /**
     * Monto vendido en bolívares (VES)
     */
    @Column(name = "bolivares", nullable = false)
    private Double bolivares;

    /**
     * Tasa en COP por 1 VES
     */
    @Column(name = "tasa", nullable = false)
    private Double tasa;

    /**
     * Total en pesos cobrados al cliente:
     *  pesos = bolivares * tasa
     */
    @Column(name = "pesos", nullable = false)
    private Double pesos;

    /**
     * Cliente al que se le venden los bolívares
     */
    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    /**
     * Proveedor de Bs asociado (opcional),
     * por si quieres trackear de qué proveedor
     * salió originalmente esa liquidez.
     */
    @ManyToOne
    @JoinColumn(name = "proveedor_id")
    private Supplier proveedor;

    /**
     * Cuenta COP donde entra el dinero de esta venta
     */
    @ManyToOne
    @JoinColumn(name = "cuenta_cop_id")
    private AccountCop cuentaCop;

}
