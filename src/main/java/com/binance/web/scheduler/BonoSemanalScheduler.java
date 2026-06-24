package com.binance.web.scheduler;

import com.binance.web.dto.RankingRetiradorDto;
import com.binance.web.service.RetiradorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cada lunes a las 00:05 AM (Bogotá) calcula el retirador que más
 * dinero retiró en la semana anterior y le aplica un bono de $20.000.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BonoSemanalScheduler {

    private static final double BONO = 20_000.0;

    private final RetiradorService retiradorService;

    // cron: segundos minutos horas díaMes mes díaSemana
    // "0 5 0 * * MON" = todos los lunes a las 00:05:00
    @Scheduled(cron = "0 5 0 * * MON", zone = "America/Bogota")
    public void aplicarBonoSemanal() {
        log.info("[Bono Semanal] Calculando top retirador de la semana...");
        List<RankingRetiradorDto> ranking = retiradorService.getRankingSemana();

        if (ranking.isEmpty()) {
            log.info("[Bono Semanal] Sin solicitudes completadas esta semana — no se aplica bono.");
            return;
        }

        RankingRetiradorDto ganador = ranking.get(0);
        retiradorService.aplicarBono(ganador.getRetiradorId(), BONO);
        log.info("[Bono Semanal] Bono de ${} aplicado a {} (retiró ${} esta semana)",
                BONO, ganador.getNombre(), ganador.getTotalRetirado());
    }
}
