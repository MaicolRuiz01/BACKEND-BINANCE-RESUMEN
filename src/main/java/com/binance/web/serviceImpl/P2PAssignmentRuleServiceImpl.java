package com.binance.web.serviceImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.P2PAssignmentRule;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.P2PAssignmentRuleRepository;
import com.binance.web.service.AccountCopService;
import com.binance.web.service.P2PAssignmentRuleService;

@Slf4j
@Service
public class P2PAssignmentRuleServiceImpl implements P2PAssignmentRuleService {

    @Autowired private P2PAssignmentRuleRepository ruleRepo;
    @Autowired private AccountBinanceRepository binanceRepo;
    @Autowired private AccountCopService accountCopService;

    @Override
    @Transactional
    public P2PAssignmentRule setRule(String binanceAccountName, Integer copAccountId) {
        AccountBinance binance = binanceRepo.findByName(binanceAccountName);
        if (binance == null) throw new RuntimeException("Cuenta Binance no encontrada: " + binanceAccountName);

        AccountCop cop = accountCopService.findByIdAccountCop(copAccountId);
        if (cop == null) throw new RuntimeException("Cuenta COP no encontrada: " + copAccountId);

        P2PAssignmentRule rule = ruleRepo.findByBinanceAccount_Name(binanceAccountName)
                .orElse(new P2PAssignmentRule());

        rule.setBinanceAccount(binance);
        rule.setCopAccount(cop);
        rule.setActive(true);
        rule.setUpdatedAt(LocalDateTime.now());

        P2PAssignmentRule saved = ruleRepo.save(rule);
        log.info("Regla P2P actualizada: {} → {} ({})", binanceAccountName, cop.getName(), cop.getId());
        return saved;
    }

    @Override
    public Optional<P2PAssignmentRule> getActiveRule(String binanceAccountName) {
        return ruleRepo.findByBinanceAccount_Name(binanceAccountName)
                .filter(r -> Boolean.TRUE.equals(r.getActive()));
    }

    @Override
    public List<P2PAssignmentRule> getAllRules() {
        return ruleRepo.findAll();
    }

    @Override
    @Transactional
    public void pauseRule(String binanceAccountName) {
        ruleRepo.findByBinanceAccount_Name(binanceAccountName).ifPresentOrElse(r -> {
            r.setActive(false);
            r.setUpdatedAt(LocalDateTime.now());
            ruleRepo.save(r);
            log.info("Regla P2P pausada para: {}", binanceAccountName);
        }, () -> log.warn("No existe regla para pausar en cuenta: {}", binanceAccountName));
    }

    @Override
    @Transactional
    public void resumeRule(String binanceAccountName) {
        ruleRepo.findByBinanceAccount_Name(binanceAccountName).ifPresentOrElse(r -> {
            r.setActive(true);
            r.setUpdatedAt(LocalDateTime.now());
            ruleRepo.save(r);
            log.info("Regla P2P reactivada para: {}", binanceAccountName);
        }, () -> log.warn("No existe regla para reactivar en cuenta: {}", binanceAccountName));
    }

    @Override
    @Transactional
    public void deleteRule(String binanceAccountName) {
        ruleRepo.findByBinanceAccount_Name(binanceAccountName).ifPresentOrElse(r -> {
            ruleRepo.delete(r);
            log.info("Regla P2P eliminada para: {}", binanceAccountName);
        }, () -> log.warn("No existe regla para eliminar en cuenta: {}", binanceAccountName));
    }
}
