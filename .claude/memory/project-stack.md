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

## Perfiles y base de datos (IMPORTANTE para velocidad local)
- application.properties: spring.profiles.active=prod.
- application-prod.properties: MySQL REMOTO de Railway (host:17809/railway).
- application-dev.properties: MySQL LOCAL (localhost:3306/binance_dev).
- SÍNTOMA: en local todo tarda ~10s → el backend local está pegado a la BD remota (perfil prod), cada query viaja por internet.
  FIX: correr local con perfil dev. En Eclipse Run Config ▸ Arguments ▸ VM arguments: -Dspring.profiles.active=dev.
  NO cambiar application.properties a dev (se despliega a Railway y rompe prod). Requiere MySQL local con base binance_dev.

## Railway — costo y RAM (JVM)
- El costo de Railway lo domina la RAM (CPU/egress/volumen mínimos). RAM subía a ~8GB (heap sin tope real).
- JAVA_TOOL_OPTIONS tenía -XX:MaxRAMPercentage=55 = % de la RAM VISIBLE (sin límite de contenedor la base es enorme → no topa).
  FIX recomendado: tope ABSOLUTO -Xmx1g (+ -Xms256m, -XX:+UseSerialGC ya estaba bien). Vigilar y luego apretar.
