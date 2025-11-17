package com.binance.web.Repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.CryptoAverageRate;

@Repository
public interface CryptoAverageRateRepository extends JpaRepository<CryptoAverageRate, Long> {

    // Última tasa registrada para una cripto
    Optional<CryptoAverageRate> findTopByCriptoOrderByFechaCalculoDesc(String cripto);

    // Snapshot por día (por si quieres ver histórico)
    Optional<CryptoAverageRate> findByCriptoAndDia(String cripto, LocalDate dia);
}
