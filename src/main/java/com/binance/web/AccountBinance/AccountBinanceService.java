package com.binance.web.AccountBinance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.model.CryptoBalanceDto;

public interface AccountBinanceService {
    List<AccountBinance> findAllAccountBinance();
    AccountBinance findByIdAccountBinance(Integer id);
    void saveAccountBinance(AccountBinance accountBinance);
    void updateAccountBinance(Integer id, AccountBinance accountBinance);
    void deleteAccountBinance(Integer id);
    AccountBinance findByName(String name);
    /** @deprecated usar updateOrCreateCryptoBalance o subtractCryptoBalance */
    @Deprecated
    void subtractBalance(String name, Double amount);
    Double getUSDTBalance(String name);
    Double getEstimatedUSDTBalance(String name);
    BigDecimal getTotalBalance();
    Double getFundingUSDTBalance(String name);
    BigDecimal getTotalBalanceInterno();
    Double getTotalExternalBalance();
	Double getCryptoBalance(String accountName, String cryptoSymbol);
	void subtractCryptoBalance(Integer accountId, String cryptoSymbol, Double amount);
	void updateOrCreateCryptoBalance(Integer accountId, String cryptoSymbol, Double amount);
	Double getInternalUsdBalance(String accountName);
    Double getTotalInternalUsdBalance();
    Double getTotalCryptoBalanceInterno(String cripto);
    void syncInternalBalancesFromExchange(String accountName);
    void syncAllInternalBalancesFromExchange();
    Double getTotalCryptoBalanceExterno(String cripto);
    Map<String, Double> getExternalBalancesSnapshot(String accountName);
	Map<String, Double> syncInternalBalancesFromExternal(String name);
	void syncAllInternalBalancesFromExternal();
	List<CryptoBalanceDto> getInternalBalancesDetail(String accountName);

}
