package com.binance.web.service;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.Entity.VesRatesConfig;
import com.binance.web.Repository.VesRatesConfigRepository;
import com.binance.web.model.VesRatesConfigDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VesRatesConfigService {

    private final VesRatesConfigRepository repo;
    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    @Transactional(readOnly = true)
    public VesRatesConfig getConfig() {
        return repo.findById(1L).orElseGet(() -> {
            VesRatesConfig cfg = new VesRatesConfig();
            cfg.setId(1L);
            cfg.setLastUpdate(LocalDateTime.now(ZONE_BOGOTA));
            return repo.save(cfg);
        });
    }

    @Transactional
    public VesRatesConfig saveConfig(VesRatesConfigDto dto) {
        if (dto.getVes1() == null || dto.getVes1() <= 0) {
            throw new IllegalArgumentException("ves1 debe ser > 0");
        }
        if (dto.getTasa1() == null || dto.getTasa1() <= 0) {
            throw new IllegalArgumentException("tasa1 debe ser > 0");
        }

        VesRatesConfig cfg = repo.findById(1L).orElseGet(() -> {
            VesRatesConfig c = new VesRatesConfig();
            c.setId(1L);
            return c;
        });

        cfg.setVes1(dto.getVes1());
        cfg.setTasa1(dto.getTasa1());

        boolean tasaUnica = Boolean.TRUE.equals(dto.getTasaUnica())
                // fallback automático si no envían tasaUnica:
                || (dto.getVes2() == null && dto.getTasa2() == null && dto.getVes3() == null && dto.getTasa3() == null);

        if (tasaUnica) {
            cfg.setVes2(null);
            cfg.setTasa2(null);
            cfg.setVes3(null);
            cfg.setTasa3(null);
        } else {
            // si es modo 3 tasas, deben venir completas (o al menos coherentes)
            // (si quieres puedes permitir parcialmente, pero normalmente mejor exigir completas)
            if (dto.getVes2() == null || dto.getTasa2() == null || dto.getVes3() == null || dto.getTasa3() == null) {
                throw new IllegalArgumentException("Para 3 tasas debes enviar ves2/tasa2/ves3/tasa3");
            }
            cfg.setVes2(dto.getVes2());
            cfg.setTasa2(dto.getTasa2());
            cfg.setVes3(dto.getVes3());
            cfg.setTasa3(dto.getTasa3());
        }

        cfg.setLastUpdate(LocalDateTime.now(ZONE_BOGOTA));
        return repo.save(cfg);
    }
}