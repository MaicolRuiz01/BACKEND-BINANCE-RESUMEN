package com.binance.web.service;

import java.util.List;

import com.binance.web.model.AssignAccountDto;
import com.binance.web.model.BuyP2PDto;

public interface BuyP2PService {
	List<BuyP2PDto> getTodayNoAsignadas(String account);
    List<BuyP2PDto> getTodayNoAsignadasAllAccounts();
    String processAssignAccountCop(Integer buyId, List<AssignAccountDto> accounts);

}
