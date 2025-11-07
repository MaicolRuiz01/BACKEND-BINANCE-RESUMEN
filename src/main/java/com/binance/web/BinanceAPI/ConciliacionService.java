package com.binance.web.BinanceAPI;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.transacciones.TransaccionesRepository;

@Service
public class ConciliacionService {
  @Autowired private TransaccionesRepository transRepo;

  public void registrarRetiroInterno(String txId, String fromName, String toAddr,
                                     String coin, double amount, LocalDateTime fecha) {
    if (txId == null) return;
    if (transRepo.existsByTxId(txId)) return;
    // Guarda como traspaso (cuentaFrom = fromName, cuentaTo = address interna)
    // y luego ajusta balances cripto (ya lo haces en TransaccionesService)
  }

  public boolean esTraspasoPorTxId(String txId) {
    return txId != null && transRepo.existsByTxId(txId);
  }
}

