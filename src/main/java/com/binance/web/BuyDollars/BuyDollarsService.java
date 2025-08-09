package com.binance.web.BuyDollars;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.binance.web.Entity.BuyDollars;

public interface BuyDollarsService {

	BuyDollars createBuyDollars(BuyDollarsDto buyDollarsDto);

	BuyDollars getLastBuyDollars();
	
	public List<BuyDollars> getAllBuyDollars();

	BuyDollars updateBuyDollars(Integer id, BuyDollarsDto dto);

	void registrarComprasAutomaticamente();

	List<BuyDollarsDto> getComprasNoAsignadasHoy();

	BuyDollars asignarCompra(Integer id, BuyDollarsDto dto);
}
