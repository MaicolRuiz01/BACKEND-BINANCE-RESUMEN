package com.binance.web.futures;

import java.util.List;


public interface FuturesService {
	List<Futures> findAllOrdersFutures();
	Futures findByIdFutures(Integer id);
    void saveFutures(Futures Futures);
    void updateFutures(Integer id, Futures sale);
    void deleteFutures(Integer id);
    void updateFuturesOrder(Integer id, Futures futuresOrder);
}
