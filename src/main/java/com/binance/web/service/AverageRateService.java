package com.binance.web.service;

import java.time.LocalDateTime;

import com.binance.web.Entity.AverageRate;

public interface AverageRateService {
	AverageRate getUltimaTasaPromedio(); // puede ser null
    AverageRate guardarNuevaTasa(Double nuevaTasa, Double nuevoSaldo,  LocalDateTime fecha);
	AverageRate getTasaPorDia(LocalDateTime fecha);
	/**
	 * @param buyDollarsId id de la compra (BuyDollars) que se está asignando ahora — se usa para
	 *        excluirla (y excluir cualquier otra compra aún sin asignar) del saldoInicial de la
	 *        sesión, evitando que ese USDT se cuente dos veces.
	 * @param esUltimaSinAsignar true si, tras esta asignación, ya no quedan compras (dollars)
	 *        sin asignar → la sesión se cierra y su tasa pasa a ser la base de la próxima.
	 */
	AverageRate actualizarTasaPromedioPorCompra(Integer buyDollarsId, LocalDateTime fechaCompra, Double montoUsdtCompra, Double tasaCompra, boolean esUltimaSinAsignar);
	AverageRate inicializarTasaPromedioInicial(Double tasaInicial, LocalDateTime fecha);

}
