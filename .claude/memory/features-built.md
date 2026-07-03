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
