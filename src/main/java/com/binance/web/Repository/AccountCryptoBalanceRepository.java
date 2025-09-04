package com.binance.web.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCryptoBalance;

@Repository
public interface AccountCryptoBalanceRepository extends JpaRepository<AccountCryptoBalance, Integer> {

	Optional<AccountCryptoBalance> findByAccountBinance_IdAndCryptoSymbol(Integer accountBinanceId, String cryptoSymbol);

	List<AccountCryptoBalance> findByAccountBinance_Id(Integer id);

}
