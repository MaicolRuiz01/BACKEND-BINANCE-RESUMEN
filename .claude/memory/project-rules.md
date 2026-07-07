# Reglas de Negocio

## REGLA #1 — valores en "miles"
Valores monetarios COP, cupos y saldos USDT/cripto se guardan en MILES en la BD.
Cupos por banco (CupoDiarioRules, en miles): NEQUI cajero 2700/corr 5000, BANCOLOMBIA 2700/10000,
DAVIPLATA 3000/5000. OJO: `balance` de AccountCop se MUESTRA tal cual con currency (sin /1000) en
cuentas-tab y en el modal P2P — coincide con el mockup del cliente.

## Entidades clave
- AccountCop (cuentas COP) — bankType (NEQUI/DAVIPLATA/BANCOLOMBIA), balance, cupoCajeroDisponibleHoy,
  cupoCorresponsalDisponibleHoy, cupoTipoP2P (CAJERO/CORRESPONSAL/AMBOS), activaParaP2P,
  brebeKeys (@OneToMany EAGER). BrebeKey.accountCop tiene @JsonIgnore.
- SolicitudRetiro / DetalleRetiro / Retirador (con telegramUsername) — feature retiros Telegram.

## Endpoints clave
- GET/POST /cuenta-cop ; PATCH /cuenta-cop/{id}/toggle-p2p ; PATCH /cuenta-cop/{id}/cupo-tipo
- POST/DELETE /cuenta-cop/{id}/brebe-keys ; POST /auth/login (publico) ; /telegram/webhook (publico)

## Gastos (comisión y reversión)
- Al crear gasto con cuenta COP: se resta monto*1.004 (comisión 0.4%). Con caja: se resta monto (sin comisión).
- Al ELIMINAR: se devuelve EXACTAMENTE eso (monto*1.004 a cuenta COP / monto a caja). Reversión atómica (sumarSaldo).
- idempotencyKey (unique) por modal evita duplicados por clics repetidos.

## Cupos en Ventas en curso
- No asignar cuenta con cupo del día lleno (balance + pre-asignado en curso >= max según cupoTipoP2P y banco).
- Cupos máx (miles): NEQUI 2700/5000, BANCOLOMBIA 2700/10000, DAVIPLATA 3000/5000.

## Monto verdadero (buy/sell)
- Campo opcional al asignar; si viene, reemplaza amount/dollars para el cálculo de pesos. NO ajusta saldo cripto.

## 4x1000 en retiros (regla del cliente)
- Nequi y Daviplata: 4x1000 se descuenta AL INSTANTE al retirar (monto + 0.4%).
- BANCOLOMBIA: hoy solo baja el monto; el 4x1000 se descuenta AL DÍA SIGUIENTE (scheduler). La cuenta puede quedar en negativo (saldo por cobrar).
- Total de arriba (Cuentas COP) = suma de saldos − 0.4% (neto para sacarlo). Las tarjetas muestran saldo real.

## PENDIENTE — Wallet Bybit = TRASPASO (buy/sell dollars)
- Si un buy o sell dollar viene de/va a la wallet Bybit TU4vEruvZwLLkSfV9bNw12EJTPvNr7Pvaa (o cualquiera de Bybit), NO es compra/venta: es un TRASPASO. Esa wallet NO es una de nuestras cuentas cripto (no se trae info de ella), solo se detecta que un movimiento está relacionado con esa dirección → traspaso.
- Factibilidad: la dirección de contraparte está en TronScan (toAddress / ownerAddress|fromAddress) pero HOY no se guarda en BuyDollarsDto/SellDollarsDto. Habría que capturarla en TronScanService (parseIncoming/Outgoing), agregar campo al DTO y en el import tratar esas tx como traspaso. FALTA definir: ¿1 wallet o lista? ¿dónde guardarla (config/BD)? ¿solo excluir o registrar Transaccion/traspaso?
