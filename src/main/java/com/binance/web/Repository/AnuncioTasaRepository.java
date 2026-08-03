package com.binance.web.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.AnuncioTasa;

public interface AnuncioTasaRepository extends JpaRepository<AnuncioTasa, Integer> {

    Optional<AnuncioTasa> findByAdvNo(String advNo);
}
