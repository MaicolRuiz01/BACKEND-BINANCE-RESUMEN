package com.binance.web.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.binance.web.Entity.AccountVes;

public interface AccountVesRepository extends JpaRepository<AccountVes, Integer> {
	
	Optional<AccountVes> findByName(String name);

    @Query("select coalesce(sum(a.balance),0) from AccountVes a")
    Double sumTotalBalance();

}
