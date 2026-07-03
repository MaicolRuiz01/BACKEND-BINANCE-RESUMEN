# Stack Técnico

## Backend (BACKEND-BINANCE-RESUMEN)
- Spring Boot 3.4.3 + Java 17. IDE: Eclipse STS (Spring Tools for Eclipse). Maven (mvnw).
- ddl-auto=update. Paquete raíz com.binance.web. Repositorios en com.binance.web.Repository.
- Lombok en la mayoría de entidades EXCEPTO AccountCop (getters/setters explícitos). lombok 1.18.38.
- Remoto: https://github.com/MaicolRuiz01/BACKEND-BINANCE-RESUMEN.git  (rama main; existe rama david).

## Frontend (FRONTED-POCHONANCE)
- Angular 17+ standalone + PrimeNG. ng serve, http://localhost:4200. Deploy Vercel.
- API URL en environment.ts. Token en localStorage clave poch_token.

## Auth / Security
- JWT 12h. Roles ADMIN/OPERARIO. SecurityConfig: OPTIONS permitAll, /auth/login, /actuator/**,
  /telegram/webhook publicos. Actualmente `.anyRequest().permitAll()` (relajado por pedido de Milton).
- Interceptor Angular: logout SOLO en 401, no en 403.

## Flujo Eclipse (importante)
- Cambios en .java editados FUERA de Eclipse no compilan solos: Stop → Refresh F5 → Project Clean → Start.
- Tras cambios en pom o merges: Maven → Update Project (Alt+F5) + Project Clean.
- El sandbox del agente NO puede compilar: Java 11 (proyecto es 17) y Maven Central bloqueado (403 allowlist).
