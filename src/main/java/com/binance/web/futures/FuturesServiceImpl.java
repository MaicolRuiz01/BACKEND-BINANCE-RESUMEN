package com.binance.web.futures;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FuturesServiceImpl implements FuturesService {

	@Autowired
	private FuturesRepository futuresRepository;
	
	@Override
	public List<Futures> findAllOrdersFutures() {
		// TODO Auto-generated method stub
		return futuresRepository.findAll();
	}

	@Override
	public Futures findByIdFutures(Integer id) {
		// TODO Auto-generated method stub
		return findByIdFutures(id);
	}

	@Override
	public void saveFutures(Futures Futures) {
		// TODO Auto-generated method stub
		futuresRepository.save(Futures);
	}

	@Override
	public void updateFutures(Integer id, Futures Futures) {
		// TODO Auto-generated method stub
		Futures futures=futuresRepository.findById(id).orElse(null);
		futuresRepository.save(futures);
		
	}

	@Override
	public void deleteFutures(Integer id) {
		// TODO Auto-generated method stub
		futuresRepository.deleteById(id);
		
	}

	@Override
	public void updateFuturesOrder(Integer id, Futures futuresOrder) {
		// TODO Auto-generated method stub
		
	}

	

}
