package com.binance.web.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.binance.web.Entity.VesRatesConfig;
import com.binance.web.Repository.VesRatesConfigRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VesRatesConfigService {

    private final VesRatesConfigRepository repo;

    public VesRatesConfig getConfig() {

        VesRatesConfig cfg = repo.findById(1L).orElse(null);

        if (cfg == null) {
            cfg = new VesRatesConfig();
            cfg.setId(1L);
            cfg.setLastUpdate(LocalDateTime.now());
            cfg = repo.save(cfg);
        }

        return cfg;
    }


    public VesRatesConfig saveConfig(VesRatesConfig newCfg) {

        VesRatesConfig cfg = repo.findById(1L).orElse(null);

        if (cfg == null) {
            cfg = new VesRatesConfig();
            cfg.setId(1L);
        }

        cfg.setVes1(newCfg.getVes1());
        cfg.setTasa1(newCfg.getTasa1());
        cfg.setVes2(newCfg.getVes2());
        cfg.setTasa2(newCfg.getTasa2());
        cfg.setVes3(newCfg.getVes3());
        cfg.setTasa3(newCfg.getTasa3());
        cfg.setLastUpdate(LocalDateTime.now());

        return repo.save(cfg);
    }

}

