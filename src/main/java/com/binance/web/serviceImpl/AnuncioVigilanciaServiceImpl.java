package com.binance.web.serviceImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.binance.web.BinanceAPI.AnuncioDto;
import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.Entity.AnuncioTasa;
import com.binance.web.Repository.AnuncioTasaRepository;
import com.binance.web.service.AnuncioVigilanciaService;
import com.binance.web.service.TelegramService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementación de la vigilancia de anuncios propios de venta.
 *
 * Cachea la foto de anuncios durante {@code anuncios.vigilancia.cache-ms} para que, aunque
 * varias reglas y varias jornadas pregunten en el mismo ciclo, solo se llame a Binance UNA vez.
 *
 * Todo es defensivo: si Binance falla se conserva la última foto buena en vez de reportar
 * "no hay anuncio" (que pausaría jornadas de operadores que sí tienen el anuncio arriba).
 * Esa distinción es importante: un error de red no puede costarle el sueldo a alguien.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnuncioVigilanciaServiceImpl implements AnuncioVigilanciaService {

    private final BinanceService binanceService;
    private final AnuncioTasaRepository anuncioTasaRepository;
    private final TelegramService telegramService;

    @Value("${app.telegram.group-chat-id:}")
    private String grupoChatId;

    /** Cuánto vale la foto cacheada antes de volver a preguntarle a Binance. */
    @Value("${anuncios.vigilancia.cache-ms:50000}")
    private long cacheMs;

    /** Cambio mínimo de tasa (COP) para considerarlo un cambio real y no ruido de redondeo. */
    @Value("${anuncios.vigilancia.cambio-min-cop:0.5}")
    private double cambioMinimo;

    private volatile List<AnuncioDto> cache = List.of();
    private volatile long cacheAt = 0L;
    /** true si la última consulta a Binance respondió (aunque fuera con lista vacía). */
    private volatile boolean ultimaConsultaOk = false;

    @Override
    public synchronized List<AnuncioDto> anunciosVenta() {
        long ahora = System.currentTimeMillis();
        if (ahora - cacheAt < cacheMs) return cache;

        try {
            List<AnuncioDto> frescos = binanceService.obtenerMisAnunciosVenta();
            cache = frescos != null ? frescos : List.of();
            cacheAt = ahora;
            ultimaConsultaOk = true;
        } catch (Exception e) {
            // Se conserva la foto anterior a propósito: un fallo de red NO debe interpretarse
            // como "el operador quitó el anuncio".
            ultimaConsultaOk = false;
            cacheAt = ahora;
            log.warn("[Anuncios] No se pudo refrescar la foto de anuncios: {}", e.getMessage());
        }
        return cache;
    }

    /** ¿La última lectura fue confiable? Si no, las reglas que castigan deben abstenerse. */
    @Override
    public boolean datosConfiables() {
        return ultimaConsultaOk;
    }

    @Override
    public boolean hayAnuncioPublicado() {
        return !anunciosVenta().isEmpty();
    }

    @Override
    public Double tasaReferencia() {
        AnuncioDto ref = anuncioReferencia();
        return ref != null ? precio(ref) : null;
    }

    @Override
    public String advNoReferencia() {
        AnuncioDto ref = anuncioReferencia();
        return ref != null ? ref.getAdvNo() : null;
    }

    @Override
    public Double tasaDe(String advNo) {
        if (advNo == null || advNo.isBlank()) return null;
        return anunciosVenta().stream()
                .filter(a -> advNo.equals(a.getAdvNo()))
                .map(this::precio)
                .filter(p -> p != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * El anuncio "que manda": el de venta más barato de las cuentas propias, que es el que
     * realmente compite por las órdenes. Si el operador tiene varios, bajarle la tasa al
     * más caro no serviría de nada, por eso se vigila el más barato.
     */
    private AnuncioDto anuncioReferencia() {
        return anunciosVenta().stream()
                .filter(a -> precio(a) != null)
                .min(Comparator.comparingDouble(a -> precio(a)))
                .orElse(null);
    }

    @Override
    public int detectarYReportarCambiosDeTasa() {
        List<AnuncioDto> actuales = anunciosVenta();
        if (actuales.isEmpty() || !ultimaConsultaOk) return 0;

        int cambios = 0;
        List<String> reportes = new ArrayList<>();

        for (AnuncioDto a : actuales) {
            String advNo = a.getAdvNo();
            Double tasa = precio(a);
            if (advNo == null || advNo.isBlank() || tasa == null) continue;

            try {
                AnuncioTasa guardado = anuncioTasaRepository.findByAdvNo(advNo).orElse(null);

                if (guardado == null) {
                    // Primera vez que lo vemos: se registra sin reportar (no es un cambio).
                    AnuncioTasa nuevo = new AnuncioTasa();
                    nuevo.setAdvNo(advNo);
                    nuevo.setVendedor(a.getVendedor());
                    nuevo.setTasa(tasa);
                    nuevo.setActualizadoAt(LocalDateTime.now());
                    anuncioTasaRepository.save(nuevo);
                    continue;
                }

                double anterior = guardado.getTasa() != null ? guardado.getTasa() : tasa;
                double delta = tasa - anterior;

                if (Math.abs(delta) >= cambioMinimo) {
                    reportes.add(String.format("%s  %s → %s  (%s%s)",
                            a.getVendedor(),
                            fmt(anterior), fmt(tasa),
                            delta > 0 ? "▲ +" : "▼ ", fmt(Math.abs(delta))));
                    cambios++;
                    guardado.setTasa(tasa);
                    guardado.setActualizadoAt(LocalDateTime.now());
                    anuncioTasaRepository.save(guardado);
                }
            } catch (Exception e) {
                log.warn("[Anuncios] Error revisando el anuncio {}: {}", advNo, e.getMessage());
            }
        }

        if (!reportes.isEmpty() && grupoChatId != null && !grupoChatId.isBlank()) {
            String msg = "🔔 *Cambio de tasa en anuncios*\n\n" + String.join("\n", reportes);
            try {
                telegramService.sendMessage(grupoChatId, msg);
            } catch (Exception e) {
                log.warn("[Anuncios] No se pudo avisar el cambio de tasa: {}", e.getMessage());
            }
        }
        return cambios;
    }

    // ── Helpers ───────────────────────────────────────────────────

    /** El precio viene como texto desde Binance; se parsea tolerando comas y espacios. */
    private Double precio(AnuncioDto a) {
        if (a == null || a.getPrecio() == null) return null;
        try {
            return Double.parseDouble(a.getPrecio().trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String fmt(double v) {
        return String.format("%,.0f", v);
    }
}
