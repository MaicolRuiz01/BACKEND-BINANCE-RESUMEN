package com.binance.web.BinanceAPI;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.P2PChatConfig;
import com.binance.web.Entity.P2PPreAsignacion;
import com.binance.web.Repository.P2PChatConfigRepository;
import com.binance.web.Repository.P2PPreAsignacionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Chat de las órdenes P2P de Binance.
 *
 * Cómo funciona (API no documentada públicamente por Binance, ver retrieveChatCredential):
 *  1) GET /sapi/v1/c2c/chat/retrieveChatCredential con la API key de la cuenta Binance (BD)
 *     devuelve chatWssUrl + listenKey + listenToken. Basta con permiso de lectura.
 *  2) Se abre el WebSocket  {chatWssUrl}/{listenKey}?token={listenToken}&clientType=web
 *  3) Se manda un JSON de tipo "text" con el orderNo y el contenido. Binance responde con el
 *     mismo mensaje y un "id" propio: eso confirma que quedó guardado en el chat.
 *  4) Los mensajes de una orden se leen con GET /sapi/v1/c2c/chat/retrieveChatMessagesWithPagination.
 *
 * ENVÍO AUTOMÁTICO DE LA CUENTA: al pre-asignar una cuenta COP a una venta en curso (a mano o
 * por la asignación automática), se le envían al cliente los datos de esa cuenta. Se programa
 * con unos segundos de espera para que, si el operador corrige la cuenta enseguida, solo salga
 * la definitiva; y se marca en la pre-asignación para no repetirlo. Se puede apagar con el
 * interruptor (P2PChatConfig).
 */
@Slf4j
@Service
public class P2PChatService {

    /** Cuánto se escucha después de enviar, para capturar la confirmación/error de Binance. */
    private static final long ESCUCHA_TRAS_ENVIO_MS = 4_000L;
    /** En el envío automático basta con la confirmación inmediata. */
    private static final long ESCUCHA_AUTO_MS = 1_500L;
    private static final long ESCUCHA_MAX_MS = 60_000L;
    private static final Integer CONFIG_ID = 1;
    private static final java.time.ZoneId ZONA = java.time.ZoneId.of("America/Bogota");

    @Autowired private BinanceService binanceService;
    @Autowired private P2PPreAsignacionRepository preAsignacionRepository;
    @Autowired private P2PChatConfigRepository configRepository;
    /** @Lazy: P2PActiveOrderService también llama a este servicio al guardar una asignación. */
    @Autowired @Lazy private P2PActiveOrderService activeOrderService;

    /** Espera entre la asignación y el envío automático (por si el operador corrige la cuenta). */
    @Value("${p2p.chat.auto-envio-espera-ms:5000}")
    private long esperaAutoEnvioMs;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /**
     * Varios hilos: cada envío abre su propio WebSocket y tarda unos 5 s, así que con un solo hilo las
     * cuentas salían de a una y se atrasaban respecto a las asignaciones. Nunca dos a la vez para la
     * MISMA orden (ver {@link #cerrojos}).
     */
    private static final int HILOS_ENVIO = 3;
    private final ScheduledExecutorService programador = Executors.newScheduledThreadPool(HILOS_ENVIO,
            hilo("p2p-chat-envio"));
    /** Un cerrojo por orden: dos envíos de la misma orden (reasignada) nunca corren en paralelo. */
    private final Map<String, Object> cerrojos = new ConcurrentHashMap<>();
    /** Para leer en paralelo el chat de varias órdenes (resumen de mensajes nuevos). */
    private final ExecutorService lectores = Executors.newFixedThreadPool(4, hilo("p2p-chat-lector"));

    /** Envío programado por orden. Si se reasigna antes de que salga, se cancela y se reprograma. */
    private final Map<String, ScheduledFuture<?>> enviosProgramados = new ConcurrentHashMap<>();
    /** Último motivo por el que NO se pudo enviar la cuenta de una orden (se muestra en pantalla). */
    private final Map<String, String> erroresEnvio = new ConcurrentHashMap<>();

    private static ThreadFactory hilo(String nombre) {
        return r -> {
            Thread t = new Thread(r, nombre);
            t.setDaemon(true);
            return t;
        };
    }

    @PreDestroy
    void cerrar() {
        programador.shutdownNow();
        lectores.shutdownNow();
    }

    // ── Interruptor del envío automático ──────────────────────────

    public boolean isAutoEnvio() {
        return configRepository.findById(CONFIG_ID)
                .map(c -> !Boolean.FALSE.equals(c.getAutoEnvioCuenta()))
                .orElse(true);
    }

    public boolean setAutoEnvio(boolean activo) {
        P2PChatConfig cfg = configRepository.findById(CONFIG_ID).orElseGet(() -> new P2PChatConfig(CONFIG_ID, true));
        cfg.setAutoEnvioCuenta(activo);
        configRepository.save(cfg);
        log.info("[P2PChat] Envío automático de la cuenta COP {}", activo ? "ACTIVADO" : "DESACTIVADO");
        if (!activo) {
            enviosProgramados.values().forEach(f -> f.cancel(false));
            enviosProgramados.clear();
        }
        return activo;
    }

    // ── Mensaje con la cuenta ─────────────────────────────────────

    /**
     * Datos de la cuenta COP en el formato que pidió el cliente:
     * <pre>
     * Oscar Nova
     * BANCOLOMBIA
     * Tipo: Ahorros
     * Cuenta: 91202498502
     * Cédula: 13508415
     * </pre>
     */
    public static String mensajeCuenta(AccountCop c) {
        return vacioComoGuion(c.getName()) + "\n"
                + (c.getBankType() != null ? c.getBankType().name() : "-") + "\n"
                + "Tipo: " + ("CORRIENTE".equalsIgnoreCase(c.getTipoCuenta()) ? "Corriente" : "Ahorros") + "\n"
                + "Cuenta: " + vacioComoGuion(c.getNumeroCuenta()) + "\n"
                + "Cédula: " + vacioComoGuion(c.getCedula());
    }

    /** Mensaje con la cuenta COP pre-asignada a la orden (para el envío manual desde el panel). */
    @Transactional(readOnly = true)
    public String mensajeSugerido(String orderNumber) {
        P2PPreAsignacion pre = preAsignacionRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalStateException("La orden " + orderNumber + " no tiene cuenta COP asignada."));
        return mensajeCuenta(pre.getCuentaCop());
    }

    private static String vacioComoGuion(String s) {
        return (s == null || s.isBlank()) ? "-" : s.trim();
    }

    /** Qué datos le faltan a la cuenta para poder enviarla (vacío = está completa). */
    private static String datosFaltantes(AccountCop c) {
        List<String> faltan = new ArrayList<>();
        if (c.getName() == null || c.getName().isBlank()) faltan.add("nombre");
        if (c.getBankType() == null) faltan.add("banco");
        if (c.getNumeroCuenta() == null || c.getNumeroCuenta().isBlank()) faltan.add("número de cuenta");
        if (c.getCedula() == null || c.getCedula().isBlank()) faltan.add("cédula");
        return String.join(", ", faltan);
    }

    // ── Envío automático al asignar ───────────────────────────────

    /**
     * Programa el envío de la cuenta asignada a la orden. Lo llama
     * P2PActiveOrderService.upsertPreAsignacion (asignación manual y automática). Si ya había un
     * envío pendiente para la orden, se reemplaza: así solo sale la última cuenta elegida.
     */
    public void programarEnvioCuenta(String orderNumber) {
        if (orderNumber == null || !isAutoEnvio()) return;
        erroresEnvio.remove(orderNumber);
        ScheduledFuture<?> nuevo = programador.schedule(() -> {
            try {
                synchronized (cerrojos.computeIfAbsent(orderNumber, k -> new Object())) {
                    enviarCuentaSiCorresponde(orderNumber);
                }
            } catch (Exception e) {
                erroresEnvio.put(orderNumber, e.getMessage() != null ? e.getMessage() : e.toString());
                log.warn("[P2PChat] Falló el envío automático de la cuenta para {}: {}", orderNumber, e.getMessage());
            } finally {
                // Las pantallas actualizan al instante el estado del envío (el aviso sale ~250 ms después, ya terminada la tarea).
                P2PSseNotificador.chatEnvioActualizado();
            }
        }, esperaAutoEnvioMs, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> previo = enviosProgramados.put(orderNumber, nuevo);
        if (previo != null) previo.cancel(false);
    }

    /** Lee la asignación ACTUAL (puede haber cambiado durante la espera) y la envía si hace falta. */
    private void enviarCuentaSiCorresponde(String orderNumber) {
        if (!isAutoEnvio()) return;
        P2PPreAsignacion pre = preAsignacionRepository.findByOrderNumber(orderNumber).orElse(null);
        if (pre == null || pre.getCuentaCop() == null) return; // se quitó la asignación
        AccountCop cop = pre.getCuentaCop();
        if (Objects.equals(pre.getChatCopEnviadoId(), cop.getId())) return; // esa cuenta ya se envió

        // Solo mientras el cliente todavía no paga: si ya marcó pagado (o la orden terminó), la
        // asignación es para registrar a dónde llegó la plata, no para pedirle que consigne.
        String estado = activeOrderService.estadoConocido(orderNumber);
        if (estado != null && !"TRADING".equalsIgnoreCase(estado)) {
            log.info("[P2PChat] No se envía la cuenta a {}: la orden está en {}", orderNumber, estado);
            return;
        }

        String faltan = datosFaltantes(cop);
        if (!faltan.isEmpty()) {
            erroresEnvio.put(orderNumber, "La cuenta " + cop.getName() + " no tiene: " + faltan);
            log.warn("[P2PChat] No se envía la cuenta a {}: a {} le falta {}", orderNumber, cop.getName(), faltan);
            return;
        }

        // Si al cliente ya se le había mandado otra cuenta, se le aclara que cambió.
        String texto = (pre.getChatCopEnviadoId() != null ? "Cambio de cuenta, por favor consigna a esta:\n\n" : "")
                + mensajeCuenta(cop);
        Map<String, Object> res = enviar(pre.getAccountBinance(), orderNumber, texto, ESCUCHA_AUTO_MS);
        boolean enChat = Boolean.TRUE.equals(res.get("ok"))
                && confirmarEnChat(pre.getAccountBinance(), orderNumber, texto);
        if (Boolean.TRUE.equals(res.get("ok")) && !enChat) {
            // El WebSocket aceptó el texto pero el mensaje no aparece en el chat: se reintenta una vez.
            log.warn("[P2PChat] El mensaje de la cuenta no apareció en el chat de {}: se reintenta el envío.", orderNumber);
            res = enviar(pre.getAccountBinance(), orderNumber, texto, ESCUCHA_AUTO_MS);
            enChat = Boolean.TRUE.equals(res.get("ok"))
                    && confirmarEnChat(pre.getAccountBinance(), orderNumber, texto);
            if (!enChat && Boolean.TRUE.equals(res.get("ok"))) {
                res = new LinkedHashMap<>(res);
                res.put("ok", false);
                res.put("error", "Binance no mostró el mensaje en el chat. Envíala a mano desde el chat de la orden.");
            }
        }
        if (enChat) {
            preAsignacionRepository.marcarCuentaEnviada(orderNumber, cop.getId(), LocalDateTime.now(ZONA));
            erroresEnvio.remove(orderNumber);
            log.info("[P2PChat] Cuenta {} enviada por chat a la orden {}", cop.getName(), orderNumber);
        } else {
            erroresEnvio.put(orderNumber, String.valueOf(res.getOrDefault("error", "Binance no aceptó el mensaje")));
        }
    }

    /**
     * Comprueba, leyendo el chat de la orden, que el mensaje enviado quedó escrito. Antes bastaba con que el
     * WebSocket aceptara el texto, y la pantalla marcaba "Cuenta enviada" aunque Binance lo hubiera descartado.
     * Reintenta unos segundos (el chat tarda un instante en reflejarlo). Si NO se puede leer el chat, se da por
     * enviado (comportamiento anterior): no se reintenta a ciegas ni se duplica el mensaje.
     */
    private boolean confirmarEnChat(String accountBinance, String orderNumber, String texto) {
        long desde = System.currentTimeMillis() - 60_000L;
        for (int intento = 0; intento < 3; intento++) {
            try {
                if (aparecioEnChat(accountBinance, orderNumber, texto, desde)) return true;
            } catch (Exception e) {
                log.warn("[P2PChat] No se pudo leer el chat de {} para confirmar el envío ({}): se da por enviado.",
                        orderNumber, e.getMessage());
                return true;
            }
            try { Thread.sleep(1_000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }

    /** Una lectura del chat: ¿hay un mensaje MÍO, reciente, con este texto? (se ignoran espacios y saltos de línea). */
    public boolean aparecioEnChat(String accountBinance, String orderNumber, String texto, long desdeMs) throws Exception {
        JsonNode data = mapper.readTree(binanceService.obtenerMensajesChatCrudos(accountBinance, orderNumber, 1, 30))
                .path("data");
        String buscado = texto.replaceAll("\\s+", "");
        for (JsonNode n : data) {
            if (!n.path("self").asBoolean(false)) continue;
            if (n.path("createTime").asLong(0) < desdeMs) continue;
            if (n.path("content").asText("").replaceAll("\\s+", "").equals(buscado)) return true;
        }
        return false;
    }

    // ── Resumen de los chats de las ventas en curso ───────────────

    private static final long RESUMEN_TTL_MS = 6_000L;
    private static final int RESUMEN_FILAS = 30;
    private record ResumenCache(long ms, Map<String, Object> chat) {}
    private final Map<String, ResumenCache> cacheResumen = new ConcurrentHashMap<>();

    /**
     * Para cada orden: horas de los mensajes del CLIENTE (la pantalla cuenta los que no ha visto),
     * vista previa del último, y el estado del envío automático de la cuenta. El chat de cada
     * orden se lee de Binance a lo sumo cada pocos segundos aunque haya varias pantallas abiertas.
     */
    public List<Map<String, Object>> resumen(List<Map<String, String>> ordenes) {
        long ahora = System.currentTimeMillis();
        cacheResumen.entrySet().removeIf(e -> ahora - e.getValue().ms() > 600_000L);
        enviosProgramados.entrySet().removeIf(e -> e.getValue().isDone());

        List<String> numeros = ordenes.stream().map(o -> o.get("orderNumber")).filter(Objects::nonNull).toList();
        Map<String, P2PPreAsignacion> pres = new HashMap<>();
        if (!numeros.isEmpty()) {
            preAsignacionRepository.findByOrderNumberIn(numeros).forEach(p -> pres.put(p.getOrderNumber(), p));
        }

        List<CompletableFuture<Map<String, Object>>> tareas = new ArrayList<>();
        for (Map<String, String> o : ordenes) {
            String numero = o.get("orderNumber");
            String cuenta = o.get("accountBinance");
            if (numero == null || cuenta == null) continue;
            tareas.add(CompletableFuture.supplyAsync(() -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orderNumber", numero);
                m.putAll(chatResumido(cuenta, numero, ahora));
                P2PPreAsignacion pre = pres.get(numero);
                if (pre != null && pre.getChatCopEnviadoId() != null) {
                    m.put("cuentaEnviadaCopId", pre.getChatCopEnviadoId());
                    m.put("cuentaEnviadaHora", pre.getChatEnviadoAt() != null
                            ? pre.getChatEnviadoAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : null);
                }
                ScheduledFuture<?> f = enviosProgramados.get(numero);
                m.put("envioPendiente", f != null && !f.isDone());
                m.put("envioError", erroresEnvio.get(numero));
                return m;
            }, lectores));
        }
        return tareas.stream().map(CompletableFuture::join).toList();
    }

    private Map<String, Object> chatResumido(String cuenta, String numero, long ahora) {
        ResumenCache c = cacheResumen.get(numero);
        if (c != null && ahora - c.ms() < RESUMEN_TTL_MS) return c.chat();

        Map<String, Object> m = new LinkedHashMap<>();
        try {
            JsonNode data = mapper.readTree(binanceService.obtenerMensajesChatCrudos(cuenta, numero, 1, RESUMEN_FILAS))
                    .path("data");
            List<Long> tiempos = new ArrayList<>();
            JsonNode ultimo = null;
            for (JsonNode n : data) {
                if (n.path("self").asBoolean(false) || "system".equalsIgnoreCase(n.path("type").asText(""))) continue;
                long t = n.path("createTime").asLong(0);
                tiempos.add(t);
                if (ultimo == null || t > ultimo.path("createTime").asLong(0)) ultimo = n;
            }
            m.put("clienteTiempos", tiempos);
            if (ultimo != null) {
                boolean imagen = ultimo.hasNonNull("imageUrl") || ultimo.hasNonNull("thumbnailUrl")
                        || "image".equalsIgnoreCase(ultimo.path("type").asText(""));
                m.put("ultimoClienteTexto", imagen ? "[imagen]" : ultimo.path("content").asText(""));
                m.put("ultimoClienteImagen", imagen);
            }
        } catch (Exception e) {
            // Si Binance falla un momento, se conserva lo último conocido en vez de borrar el aviso.
            if (c != null) return c.chat();
            m.put("clienteTiempos", List.of());
            m.put("errorChat", e.getMessage());
        }
        cacheResumen.put(numero, new ResumenCache(ahora, m));
        return m;
    }

    // ── Órdenes recientes (para elegir a cuál escribir) ───────────

    private static final java.time.format.DateTimeFormatter FMT_HORA =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final int MAX_RECIENTES = 40;

    /**
     * Órdenes P2P de la cuenta en las últimas {@code horas} (en curso, completadas y canceladas),
     * más nuevas primero. El chat de una orden terminada sigue abierto un tiempo en Binance, así
     * que sirven para probar el envío aunque no haya ventas activas.
     */
    public List<Map<String, Object>> ordenesRecientes(String accountBinance, int horas) throws Exception {
        long fin = System.currentTimeMillis();
        long inicio = fin - Math.max(1, Math.min(72, horas)) * 3_600_000L;
        JsonNode root = mapper.readTree(binanceService.getP2POrdersInRange(accountBinance, inicio, fin, null));
        if (root.has("error")) throw new IllegalStateException(root.get("error").asText());

        List<JsonNode> filas = new ArrayList<>();
        root.path("data").forEach(filas::add);
        filas.sort(Comparator.comparingLong((JsonNode n) -> n.path("createTime").asLong()).reversed());

        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode n : filas.subList(0, Math.min(MAX_RECIENTES, filas.size()))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderNumber", n.path("orderNumber").asText());
            m.put("tradeType", n.path("tradeType").asText(null));
            m.put("status", n.path("orderStatus").asText(null));
            m.put("totalPrice", n.path("totalPrice").asDouble(0));
            m.put("fiat", n.path("fiat").asText(null));
            m.put("counterPartNickName", n.path("counterPartNickName").asText(null));
            long ct = n.path("createTime").asLong(0);
            m.put("createTime", ct);
            m.put("hora", ct > 0 ? FMT_HORA.format(java.time.Instant.ofEpochMilli(ct).atZone(ZONA)) : null);
            m.put("accountBinance", accountBinance);
            out.add(m);
        }
        return out;
    }

    // ── Conversación de una orden ─────────────────────────────────

    private static final java.time.format.DateTimeFormatter FMT_MSG =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

    /**
     * Mensajes del chat de la orden, del más viejo al más nuevo (como se lee un chat).
     * Binance pagina del más nuevo hacia atrás, así que con {@code rows} se piden los últimos N.
     * Si la respuesta no trae la lista esperada, se devuelve la cruda en "respuestaCruda".
     */
    public Map<String, Object> mensajes(String accountBinance, String orderNo, int rows) throws Exception {
        int filas = Math.max(1, Math.min(100, rows));
        String raw = binanceService.obtenerMensajesChatCrudos(accountBinance, orderNo.trim(), 1, filas);
        JsonNode root = mapper.readTree(raw);
        JsonNode data = root.path("data");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderNo", orderNo.trim());
        List<Map<String, Object>> lista = new ArrayList<>();
        out.put("mensajes", lista);
        if (!data.isArray()) {
            out.put("ok", false);
            out.put("respuestaCruda", raw);
            return out;
        }

        List<JsonNode> filasJson = new ArrayList<>();
        data.forEach(filasJson::add);
        filasJson.sort(Comparator.comparingLong(n -> n.path("createTime").asLong()));
        for (JsonNode n : filasJson) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.path("id").asText(null));
            m.put("type", n.path("type").asText(null));
            m.put("content", n.path("content").asText(null));
            m.put("imageUrl", n.path("imageUrl").asText(null));
            m.put("thumbnailUrl", n.path("thumbnailUrl").asText(null));
            m.put("self", n.path("self").asBoolean(false));
            m.put("fromNickName", n.path("fromNickName").asText(null));
            m.put("status", n.path("status").asText(null));
            long ct = n.path("createTime").asLong(0);
            m.put("createTime", ct);
            m.put("hora", ct > 0 ? FMT_MSG.format(java.time.Instant.ofEpochMilli(ct).atZone(ZONA)) : null);
            lista.add(m);
        }
        out.put("ok", true);
        out.put("total", root.path("total").asInt(lista.size()));
        return out;
    }

    // ── Envío / escucha ───────────────────────────────────────────

    /** Envía un mensaje de texto al chat de la orden y devuelve lo que respondió Binance. */
    public Map<String, Object> enviar(String accountBinance, String orderNumber, String texto) {
        return enviar(accountBinance, orderNumber, texto, ESCUCHA_TRAS_ENVIO_MS);
    }

    private Map<String, Object> enviar(String accountBinance, String orderNumber, String texto, long escucharMs) {
        if (orderNumber == null || orderNumber.isBlank()) throw new IllegalArgumentException("Falta el número de orden.");
        if (texto == null || texto.isBlank()) throw new IllegalArgumentException("El mensaje está vacío.");

        long ahora = System.currentTimeMillis();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "text");
        msg.put("uuid", String.valueOf(ahora));
        msg.put("orderNo", orderNumber.trim());
        msg.put("content", texto);
        msg.put("self", true);
        msg.put("clientType", "web");
        msg.put("createTime", ahora);
        msg.put("sendStatus", 0);

        Map<String, Object> out = sesion(accountBinance, msg, escucharMs);
        log.info("[P2PChat] Envío a orden {} ({}): ok={} recibidos={}",
                orderNumber, accountBinance, out.get("ok"), ((List<?>) out.getOrDefault("recibidos", List.of())).size());
        return out;
    }

    /**
     * Solo escucha el chat de la cuenta durante unos segundos (no envía nada). Sirve para ver el
     * formato exacto en que Binance entrega los mensajes: se escribe algo en una orden desde el
     * celular mientras corre, y aparece aquí tal cual.
     */
    public Map<String, Object> escuchar(String accountBinance, int segundos) {
        long ms = Math.max(1_000L, Math.min(ESCUCHA_MAX_MS, segundos * 1_000L));
        return sesion(accountBinance, null, ms);
    }

    /** Abre el WebSocket del chat, envía {@code mensaje} (si no es null), escucha y cierra. */
    private Map<String, Object> sesion(String accountBinance, Map<String, Object> mensaje, long escucharMs) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cuenta", accountBinance);
        List<String> recibidos = new CopyOnWriteArrayList<>();
        out.put("recibidos", recibidos);
        WebSocket ws = null;
        try {
            JsonNode data = mapper.readTree(binanceService.obtenerCredencialChatCruda(accountBinance)).path("data");
            String wssUrl = data.path("chatWssUrl").asText("");
            String listenKey = data.path("listenKey").asText("");
            String listenToken = data.path("listenToken").asText("");
            if (wssUrl.isBlank() || listenKey.isBlank() || listenToken.isBlank()) {
                throw new IllegalStateException("Binance no devolvió las credenciales del chat.");
            }
            URI uri = URI.create(wssUrl + "/" + listenKey
                    + "?token=" + URLEncoder.encode(listenToken, StandardCharsets.UTF_8) + "&clientType=web");

            ws = http.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(uri, new Oyente(recibidos))
                    .get(15, TimeUnit.SECONDS);

            if (mensaje != null) {
                String json = mapper.writeValueAsString(mensaje);
                ws.sendText(json, true).get(10, TimeUnit.SECONDS);
                out.put("enviado", json);
            }
            Thread.sleep(escucharMs);
            out.put("ok", true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            out.put("ok", false);
            out.put("error", "Interrumpido");
        } catch (Exception e) {
            Throwable causa = e.getCause() != null ? e.getCause() : e;
            out.put("ok", false);
            out.put("error", causa.getMessage() != null ? causa.getMessage() : causa.toString());
            log.warn("[P2PChat] Error en sesión de chat ({}): {}", accountBinance, out.get("error"));
        } finally {
            if (ws != null) {
                try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "fin").get(3, TimeUnit.SECONDS); }
                catch (Exception ignored) { ws.abort(); }
            }
        }
        return out;
    }

    /** Junta los mensajes de texto que manda Binance (pueden llegar partidos en varios frames). */
    private static final class Oyente implements WebSocket.Listener {
        private final List<String> destino;
        private final StringBuilder parcial = new StringBuilder();

        Oyente(List<String> destino) { this.destino = destino; }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            parcial.append(data);
            if (last) {
                destino.add(parcial.toString());
                parcial.setLength(0);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            destino.add("[cerrado por Binance: " + statusCode + " " + reason + "]");
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            destino.add("[error: " + error.getMessage() + "]");
        }
    }
}
