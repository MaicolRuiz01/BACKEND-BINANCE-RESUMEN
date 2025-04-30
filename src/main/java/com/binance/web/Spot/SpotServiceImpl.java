package com.binance.web.Spot;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.Spot;
import com.binance.web.Repository.SpotRepository;

@Service
public class SpotServiceImpl implements SpotService{
	
	@Autowired
	private SpotRepository spotRepository;

	@Override
	public List<Spot> findAllOrdersSpot() {
		// TODO Auto-generated method stub
		return spotRepository.findAll();
	}

	@Override
	public Spot findByIdSpot(Integer id) {
		// TODO Auto-generated method stub
		return findByIdSpot(id);
	}

	@Override
	public void saveSpot(Spot Spot) {
		spotRepository.save(Spot);
	}

	@Override
	public void updateSpot(Integer id, Spot sale) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deleteSpot(Integer id) {
		spotRepository.deleteById(id);
	}
	// Método para actualizar una orden Spot existente
    @Override
    public void updateSpotOrder(Integer id, Spot spotOrder) {
        if (spotRepository.existsById(id)) {
            spotOrder.setId(id);
            spotRepository.save(spotOrder);  // Guardamos la orden actualizada
        }
    }

}
