package com.binance.web.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida el "initData" que manda la Mini App de Telegram (Web App) al backend
 * — confirma que el usuario que la abrió es de verdad quien Telegram dice que
 * es, sin que el retirador tenga que hacer login aparte. Nadie puede
 * falsificar este dato sin conocer el bot-token (Telegram lo firma del lado
 * del cliente con un HMAC que solo se puede recalcular con el token).
 *
 * Algoritmo oficial:
 * https://core.telegram.org/bots/webapps#validating-data-received-via-the-web-app
 *
 *   secret_key   = HMAC_SHA256(key="WebAppData", msg=bot_token)
 *   hash_esperado = HMAC_SHA256(key=secret_key, msg=data_check_string)
 *
 * data_check_string = todos los campos de initData (menos "hash"), YA
 * decodificados de la URL, ordenados alfabéticamente por nombre de campo,
 * como "campo=valor" uno por línea.
 */
public final class TelegramInitDataValidator {

    private TelegramInitDataValidator() {
    }

    // Cuánto tiempo (segundos) se acepta un initData desde que Telegram lo firmó
    // (auth_date), para que un initData viejo capturado y reenviado no sirva para
    // siempre. 24h es generoso a propósito — la Mini App normalmente se abre y se
    // usa en el momento, este límite es solo una red de seguridad.
    private static final long MAX_AUTH_AGE_SECONDS = 24 * 60 * 60;

    public record TelegramUsuario(Long id, String username) {
    }

    /**
     * Valida la firma y devuelve el usuario de Telegram que abrió la Mini App,
     * o null si la firma no es válida, está incompleta, o el initData es muy viejo.
     */
    public static TelegramUsuario validar(String initData, String botToken) {
        if (initData == null || initData.isBlank() || botToken == null || botToken.isBlank()) {
            return null;
        }
        try {
            Map<String, String> params = parseQueryString(initData);
            String hashRecibido = params.remove("hash");
            if (hashRecibido == null || hashRecibido.isBlank()) {
                return null;
            }

            String authDateStr = params.get("auth_date");
            if (authDateStr != null) {
                long authDate = Long.parseLong(authDateStr);
                long ahora = System.currentTimeMillis() / 1000;
                if (ahora - authDate > MAX_AUTH_AGE_SECONDS) {
                    return null;
                }
            }

            List<String> claves = new ArrayList<>(params.keySet());
            Collections.sort(claves);
            StringBuilder dataCheckString = new StringBuilder();
            for (int i = 0; i < claves.size(); i++) {
                if (i > 0) dataCheckString.append("\n");
                dataCheckString.append(claves.get(i)).append("=").append(params.get(claves.get(i)));
            }

            byte[] secretKey = hmacSha256(botToken.getBytes(StandardCharsets.UTF_8),
                    "WebAppData".getBytes(StandardCharsets.UTF_8));
            byte[] hashCalculado = hmacSha256(dataCheckString.toString().getBytes(StandardCharsets.UTF_8), secretKey);
            String hashCalculadoHex = bytesToHex(hashCalculado);

            if (!hashCalculadoHex.equalsIgnoreCase(hashRecibido)) {
                return null;
            }

            String userJson = params.get("user");
            if (userJson == null) {
                return null;
            }
            Long telegramId = extraerLong(userJson, "id");
            String username = extraerString(userJson, "username");
            if (telegramId == null) {
                return null;
            }
            return new TelegramUsuario(telegramId, username);
        } catch (Exception e) {
            return null;
        }
    }

    /** HMAC-SHA256 de {@code message} usando {@code key} como llave. */
    private static byte[] hmacSha256(byte[] message, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(message);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static Map<String, String> parseQueryString(String query) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            if (pair.isBlank()) continue;
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";
            result.put(urlDecode(key), urlDecode(value));
        }
        return result;
    }

    /**
     * Equivalente a decodeURIComponent de JS — a diferencia de
     * java.net.URLDecoder.decode "puro" (pensado para application/x-www-form-urlencoded),
     * NO debe convertir "+" en espacio, porque el campo "user" es JSON con
     * %-encoding tal cual lo arma Telegram.WebApp.initData en el cliente.
     */
    private static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    // Extracción mínima de campos de un JSON plano (id, username) sin traer una
    // librería nueva — el campo "user" de initData es siempre un objeto simple,
    // no anidado, así que una extracción por regex alcanza y no vale la pena
    // instanciar un ObjectMapper de Jackson solo para esto.
    private static Long extraerLong(String json, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : null;
    }

    private static String extraerString(String json, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }
}
