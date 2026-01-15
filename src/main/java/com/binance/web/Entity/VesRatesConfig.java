package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ves_rates_config")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VesRatesConfig {

    @Id
    private Long id = 1L; // siempre 1, solo 1 registro

    // Tramo 1 (siempre)
    private Double ves1;
    private Double tasa1;

    // Tramo 2 (opcional)
    private Double ves2;
    private Double tasa2;

    // Tramo 3 (opcional)
    private Double ves3;
    private Double tasa3;

    private LocalDateTime lastUpdate;

    @Transient
    public boolean isTasaUnica() {
        return ves2 == null && tasa2 == null && ves3 == null && tasa3 == null;
    }
}


