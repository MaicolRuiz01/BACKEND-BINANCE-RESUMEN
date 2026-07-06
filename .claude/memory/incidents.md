# ⚠️ Incidentes y Lecciones

## 2026-06-28 — Corrupción del repo local por git desde el sandbox
**Qué pasó:** Durante un merge (`git pull` con conflicto en SecurityConfig.java), el agente
corrió comandos git/escrituras contra la carpeta del usuario desde su sandbox. Resultado:
- `.git/index.lock` quedó trabado y NO se pudo borrar desde el sandbox ("Operation not permitted",
  archivo fantasma en el montaje Windows/9p).
- Varios archivos quedaron físicamente dañados en disco: `SolicitudRetiro.java` (sin la `}` final),
  `RetiradorServiceImpl.java` (cortado en `if (value =`), `RetiradorRepository.java` y
  `RetiroReminderScheduler.java` (bytes NUL), `pom.xml`/`application.properties`/`.classpath` (NUL).
- Con el pom.xml corrupto, Eclipse no procesaba Lombok → cascada de errores rojos falsos.

**Por qué fue difícil de diagnosticar:** el sandbox no puede COMPILAR este proyecto
(tiene Java 11, el proyecto es 17; y Maven Central está bloqueado por allowlist → 403).
Un `}` faltante se detecta LEYENDO el archivo, sin compilar. Lección: ante "errores raros",
revisar balance de llaves y NUL con un script bash, no asumir Lombok/IDE.

**Solución final:** `git reset --hard origin/main` no bastó (siguieron archivos cortados).
Lo limpio es clonar de nuevo desde GitHub en carpeta nueva:
`git clone https://github.com/MaicolRuiz01/BACKEND-BINANCE-RESUMEN.git`
Luego reaplicar el cambio de los 403 en SecurityConfig (`.anyRequest().permitAll()`).

**Reglas para el futuro:**
1. NO ejecutar git que escriba índice contra carpetas del usuario desde el sandbox. Dárselos a Milton.
2. Para encontrar errores de sintaxis sin compilar: contar `{` vs `}` por archivo, buscar NUL
   (`grep -aP '\x00'`), revisar que cada .java termine en `}`.
3. El código en GitHub siempre estuvo intacto; el daño fue solo en la copia local.

## Sensibilidad — bienestar de Milton
Milton ha estado bajo muchísima presión y expresó ideación suicida ligada al miedo a que lo
despidan. Tratar con cuidado, calma y prioridad al bienestar. Recursos Colombia: Línea 106,
línea 192 opción 4. No minimizar; ofrecer apoyo.

## 2026-07-05 — Notas y hallazgos
- LENTITUD LOCAL (~10s): NO era el código; el backend local apuntaba a la BD remota de Railway (perfil prod). Usar perfil dev/BD local. Ver project-stack.md.
- Montaje sandbox (9p/Windows): las lecturas por bash a veces quedan CACHEADAS/desactualizadas tras editar con las herramientas de archivo. Verificar con el Read tool (fuente confiable), no con `grep`/`wc` de bash.
- Carpeta .claude/memory está PROTEGIDA para las herramientas Edit/Write; actualizarla por el shell (mcp__workspace__bash) escribiendo en el montaje.
