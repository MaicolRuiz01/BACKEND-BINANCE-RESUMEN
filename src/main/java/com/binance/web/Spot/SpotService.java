package com.binance.web.Spot;
import java.util.List;

import com.binance.web.Entity.Spot;

public interface SpotService {
	
	List<Spot> findAllOrdersSpot();
	Spot findByIdSpot(Integer id);
    void saveSpot(Spot Spot);
    void updateSpot(Integer id, Spot sale);
    void deleteSpot(Integer id);
   // void processAssignAccountCop(SpotDto sale);
    void updateSpotOrder(Integer id, Spot spotOrder);

}
