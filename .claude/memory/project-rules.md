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
