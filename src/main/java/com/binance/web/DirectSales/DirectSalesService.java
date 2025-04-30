package com.binance.web.DirectSales;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.DirectSales;
import com.binance.web.Repository.DirectSalesRepository;

@Service
public class DirectSalesService {
    
    @Autowired
    private DirectSalesRepository repository;

    public Optional<DirectSales> findById(Integer id) {
        return repository.findById(id);
    }

    public DirectSales save(DirectSales directSales) {
        return repository.save(directSales);
    }

    public void deleteById(Integer id) {
        repository.deleteById(id);
    }

	public List<DirectSales> findAll() {
		// TODO Auto-generated method stub
		return repository.findAll();
	}
}
