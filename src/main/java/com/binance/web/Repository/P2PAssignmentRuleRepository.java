package com.binance.web.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.P2PAssignmentRule;

@Repository
public interface P2PAssignmentRuleRepository extends JpaRepository<P2PAssignmentRule, Integer> {

    /** Regla (activa o no) para una cuenta Binance por nombre. */
    Optional<P2PAssignmentRule> findByBinanceAccount_Name(String name);

    /** Solo reglas activas. */
    List<P2PAssignmentRule> findAllByActiveTrue();
}
