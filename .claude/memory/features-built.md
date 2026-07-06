# Features Implementadas

## Autenticación JWT (full stack)
- Backend: JwtService, JwtAuthFilter, UserDetailsServiceImpl, AuthController, DataInitializer.
  SecurityConfig con CORS + JWT filter + OPTIONS permitAll. JJWT en pom.
- Frontend: AuthService, authInterceptor (solo logout en 401, NO en 403), AuthGuard, LoginComponent.
- NOTA: por pedido de Milton, SecurityConfig quedó con `.anyRequest().permitAll()` (seguridad relajada,
  para que no salgan 403 al crear cuentas COP, etc.). Endpoint publico extra: `/telegram/webhook`.

## Llaves Brebe (cuentas COP)
- Backend: entidad BrebeKey (tabla brebe_key), BrebeKeyRepository, AccountCop con @OneToMany EAGER brebeKeys.
  AccountCopController: POST/DELETE /cuenta-cop/{id}/brebe-keys.
- Frontend (cuentas-tab): interfaz BrebeKey, addBrebeKey/deleteBrebeKey, dialog de llaves.

## Modal "Cuentas COP en P2P"  (FRONTED-POCHONANCE: p2p/p2p-wrapper.component)
Construido esta sesión (2026-06-28):
- Muestra saldo junto al nombre: `{{ c.balance | currency:'COP':'$':'1.0-0' }}` (mismo formato que Saldos>Cuentas, sin /1000).
- Filtro por tipo de cupo (Todos/Cajero/Corresponsal): filtra cuentas con cupo disponible de ese tipo.
- Filtro por banco (Nequi/Daviplata/Bancolombia) lista FIJA siempre visible (no derivada de las cuentas existentes). multi-seleccion.
- Orden de mayor a menor saldo.
- Badge verde "Retirar": aparece si el saldo cubre cajero+corresponsal Y ambos cupos estan al maximo del dia
  (sin retirar aun). Maximos por banco (en miles): NEQUI 2700/5000, BANCOLOMBIA 2700/10000, DAVIPLATA 3000/5000.
- getter `cuentasFiltradas`, helpers `puedeRetirar`, `toggleBanco`, `bancoActivo`.

## Quitar cuenta de P2P desde la card (ventas-en-curso.component)
- Boton rojo ✕ (`cop-mini-remove`) en cada `cop-mini-card`; llama `desactivarP2P()` → toggleActivaParaP2P.
- Señal compartida `p2pCambio$` en AccountCopService (Subject) + `notificarCambioP2P()`:
  el modal (p2p-wrapper) y la tira (ventas-en-curso) se suscriben y recargan, para que NO se desincronicen
  (antes: quitabas la card pero el modal seguia mostrando la cuenta activa).

## Retiros por Telegram (de la rama del compañero, mergeada en origin/main ce5e176)
- SolicitudRetiro, DetalleRetiro, EstadoSolicitud (SIN_ASIGNAR/PENDIENTE/COMPLETADO), TipoRetiro,
  RetiradorServiceImpl, TelegramWebhookService, RetiroReminderScheduler, /telegram/webhook.
- Retirador tiene campo telegramUsername (col telegram_username). app.telegram.* en application.properties.

## Display saldos cripto en miles
- saldos.component.html: saldoExterno, totalUsdtGeneral, totalCopGeneral → /1000.

## Backend fix 403 (esta sesión)
- Tras implementar llaves Brebe + seguridad, crear cuentas COP daba 403 (JWT rechazado, interceptor no
  hace logout en 403). Fix pragmatico pedido por Milton: SecurityConfig `.anyRequest().permitAll()`.

## ── Sesión 2026-07-05 ──

## "Monto verdadero" en asignación de compras y ventas
- Problema: a veces compra 30.000 USDT pero por comisión de red llegan 29.998; los pesos con la tasa no cuadran.
- BuyDollarsDto/SellDollarsDto: campo `montoVerdadero` (misma escala MILES que amount/dollars).
- asignarCompra: si montoVerdadero>0 → existing.setAmount(montoVerdadero) antes de pesos.
- asignarVenta: si montoVerdadero>0 → existing.setDollars(montoVerdadero). NO toca saldo cripto (queda el real recibido).
- Front (asignaciones-compras / asignaciones-ventas): checkbox "Usar monto verdadero" + input prellenado con el monto (escala miles, ej "30"). montoEfectivo alimenta pesos y se envía al backend.

## Gastos — anti-duplicado + eliminar con reversión + velocidad
- Doble-registro (clics repetidos sin feedback): (a) front bloquea botón mientras el POST va (guardando); (b) backend Gasto.idempotencyKey @Column(unique) + findByIdempotencyKey; saveGasto devuelve el existente si la key ya está (sin re-restar). Front genera UUID por modal (openNew).
- Eliminar gasto: GastoService.eliminarGasto + AccountCopRepository.sumarSaldo (atómico). Devuelve EXACTO lo restado: cuenta COP = monto*1.004 (con comisión 0.4%), caja = monto. Controller DELETE usa el service. Front: botón basura en la tabla + modal de confirmación.
- Velocidad: crear NO re-consulta la lista (usa la respuesta del POST y arma la fila local); eliminar es OPTIMISTA (quita la fila ya, revierte si el backend falla). Se quitó el getAll() pesado de cuentas tras cada operación; se ajusta el saldo local.

## Ventas en curso — cupos
- REGLA: NO se puede asignar a una orden una cuenta COP cuyo cupo del día ya está lleno.
  cupoMaxDeCuenta según cupoTipoP2P (CAJERO/CORRESPONSAL/AMBOS) y banco; lleno = balance + pesos en curso pre-asignados >= max.
  Dropdown deshabilita esas cuentas ("— cupo lleno") y dropdownChanged las bloquea (cubre botón "=").
- Modal AUTOMÁTICO al llenarse el cupo (antes solo salía al click de un badge en p2p-wrapper): aparece solo al asignar la que llena o en refrescos; opciones "Cambiar por otra" (libera) / "Desactivar". Set cupoLlenoAvisado evita repetir.

## Vista Operadores (solo ADMIN) — tiempo de sesión e ingresos
- Backend: entidad SesionOperador (username, rol, loginAt, lastSeenAt, logoutAt) + SesionOperadorRepository.
  AuthResponse +sessionId; AuthController.login crea la sesión. SesionController: POST /auth/sesion/heartbeat, /logout, GET /auth/sesion/resumen?fecha=YYYY-MM-DD (@PreAuthorize ADMIN; agrupa por operador: ingresos + tiempoTotalSegundos + sesionAbierta).
  Duración = (logoutAt | lastSeenAt) - loginAt. "En línea" = sin logout y lastSeenAt < 3 min.
- Front: AuthService guarda sessionId (poch_sid) y hace heartbeat cada 60s (reanuda tras refresh); logout avisa al backend. sesion.service.ts (getResumen), admin.guard.ts, pages/operadores/*, ruta /operadores (adminGuard) e ítem de menú OPERADORES solo-admin.
- Tabla sesion_operador la crea Hibernate (ddl-auto=update).
- NOTA: se decidió heartbeat (no beforeunload/sendBeacon, porque disparaba en refresh y cerraba la sesión antes de tiempo).
