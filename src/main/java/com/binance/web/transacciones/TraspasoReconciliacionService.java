package com.binance.web.transacciones;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Entity.Transacciones;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.service.AccountBinanceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Detecta transferencias internas Binance↔TRON que se importaron por separado como
 * una COMPRA (entrada) y una VENTA (salida) sin asignar, las empareja por MONTO + HORA
 * entre dos cuentas distintas tuyas, y las convierte en un TRASPASO:
 *   - registra la Transacción (origen → destino),
 *   - ajusta el saldo cripto (la compra ya sumó en destino al importarse; aquí resta en origen),
 *   - borra la compra y la venta.
 *
 * Tolerancias ajustables abajo. Riesgo asumido: si una venta real y una compra real
 * coinciden en monto y hora, podrían emparejarse por error (por eso la ventana es corta).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraspasoReconciliacionService {

    private final BuyDollarsRepository buyRepo;
    private final SellDollarsRepository sellRepo;
    private final AccountBinanceRepository accountRepo;
    private final AccountBinanceService accountBinanceService;
    private final TransaccionesRepository transRepo;

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    /** Diferencia máxima de monto entre las dos patas (comisión de red / redondeo).
     *  Los montos están en escala "miles" (÷1000), así que 0.005 ≈ 5 USDT reales. */
    private static final double TOLERANCIA_MONTO = 0.005;
    /** Ventana de tiempo máxima entre la salida y la entrada. */
    private static final long VENTANA_MINUTOS = 60;

    @Transactional
    public int reconciliarTraspasos() {
        LocalDate hoy = LocalDate.now(ZONE);
        LocalDateTime inicio = hoy.atStartOfDay();
        LocalDateTime fin = hoy.atTime(LocalTime.MAX);

        List<BuyDollars> buys = buyRepo.findByAsignadaFalseAndDateBetween(inicio, fin);
        List<SellDollars> sells = sellRepo.findByAsignadoFalseAndDateBetween(inicio, fin);
        if (buys.isEmpty() || sells.isEmpty()) return 0;

        Set<String> idsRegistrados = transRepo.findAllTransaccionIds();
        Set<Integer> buysUsados = new HashSet<>();
        int emparejados = 0;

        for (SellDollars sell : sells) {
            if (sell.getDollars() == null || sell.getNameAccount() == null || sell.getDate() == null) continue;

            BuyDollars match = null;
            for (BuyDollars buy : buys) {
                if (buy.getId() != null && buysUsados.contains(buy.getId())) continue;
                if (buy.getAmount() == null || buy.getNameAccount() == null || buy.getDate() == null) continue;
                // Debe ser entre dos cuentas DISTINTAS (traspaso interno).
                if (sell.getNameAccount().equalsIgnoreCase(buy.getNameAccount())) continue;
                // Mismo monto (± tolerancia por comisión).
                if (Math.abs(sell.getDollars() - buy.getAmount()) > TOLERANCIA_MONTO) continue;
                // Dentro de la ventana de tiempo.
                long minutos = Math.abs(Duration.between(sell.getDate(), buy.getDate()).toMinutes());
                if (minutos > VENTANA_MINUTOS) continue;
                match = buy;
                break;
            }
            if (match == null) continue;

            // Es un traspaso: origen = cuenta de la venta (salida), destino = cuenta de la compra (entrada).
            AccountBinance origen = accountRepo.findByName(sell.getNameAccount());
            AccountBinance destino = accountRepo.findByName(match.getNameAccount());
            String symbol = match.getCryptoSymbol() != null ? match.getCryptoSymbol() : "USDT";

            // Si NO se identifica NINGUNA de las dos cuentas, no es un traspaso útil: quedaría
            // "Externa → Externa" (un registro basura del que no se sabe origen ni destino).
            // Se deja la compra/venta SIN tocar (no se crea el traspaso ni se borran) para que,
            // cuando las cuentas estén bien registradas, se pueda resolver de verdad.
            if (origen == null && destino == null) {
                log.warn("[ReconTraspaso] Par {}/{} sin cuentas identificadas ({} / {}) → NO se registra como traspaso.",
                        sell.getNameAccount(), match.getNameAccount(), sell.getDollars(), match.getAmount());
                continue;
            }

            // La compra ya sumó el cripto en destino al importarse. Falta restarlo en origen.
            if (origen != null) {
                // dollars está en miles; el saldo cripto va en USDT reales → ×1000.
                accountBinanceService.subtractCryptoBalance(origen.getId(), symbol, sell.getDollars() * 1000.0);
            }

            // Registrar la transacción (traspaso) para el historial.
            String idTx = "TRASPASO-"
                    + (sell.getIdWithdrawals() != null ? sell.getIdWithdrawals() : "S" + sell.getId())
                    + "-" + (match.getIdDeposit() != null ? match.getIdDeposit() : "B" + match.getId());
            if (!idsRegistrados.contains(idTx)) {
                Transacciones t = new Transacciones();
                t.setIdtransaccion(idTx);
                // Los traspasos existentes guardan cantidad en crudo (USDT reales) → ×1000.
                t.setCantidad(match.getAmount() == null ? null : match.getAmount() * 1000.0);
                t.setFecha(match.getDate());
                t.setTipo(symbol);
                t.setCuentaFrom(origen);
                t.setCuentaTo(destino);
                transRepo.save(t);
                idsRegistrados.add(idTx);
            }

            // Ya no son compra/venta: son un traspaso.
            buyRepo.delete(match);
            sellRepo.delete(sell);
            if (match.getId() != null) buysUsados.add(match.getId());
            emparejados++;

            log.info("[ReconTraspaso] {} {} ({}) → {} ({}) convertido en traspaso interno",
                    symbol, sell.getDollars(), sell.getNameAccount(), match.getAmount(), match.getNameAccount());
        }

        if (emparejados > 0) {
            log.info("[ReconTraspaso] {} par(es) compra/venta convertidos en traspaso interno.", emparejados);
        }
        return emparejados;
    }
}
