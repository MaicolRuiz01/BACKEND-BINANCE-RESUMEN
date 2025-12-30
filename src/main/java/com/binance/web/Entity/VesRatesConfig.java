package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VesRatesConfig {
	
	@Id
    private Long id = 1L; // siempre 1, solo 1 registro

    // Tramo 1
    private Double ves1;
    private Double tasa1;

    // Tramo 2
    private Double ves2;
    private Double tasa2;

    // Tramo 3
    private Double ves3;
    private Double tasa3;

    private LocalDateTime lastUpdate;

}
