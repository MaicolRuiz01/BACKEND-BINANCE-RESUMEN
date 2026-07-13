package com.binance.web.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.binance.web.BinanceAPI.BybitService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Repository.AccountBinanceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Detección REAL de traspasos que salen de Bybit, por HASH on-chain (no por wallet hardcodeada).
 *
 * Idea: un traspaso interno (ej. Javier[Bybit] → Luis) aparece como un RETIRO en la cuenta que
 * envía y un DEPÓSITO en la que recibe, y AMBOS comparten el mismo hash (txID) on-chain.
 * Entonces, en vez de mirar si la dirección está en una lista (Bybit rota sus hot-wallets),
 * preguntamos: ¿el hash de este depósito coincide con un RETIRO de una de MIS cuentas Bybit?
 *  - Sí → es traspaso, y sabemos EXACTAMENTE de qué cuenta salió.
 *  - No → es una compra externa de verdad.
 *
 * Para no llamar la API de Bybit en cada depósito, se arma un índice (hash → cuenta Bybit)
 * cacheado 1 minuto. Todo defensivo: si Bybit falla, el índice queda vacío y no rompe el import.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraspasoBybitService {

    private final AccountBinanceRepository accountBinanceRepository;
    private final BybitService bybitService;

    /** hash on-chain (minúsculas) → cuenta Bybit que hizo ese retiro. */
    private volatile Map<String, AccountBinance> indiceRetiros = new HashMap<>();
    private volatile long indiceTs = 0L;
    private static final long TTL_MS = 60_000L; // 1 minuto

    /**
     * ¿De cuál de tus cuentas Bybit salió este hash? Devuelve la cuenta origen, o null si el
     * hash no corresponde a ningún retiro tuyo (entonces NO es un traspaso desde Bybit).
     */
    public AccountBinance cuentaOrigenPorHash(String txHash) {
        if (txHash == null || txHash.isBlank()) return null;
        refrescarSiVencido();
        AccountBinance acc = indiceRetiros.get(txHash.trim().toLowerCase());
        // DIAGNÓSTICO: muestra el hash buscado y si matcheó (quitar cuando ya funcione).
        log.info("[TraspasoBybit][DIAG] Busco hash del depósito = '{}' → {} (el índice tiene {} hash(es) de retiro).",
                txHash, acc != null ? acc.getName() : "NO-MATCH", indiceRetiros.size());
        return acc;
    }

    /** Reconstruye el índice consultando los retiros de todas las cuentas Bybit activas. */
    private synchronized void refrescarSiVencido() {
        boolean vigente = (System.currentTimeMillis() - indiceTs) < TTL_MS;
        if (vigente && !indiceRetiros.isEmpty()) return;

        Map<String, AccountBinance> nuevo = new HashMap<>();
        try {
            for (AccountBinance acc : accountBinanceRepository.findAll()) {
                // Acepta "BYBIT" y el typo común "BYBIP" (cualquier tipo que empiece por BYBI).
                if (acc.getTipo() == null || !acc.getTipo().trim().toUpperCase().startsWith("BYBI")) continue;
                if (!Boolean.TRUE.equals(acc.getActiva())) continue;
                try {
                    List<String> hashes = bybitService.getWithdrawalTxIds(acc.getApiKey(), acc.getApiSecret());
                    for (String h : hashes) {
                        if (h != null && !h.isBlank()) nuevo.put(h.trim().toLowerCase(), acc);
                    }
                } catch (Exception e) {
                    log.warn("[TraspasoBybit] No se pudieron leer retiros de {}: {}", acc.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[TraspasoBybit] Error armando el índice de retiros: {}", e.getMessage());
        }

        // Solo reemplaza el índice si obtuvimos algo; si todo falló, conserva el anterior.
        if (!nuevo.isEmpty() || indiceRetiros.isEmpty()) {
            indiceRetiros = nuevo;
            indiceTs = System.currentTimeMillis();
        }

        // DIAGNÓSTICO: qué hashes de retiro trajo Bybit (quitar cuando ya funcione).
        log.info("[TraspasoBybit][DIAG] Índice de retiros armado con {} hash(es): {}",
                indiceRetiros.size(), indiceRetiros.keySet());
    }
}
