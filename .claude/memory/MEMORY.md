# Memory Index — Binance/P2P App (Milton Polana)
*Última actualización: 2026-07-08*

## Archivos
- [project-stack.md] — Spring Boot 3.4.3 + Java 17 + Angular 17 + PrimeNG, Eclipse STS, JWT, CORS
- [project-rules.md] — REGLA MILES (/1000), entidades, endpoints, patrones JPA
- [features-built.md] — Auth JWT, Llaves Brebe, modal Cuentas P2P, retiros Telegram
- [work-style.md] — Perfil Milton, comunicación, flujo Eclipse
- [incidents.md] — ⚠️ LEER ANTES DE TOCAR GIT O ARCHIVOS DEL USUARIO

## Resumen
Proyecto full-stack de trading P2P con cuentas Binance (USDT), COP y VES.
Dos repos: BACKEND-BINANCE-RESUMEN (Spring Boot) y FRONTED-POCHONANCE (Angular 17 + PrimeNG).
Regla clave: valores COP/cupos se guardan en MILES (revisar antes de /1000).
Auth JWT 12h (ADMIN/OPERARIO). Seguridad RELAJADA a pedido de Milton: SecurityConfig usa
`.anyRequest().permitAll()` para evitar 403 (no le interesa la seguridad por ahora).

## ⚠️ Advertencia crítica
NO correr comandos git que escriban el índice (add/commit/merge/reset) contra las carpetas
del usuario desde el sandbox del agente: deja `.git/index.lock` trabado y corrompe archivos
(truncados + bytes NUL). Para git, dar los comandos a Milton para que los corra en su Git Bash.
