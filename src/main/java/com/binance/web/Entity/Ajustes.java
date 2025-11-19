package com.binance.web.Entity;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.binance.web.Entity.Movimiento;
import com.binance.web.Entity.Cliente;
import com.binance.web.Entity.Supplier;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Ajustes {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)

	@Column(unique = true)
    private Integer id;
	private String contenido;
    private Double monto;
 @ManyToOne
    @JoinColumn(name = "movimiento_id")
    private Movimiento movimiento;      // Relación directa

    @ManyToOne
    private Cliente usuarioCL;            // Quien hizo el cambio

    @ManyToOne
    private Supplier usuarioPR;            // Quien hizo el cambio

}
