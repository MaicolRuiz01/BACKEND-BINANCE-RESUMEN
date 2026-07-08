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

## ── Sesión 2026-07-06 ──

## Borrados con reversa de saldos
- Cajas (Efectivo): DELETE /efectivo/{id} (EfectivoService.eliminarCaja). 409 si tiene FK (movimientos/gastos).
- Proveedores (Supplier): DELETE /supplier/{id} (deleteSupplier). 409 si tiene compras/pagos/movimientos.
- Movimientos: DELETE /movimiento/eliminar/{id} (MovimientoService.eliminarMovimiento). Revierte saldos EXACTO por tipo:
  · PAGO PROVEEDOR: +proveedor destino; origen = +cuentaCOP(monto*1.004) / +caja / -provOrigen / -cliente.
  · RETIRO CAJERO/CORRESPONSAL: +cuentaCOP (monto + 4x1000 solo si comisionAplicada), -caja(monto), restablece cupo del tipo si el retiro es de HOY (clamp al max).
  · TRANSFERENCIA CAJA: +origen, -destino.
  · Otros tipos: se rechazan (no descuadrar).
- Front: botones en la vista REAL de cajas saldos/tabs/cajas/cajas.component (tarjeta = eliminar caja; modal Movimientos = columna Acción con eliminar por fila, helper esEliminable). OJO: hay una cajas-tab gemela en asignaciones/tabs que NO es la que usa Milton.
- lista-pagos (proveedor): se arregló el confirm INVERTIDO (if(!confirm) borraba al cancelar) + Output eliminado para recargar saldos.

## Traspaso entre cajas (sin 4x1000)
- Movimiento.cajaDestino (nuevo). RegistrarTransferenciaCaja: resta origen, suma destino, tipo "TRANSFERENCIA CAJA", comision 0.
- listarMovimientosPorCaja usa findByCaja_IdOrCajaDestino_IdOrderByFechaDesc (aparece en ambas cajas). Endpoint POST /movimiento/transferencia-caja. Front: botón "Transferir entre cajas" en cajas-tab.

## Retiros: listado + eliminar
- BUG arreglado: listarRetiros usaba findByTipo("RETIRO") exacto; los retiros son "RETIRO CAJERO"/"RETIRO CORRESPONSAL" → ahora findByTipoStartingWith("RETIRO"). Antes la lista salía vacía.
- Eliminar retiro revierte saldos y cupo (ver arriba). Se ve en Movimientos → Retiros y en el modal de la caja.

## Perf: movimientos de caja en 1 query
- El endpoint /movimiento/caja/{id} traía cada Movimiento con TODAS sus @ManyToOne EAGER (cuentas COP con brebeKeys, etc.) → N+1 lentísimo contra BD remota. Ahora usa proyección JPQL findMovimientosCajaLite (1 sola query, solo nombres). Constructor liviano en MovimientoDTO.

## Fix dropdown "Proveedor Destino" (modal Pago a Proveedor)
- El select se reseteaba al hacer scroll porque el panel vivía dentro del p-dialog. Fix: appendTo="body" (+ [filter] y scrollHeight) en todos los dropdowns de ese modal (proveedor.component.html).

## 4x1000: total neto + diferido en Bancolombia
- Total de arriba en Cuentas COP: badge "Total COP disponible" = suma de saldos × 0.996 (getter totalCuentasNeto). Las tarjetas siguen mostrando el saldo real.
- Al RETIRAR: Nequi y Daviplata descuentan el 4x1000 al instante; BANCOLOMBIA lo DIFIERE al día siguiente (hoy solo baja el monto).
  · Movimiento.comisionAplicada (Bancolombia=false al crear, otros=true; null en viejos = "ya aplicado").
  · Comision4x1000Scheduler: cada hora (con catch-up) aplica el 4x1000 pendiente de retiros con fecha < hoy → cuenta puede quedar en NEGATIVO (saldo por cobrar).
  · Validación de saldo del retiro usa lo que sale HOY (Bancolombia = solo monto).

## ── Sesión 2026-07-08 ──

## Wallet Bybit = TRASPASO (buy/sell dollars) [IMPLEMENTADO]
- Config app.bybit.wallets (lista CSV en application.properties, arranca con TU4vEruvZwLLkSfV9bNw12EJTPvNr7Pvaa). Servicio util/TraspasoWalletService.esWalletTraspaso(address).
- BuyDollarsDto/SellDollarsDto: campo contraparteAddress; se setea en TronScanService.parseTRC20Incoming (from) y Outgoing (to).
- En import de compras/ventas: si la contraparte es Bybit → ajusta cripto igual (entra/sale) + crea Transaccion "TRASPASO-BYBIT-<txHash>" (un lado nuestra cuenta, el otro null) y NO registra BuyDollars/SellDollars. Idempotente por existsByTxId. Solo aplica a TRON (spot/binancePay no traen dirección).

## Ventas en curso: cupo pasa a SOLO AVISO + saldos verde/amarillo
- Se QUITÓ el bloqueo duro (copOptions ya no deshabilita, dropdownChanged ya no bloquea, se dejó de auto-abrir el modal cupoLleno). Ahora solo AVISA (avisarSiExcedeCupo) cuánto se pasó del cupo tras asignar.
- Cards muestran DOS saldos: VERDE = saldo real + órdenes marcadas RECIBIDO (solo visual, NO toca el saldo real); AMARILLO = órdenes pre-asignadas pendientes.
- 2 botones por orden ("Ya cayó"=RECIBIDO / "Pendiente"=PENDIENTE). Persisten: P2PPreAsignacion.estadoManual. ActiveP2POrderDto +estadoManual. Endpoint PUT /api/p2p/pre-asignacion/{orderNumber}/estado?estado=RECIBIDO|PENDIENTE. El real solo sube con el import al completar.

## Cuentas COP: aviso de cupo agotado + iconos verde/rojo
- Modal de Retiro (cuentas-tab): banner rojo si el cupo del medio está agotado + botón Confirmar deshabilitado; aviso amarillo si el monto supera el cupo.
- Cards de cuentas COP: iconos pi-credit-card (cajero) y pi-building (corresponsal), verdes si hay cupo, rojos si agotado (como en ventas en curso).

## Vista de movimientos de una cuenta COP (lista-ventas) reconstruida
- 7 pestañas con carga perezosa: Ventas P2P, Compras P2P, Retiros, Traspasos (COP↔COP), Entradas, Salidas, Ajustes.
- Nuevo backend: compras P2P por cuenta → CompraP2PCuentaDTO + BuyP2PRepository.findComprasP2PByAccountCop (join accountCopsDetails + binanceAccount.name) + endpoint GET /cuenta-cop/{id}/compras-p2p.
- Retiros/Traspasos/Entradas/Salidas salen de getVistaCuentaCop (1 llamada, split por tipo). Ventas usan binanceAccount.name (no nameAccountBinance). Botón Regresar vuelve a la vista de cuentas COP (tab=cuentas-cop → container index 0 + saldosComp.verCop()). Se quitó el "ID #".

## Saldo Total (vista saldos) = COP + VES→COP + cripto EXTERNA→COP
- saldos.component: saldoTotalCop = totalCuentasCop + totalCuentasVesCop + criptoExternoCop (balanceExterno × tasa). Se quitaron las criptos internas del total.
- FIX bug: getBalanceTotalExterno() llamaba por error al endpoint INTERNO; ahora usa getBalanceTotalExterno (/cuenta-binance/balance-total-externo).

## Balance: cuentas COP Bancolombia con doble 4x1000
- BalanceGeneralServiceImplement.saldoCop: Nequi/Daviplata tal cual; BANCOLOMBIA = (Σsaldo − 4x1000 pendiente) y a ese total se le resta su 4x1000. Query MovimientoRepository.sumComisionPendienteBancolombia (RETIRO*, comisionAplicada=false, bankType BANCOLOMBIA). Solo afecta el balance, no los saldos operativos.

## SSE de saldos COP en tiempo real
- SaldosSseController (/saldos-events/subscribe) + AccountCopSaldoListener (@EntityListeners en AccountCop): al cambiar cualquier saldo COP notifica DESPUÉS del commit (TransactionSynchronization.afterCommit). Frontend saldos-sse.service.ts (EventSource, reconecta); cuentas-tab y ventas-en-curso se suscriben (debounce 700ms) y refrescan con getSaldos() liviano (id+balance).
