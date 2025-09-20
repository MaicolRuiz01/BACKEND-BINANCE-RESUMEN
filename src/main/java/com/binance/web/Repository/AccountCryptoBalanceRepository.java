package com.binance.web.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCryptoBalance;

@Repository
public interface AccountCryptoBalanceRepository extends JpaRepository<AccountCryptoBalance, Integer> {

	Optional<AccountCryptoBalance> findByAccountBinance_IdAndCryptoSymbol(Integer accountBinanceId, String cryptoSymbol);

	List<AccountCryptoBalance> findByAccountBinance_Id(Integer id);
	
	interface SymbolQty {
        String getSymbol();
        Double getQty();
    }

    @Query("""
           select upper(b.cryptoSymbol) as symbol,
                  sum(coalesce(b.balance,0)) as qty
           from AccountCryptoBalance b
           group by upper(b.cryptoSymbol)
           """)
    List<SymbolQty> sumBySymbol();

    @Query("""
           select upper(b.cryptoSymbol) as symbol,
                  sum(coalesce(b.balance,0)) as qty
           from AccountCryptoBalance b
           where upper(b.accountBinance.name) = upper(:accountName)
           group by upper(b.cryptoSymbol)
           """)
    List<SymbolQty> sumBySymbolForAccount(@Param("accountName") String accountName);

    // Para stables dinámicas (símbolos distintos)
    @Query("select distinct upper(b.cryptoSymbol) from AccountCryptoBalance b")
    List<String> findDistinctSymbols();
    
 // busca por accountBinance.id y por cryptoSymbol
    Optional<AccountCryptoBalance> findByAccountBinanceIdAndCryptoSymbol(
            Integer accountBinanceId,
            String cryptoSymbol
    );



}
