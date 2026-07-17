# PROMPT DE CONTINUIDAD — Proyecto Pochonance

> Copia y pega todo esto al inicio de un chat nuevo para que el asistente retome el proyecto con contexto completo.

---

## Contexto del proyecto

Soy Milton. Trabajo en **"Pochonance"**, un sistema de gestión financiera / P2P de cripto. Tiene dos repos, ambos son **carpetas conectadas** en esta sesión:

- **BACKEND** (`BACKEND-BINANCE-RESUMEN`): **Spring Boot 3.4.x + Java 17**, base de datos **MySQL** (en Railway, la BD se llama `railway`). Desplegado en **Railway**.
- **FRONTEND** (`FRONTED-POCHONANCE`): **Angular 18 + PrimeNG**, componentes standalone. Desplegado en **Vercel**.

El sistema maneja: cuentas COP (bancos colombianos), cuentas de cripto (Binance, Bybit), clientes, proveedores, cajas, ventas/compras P2P, traspasos entre cuentas, retiradores, gastos, y un balance general.

---

## Reglas de negocio importantes (NO romper)

- **REGLA MILES:** los saldos COP se guardan divididos entre 1000 (ej: 2.000.000 se guarda como 2000). El cripto se guarda en USDT crudo.
- **4x1000 (GMF):** el impuesto se difiere al día siguiente para Bancolombia (`movimiento.comisionAplicada=false`). En las fórmulas de total se aplica multiplicando por `0.996` (eso resta el 4x1000).
- **Convención de signo "debe/debemos":** en clientes/proveedores, saldo ≥ 0 = "Debemos" (nosotros debemos). Un **PRÉSTAMO a caja aumenta la deuda** (`+= monto`), NO la baja. (Antes se llamaba "PAGO a caja" y restaba; se cambió a préstamo.)
- **Cuenta COP bloqueada:** una cuenta marcada como bloqueada (roja) NO debe aparecer ni ser seleccionable en NINGUNA lista, formulario, movimiento, ventas-en-curso, retiradores, gastos, pagos ni P2P. Se logra usando la proyección liviana `getP2PView()` (que filtra bloqueadas) en vez de `getAll()`.

---

## Reglas para el asistente (MUY IMPORTANTE)

1. **NO ejecutar git** contra mis carpetas (corrompe el `.git`). Yo hago los commits.
2. El **sandbox de Linux NO puede**: compilar Java, llegar a APIs externas, ni conectarse a la BD de Railway. Solo sirve para scripts locales sencillos.
3. El **mount 9p cachea las lecturas por bash** → verificar siempre los archivos con la herramienta **Read**, no con `cat`.
4. **Flujo en Eclipse para recompilar:** Stop → Refresh (F5) → Project > Clean → Start. **F5 solo NO recompila** (causa recurrente de "build viejo").
5. Yo (Milton) a veces me frustro y digo groserías. Manténte **calmado, honesto y directo**, sin disculparte en exceso.
6. **Seguridad:** pegué mis credenciales reales de Bybit en texto plano en el chat anterior. NO usarlas. (Debería regenerar la API key de Bybit por seguridad.)

---

## Estado ACTUAL (lo último que resolvimos)

**Problema:** producción en Railway se crasheaba mucho. La gráfica de Metrics mostró que **era MEMORIA** (subía en dientes de sierra hasta ~4,5 GB de 5 GB y a las 9PM se cayó → OOM → Railway reinicia). El CPU estaba casi en 0.

**Causa raíz:** el recolector de basura estaba en **SerialGC** (lento, deja acumular memoria hasta el tope antes de limpiar).

**Solución aplicada (✅ FUNCIONÓ — la gráfica bajó):** en Railway → Variables → `JAVA_TOOL_OPTIONS`:
- `-XX:+UseSerialGC` → `-XX:+UseG1GC`
- `-XX:MaxRAMPercentage=55` → `-XX:MaxRAMPercentage=50`

**Nota:** si en el futuro la memoria vuelve a subir en rampa sin recuperarse nunca, sería una **fuga real** en el código y habría que sacar un heap dump para cazarla. Por ahora quedó estable.

---

## Trabajo COMPLETADO en la sesión anterior

**Backend:**
- Renombrado PAGO→PRÉSTAMO a caja (cliente y proveedor); el préstamo ahora SUMA a la deuda (`+= monto`). Archivos: `MovimientoServiceImplement`, `MovimientoService`, `MovimientoController`, `MovimientoVistaService`.
- Creado `HttpClientFactory.timed()` (timeouts connect 5s / read 20s) aplicado a todos los RestTemplate (Binance, TronScan, Solscan, Retirador, Telegram, Solana). Antes no tenían timeout.
- Removido el bloque de balance de futuros de Binance (`getGeneralBalanceInUSDT`).
- Nueva pestaña TRASPASOS: `TransaccionesRepository.findTraspasosPaginados`, endpoint `GET /transacciones/listado`, `TransaccionesDTO.fromEntity` null-safe. Bug corregido: JavaBeans `getAQuien()` daba null → se renombró a `origen`/`destino`.
- Integración **Bybit** (`BybitService`, `TraspasoBybitService`): lee balance (Unified+Funding) y detecta traspasos DESDE Bybit cruzando el txID on-chain contra los registros de retiro de Bybit. Fix clave: `SpotOrdersController` ahora guarda el hash on-chain con `dto.setTxId(txId)` (antes se descartaba). Confirmado funcionando ("Javier → Luis").
- Balance General "CUENTAS" unificado con "TOTAL COP DISPONIBLE": `(Σ saldos COP − comisión pendiente Bancolombia) × 0.996`.
- Feature **bloqueo de cuenta COP**: columna `bloqueada` en `AccountCop`, endpoint `PATCH /{id}/toggle-bloqueo`, `findAllP2PView` filtra bloqueadas.
- Fix carrera "Duplicate entry" en `P2PActiveOrderController.upsertPreAsignacion` (reintenta una vez → entra por UPDATE).
- Pool de conexiones 12 → 20 en `application-prod.properties`.
- Optimización Hibernate: `default_batch_fetch_size=16`, `jdbc.batch_size=20`, compresión GZIP.

**Frontend:**
- Rutas lazy (`loadComponent`), xlsx/file-saver cargados con `import()` dinámico, budgets en `angular.json`, `trackBy` en tablas.
- Refresh de saldo en tiempo real (SSE + poll cada 5s) en Cajas, Clientes, Proveedores, Cuentas.
- Sistema de diseño (`src/design-system.css`): tokens, `.ds-btn`, `.ds-card`, `.ds-form`. Se está aplicando por fases (empezando por formularios).
- Nueva pestaña Traspasos (card + tabla paginada).
- Feature bloqueo de cuenta: botón candado, estilo rojo `.cop-card--blocked`, dropdowns usan `cuentasSeleccionables` (filtra bloqueadas).
- Muchos selects cambiados de `getAll()` → `getP2PView()` para excluir bloqueadas.

---

## Tareas PENDIENTES

1. **Verificar estabilidad post-G1GC:** revisar la gráfica de Memory en Railway tras unas horas/días. Si sube en rampa sin recuperarse → cazar fuga (heap dump).
2. **Limpiar código Bybit:** quitar los logs `[DIAG]` de `TraspasoBybitService` y remover el fallback hardcodeado `esWalletTraspaso` ahora que la detección por txID funciona.
3. **Verificar el feature de bloqueo:** confirmar que los formularios que pasaron a `getP2PView()` (proyección liviana) no pierdan campos que sí usaban (ej: si un form usaba `numeroCuenta`/`cedula`, la proyección liviana los deja en blanco → revertir ese caso a `getAll()` + filtro en cliente).
4. **Considerar** subir el intervalo de los polls (5s) si se quiere bajar más la carga.
5. **Sistema de diseño:** seguir aplicándolo por fases (estandarizar botones con `.ds-btn`, pulir diálogos de crear/editar).
6. **Seguridad:** regenerar la API key de Bybit (se expuso en texto plano).
7. **Recordar:** después de cambios en backend, hacer Clean + deploy (no solo F5).

---

## Cómo continuar

Empieza preguntándome en qué quiero avanzar de la lista de pendientes, o dime el problema nuevo. Confirma que tienes acceso a las dos carpetas (BACKEND-BINANCE-RESUMEN y FRONTED-POCHONANCE) antes de tocar código.
