package com.binance.web.Repository;

import com.binance.web.Entity.P2PPreAsignacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface P2PPreAsignacionRepository extends JpaRepository<P2PPreAsignacion, Long> {

    Optional<P2PPreAsignacion> findByOrderNumber(String orderNumber);

    void deleteByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);
}
