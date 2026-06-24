package com.binance.web.Repository;

import com.binance.web.Entity.BrebeKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrebeKeyRepository extends JpaRepository<BrebeKey, Integer> {
    List<BrebeKey> findByAccountCopId(Integer accountCopId);
}
