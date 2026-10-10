package com.binance.web.BinanceAPI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Liberar (soltar el cripto de) una venta P2P desde Pochonance.
 *
 * OJO — API NO SOPORTADA POR BINANCE: los endpoints que usa (orderMatch/checkIfCanReleaseCoin y
 * orderMatch/releaseCoin) están marcados como deprecados y, según un moderador del foro de
 * desarrolladores de Binance, solo funcionan para "socios en lista blanca". Se intenta igual;
 * si Binance lo rechaza, se devuelve su motivo tal cual y la pantalla ofrece abrir la orden en
 * Binance para liberarla allá.
 *
 * Seguridad:
 *  - El código de Google Authenticator lo escribe el operador en cada liberación. Nunca se
 *    guarda la clave del 2FA en el servidor.
 *  - Solo se libera una orden en estado BUYER_PAYED (el cliente marcó que pagó) según el último
 *    poll. Liberar es IRREVERSIBLE: la pantalla exige confirmar que el pago llegó.
 *  - Una sola liberación en curso por orden (doble clic / dos pantallas).
 *  - Queda en el log quién liberó qué orden y qué respondió Binance.
 */
@Slf4j
@Service
public class P2PLiberacionService {

    private static final String PATH_VERIFICAR = "/sapi/v1/c2c/orderMatch/checkIfCanReleaseCoin";
    private static final String PATH_LIBERAR = "/sapi/v1/c2c/orderMatch/releaseCoin";
    private static final Pattern CODIGO_2FA = Pattern.compile("\\d{6}");

    @Autowired private BinanceService binanceService;
    @Autowired @Lazy private P2PActiveOrderService activeOrderService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> liberandoAhora = ConcurrentHashMap.newKeySet();

    public Map<String, Object> liberar(String orderNumber, String accountBinance, String codigo2fa, String operador) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderNumber", orderNumber);

        if (orderNumber == null || orderNumber.isBlank() || accountBinance == null || accountBinance.isBlank()) {
            return error(out, "validacion", "Faltan la orden o la cuenta Binance.");
        }
        String codigo = codigo2fa == null ? "" : codigo2fa.trim();
        if (!CODIGO_2FA.matcher(codigo).matches()) {
            return error(out, "validacion", "El código de Google Authenticator debe tener 6 dígitos.");
        }

        String estado = activeOrderService.estadoConocido(orderNumber);
        if (!"BUYER_PAYED".equalsIgnoreCase(estado)) {
            return error(out, "validacion", estado == null
                    ? "Todavía no se conoce el estado de la orden. Espera a que se actualice la lista y reintenta."
                    : "La orden está en " + estado + ": solo se libera cuando el cliente marcó que pagó.");
        }

        if (!liberandoAhora.add(orderNumber)) {
            return error(out, "validacion", "Esta orden ya se está liberando.");
        }
        try {
            // 1) Verificación previa, SIN el código 2FA (para no gastarlo): dice si Binance deja
            //    liberar esta orden por API. Aquí es donde se ve si la cuenta no está habilitada.
            JsonNode verif = llamar(accountBinance, PATH_VERIFICAR, Map.of("orderNumber", orderNumber));
            if (!exito(verif)) {
                return rechazo(out, "verificacion", verif, orderNumber, accountBinance, operador);
            }
            if (verif.has("data") && verif.get("data").isBoolean() && !verif.get("data").asBoolean()) {
                out.put("respuestaCruda", verif.toString());
                return error(out, "verificacion", "Binance indica que esta orden no se puede liberar en este momento.");
            }

            // 2) Liberación con el código del operador.
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("orderNumber", orderNumber);
            cuerpo.put("authType", "GOOGLE");
            cuerpo.put("googleVerifyCode", codigo);
            cuerpo.put("code", codigo);
            JsonNode lib = llamar(accountBinance, PATH_LIBERAR, cuerpo);
            if (!exito(lib)) {
                return rechazo(out, "liberacion", lib, orderNumber, accountBinance, operador);
            }

            log.warn("[Liberar] {} LIBERÓ la orden {} ({}) desde Pochonance.", operador, orderNumber, accountBinance);
            out.put("ok", true);
            out.put("mensaje", "Orden liberada en Binance.");
            return out;
        } catch (Exception e) {
            log.error("[Liberar] Error liberando {} ({}) por {}: {}", orderNumber, accountBinance, operador, e.getMessage());
            return error(out, "conexion", "No se pudo hablar con Binance: " + e.getMessage()
                    + ". Revisa en Binance si la orden quedó liberada antes de reintentar.");
        } finally {
            liberandoAhora.remove(orderNumber);
        }
    }

    private JsonNode llamar(String cuenta, String path, Map<String, Object> cuerpo) throws Exception {
        return mapper.readTree(binanceService.c2cPostJson(cuenta, path, cuerpo));
    }

    private static boolean exito(JsonNode r) {
        return "000000".equals(r.path("code").asText()) && r.path("success").asBoolean(true);
    }

    private Map<String, Object> rechazo(Map<String, Object> out, String paso, JsonNode r,
                                        String orderNumber, String cuenta, String operador) {
        String msg = r.path("message").asText(r.path("msg").asText("Binance rechazó la operación."));
        log.warn("[Liberar] Binance rechazó ({}) la orden {} ({}) pedida por {}: {} {}",
                paso, orderNumber, cuenta, operador, r.path("code").asText(), msg);
        out.put("codigoBinance", r.path("code").asText(null));
        out.put("respuestaCruda", r.toString());
        return error(out, paso, msg);
    }

    private static Map<String, Object> error(Map<String, Object> out, String paso, String mensaje) {
        out.put("ok", false);
        out.put("paso", paso);
        out.put("mensaje", mensaje);
        return out;
    }
}
