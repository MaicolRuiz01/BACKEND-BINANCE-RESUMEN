package com.binance.web;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * DESACTIVADA a propósito — no la reactives sin leer esto.
 *
 * @SpringBootTest levanta la aplicación COMPLETA. Y como el perfil por defecto es "prod"
 * (spring.profiles.active=prod en application.properties), la app se conectaba a la base de
 * datos REAL de Railway. Peor todavía: con @EnableScheduling activo, arrancaban todos los
 * procesos automáticos contra datos de producción.
 *
 * Se comprobó en la salida de una corrida de pruebas:
 *     [ActiveOrders][Nora] Binance devolvió 84 orden(es) en la ventana de 4h
 *     [ActivePoll] 4 orden(es) cambiaron de estado
 *
 * O sea que correr "mvnw test" podía importar ventas, MODIFICAR SALDOS de cuentas COP, mandar
 * mensajes al grupo de Telegram, pausar jornadas de operadores y ejecutar ddl-auto=update
 * sobre producción. También explica el "Surefire is going to kill self fork JVM": la app
 * quedaba viva con sus temporizadores y Maven tenía que matarla.
 *
 * Lo que verificaba esta prueba —que el contexto de Spring carga— no justifica ese riesgo.
 *
 * PARA REACTIVARLA hay que aislar primero el entorno de pruebas:
 *   1. Crear src/test/resources/application.properties con una base en memoria (H2).
 *   2. Desactivar ahí los procesos automáticos.
 *   3. Recién entonces quitar el @Disabled.
 */
@Disabled("Levantaba la app real contra la base de PRODUCCIÓN y disparaba los schedulers. "
        + "Reactivar solo tras aislar el entorno de pruebas (ver comentario de arriba).")
@SpringBootTest
class BinancebackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
