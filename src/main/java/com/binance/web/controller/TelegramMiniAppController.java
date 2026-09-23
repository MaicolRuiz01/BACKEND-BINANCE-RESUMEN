package com.binance.web.controller;

import com.binance.web.Entity.Retirador;
import com.binance.web.Entity.SolicitudRetiro;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.RetiradorRepository;
import com.binance.web.dto.CuentaComprometidoDto;
import com.binance.web.dto.MiniAppSolicitudRetiroDto;
import com.binance.web.dto.SolicitudRetiroRequestDto;
import com.binance.web.service.RetiradorService;
import com.binance.web.service.TelegramInitDataValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API que consume la Mini App de Telegram "📤 Solicitar retiro" (página
 * estática servida en /miniapp/retiro.html). Reemplaza el flujo de checkboxes
 * cuenta-por-cuenta por botones inline de Telegram (lento con varias cuentas)
 * por un formulario único dentro de la Mini App.
 *
 * Sin JWT: el retirador no hace login acá — su identidad se valida con el
 * initData que Telegram firma del lado del cliente (ver
 * TelegramInitDataValidator), igual de confiable pero sin pedirle contraseña.
 */
@Slf4j
@RestController
@RequestMapping("/telegram/miniapp")
@RequiredArgsConstructor
public class TelegramMiniAppController {

    private final AccountCopRepository accountCopRepository;
    private final RetiradorRepository retiradorRepository;
    private final RetiradorService retiradorService;

    @Value("${app.telegram.bot-token}")
    private String botToken;

    /**
     * A diferencia de etiquetaCuenta() (usado en el resto del bot de
     * Telegram), esta pantalla SÍ muestra el nombre real del dueño — pedido
     * explícito de Milton (22/09/2026) para que la Mini App se vea igual que
     * el modal "Solicitar retiro" de la web de administración, que también
     * muestra nombres reales.
     */
    public record CuentaDto(
            Integer id,
            String nombre,
            Double saldo,
            Double cupoCajeroDisponibleHoy,
            Double cupoCorresponsalDisponibleHoy,
            Double montoComprometido,
            Double montoCajeroComprometido,
            Double montoCorresponsalComprometido) {
    }

    @GetMapping("/cuentas")
    public List<CuentaDto> cuentas() {
        Map<Integer, CuentaComprometidoDto> comprometidoPorCuenta = new HashMap<>();
        for (CuentaComprometidoDto c : retiradorService.obtenerMontosComprometidos()) {
            comprometidoPorCuenta.put(c.getCuentaCopId(), c);
        }

        return accountCopRepository.findAll().stream()
                .filter(c -> c.getBalance() != null && c.getBalance() > 0)
                .sorted((a, b) -> Double.compare(b.getBalance(), a.getBalance()))
                .map(c -> {
                    CuentaComprometidoDto comp = comprometidoPorCuenta.get(c.getId());
                    return new CuentaDto(
                            c.getId(),
                            c.getName(),
                            c.getBalance(),
                            c.getCupoCajeroDisponibleHoy(),
                            c.getCupoCorresponsalDisponibleHoy(),
                            comp != null ? comp.getMontoComprometido() : 0.0,
                            comp != null ? comp.getMontoCajeroComprometido() : 0.0,
                            comp != null ? comp.getMontoCorresponsalComprometido() : 0.0);
                })
                .collect(Collectors.toList());
    }

    @PostMapping("/solicitar-retiro")
    public ResponseEntity<?> solicitarRetiro(@RequestBody MiniAppSolicitudRetiroDto body) {
        TelegramInitDataValidator.TelegramUsuario usuario =
                TelegramInitDataValidator.validar(body.getInitData(), botToken);
        if (usuario == null) {
            return ResponseEntity.status(401).body(Map.of("error",
                    "No se pudo verificar tu identidad de Telegram. Cierra y vuelve a abrir la Mini App desde el bot."));
        }

        Retirador retirador = retiradorRepository.findByTelegramChatId(usuario.id()).orElse(null);

        // Si aún no tiene chat_id vinculado (nunca le dio /start al bot), lo
        // buscamos por @username y, si aparece, lo vinculamos de una vez —
        // mismo auto-registro que ya hace el webhook en /start y en "accept".
        if (retirador == null && usuario.username() != null && !usuario.username().isBlank()) {
            String clean = usuario.username().startsWith("@") ? usuario.username().substring(1) : usuario.username();
            retirador = retiradorRepository.findByTelegramUsernameIgnoreCase(clean)
                    .or(() -> retiradorRepository.findByTelegramUsernameIgnoreCase("@" + clean))
                    .orElse(null);
            if (retirador != null && retirador.getTelegramChatId() == null) {
                retirador.setTelegramChatId(usuario.id());
                retiradorRepository.save(retirador);
            }
        }

        if (retirador == null) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "No estás registrado como retirador. Mándale /start al bot desde tu cuenta de retirador primero."));
        }

        if (body.getDetalles() == null || body.getDetalles().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Selecciona al menos una cuenta."));
        }

        SolicitudRetiroRequestDto dto = new SolicitudRetiroRequestDto();
        dto.setRetiradorId(retirador.getId());
        dto.setDetalles(body.getDetalles());

        try {
            SolicitudRetiro creada = retiradorService.crearSolicitud(dto);
            return ResponseEntity.ok(Map.of("solicitudId", creada.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[MiniApp Retiro] Error creando solicitud", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Hubo un error inesperado. Intenta de nuevo."));
        }
    }
}
