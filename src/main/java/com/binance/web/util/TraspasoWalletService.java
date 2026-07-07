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
                        .map(w -> w.trim().toLowerCase())
                        .filter(w -> !w.isEmpty())
                        .collect(Collectors.toSet());
    }

    /** ¿La dirección es una wallet de traspaso conocida (Bybit)? */
    public boolean esWalletTraspaso(String address) {
        if (address == null || address.isBlank()) return false;
        return wallets.contains(address.trim().toLowerCase());
    }
}
