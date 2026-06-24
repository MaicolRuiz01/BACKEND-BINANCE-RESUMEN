package com.binance.web.Repository;

import com.binance.web.Entity.Retirador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RetiradorRepository extends JpaRepository<Retirador, Long> {
    boolean existsByEfectivoId(Integer efectivoId);
    java.util.Optional<Retirador> findByTelegramUsernameIgnoreCase(String telegramUsername);
   