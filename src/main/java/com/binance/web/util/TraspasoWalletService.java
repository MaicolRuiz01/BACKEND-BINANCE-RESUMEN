package com.binance.web.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Wallets externas (Bybit u otras) cuyas transferencias NO son compra/venta sino TRASPASO.
 * La lista viene de application.properties (app.bybit.wallets), separada por comas,
 * para poder ampliarla sin tocar código.
 */
@Service
public class TraspasoWalletService {

    private final Set<String> wallets;

    public TraspasoWalletService(@Value("${app.bybit.wallets:}") String walletsCsv) {
        this.wallets = (walletsCsv == null || walletsCsv.isBlank())
                ? new HashSet<>()
                : Arrays.stream(walletsCsv.split(","))
                        .map(TraspasoWalletService::normalizar)
                        .filter(w -> !w.isEmpty())
                        .collect(Collectors.toSet());
    }

    /** ¿La dirección es una wallet de traspaso conocida (Bybit)? Compara en formato normalizado
     *  (sin "0x", en minúsculas), y la config trae Base58 y hex, así coincide venga en el formato
     *  que venga de la API de TRON. */
    public boolean esWalletTraspaso(String address) {
        if (address == null || address.isBlank()) return false;
        return wallets.contains(normalizar(address));
    }

    /**
     * Normaliza una dirección TRON a un núcleo comparable. La misma dirección puede venir en:
     *  - Base58  (T...)              → se deja tal cual (en minúsculas)
     *  - hex TRON (41 + 20 bytes)    → se le quita el prefijo 41
     *  - hex ETH (0x + 20 bytes)     → se le quita el 0x
     * Así los formatos hex (41c686… y 0xc686…) quedan como el mismo núcleo (c686…) y coinciden.
     */
    private static String normalizar(String w) {
        if (w == null) return "";
        String s = w.trim().toLowerCase();
        if (s.startsWith("0x")) s = s.substring(2);
        // hex TRON: 41 + 20 bytes (40 hex) = 42 chars. Quitamos el 41 para dejar solo el núcleo.
        if (s.length() == 42 && s.startsWith("41")) s = s.substring(2);
        return s;
    }
}
