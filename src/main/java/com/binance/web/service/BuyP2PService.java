package com.binance.web.service;

import java.util.List;

import com.binance.web.model.AssignAccountDto;
import com.binance.web.model.BuyP2PDto;

public interface BuyP2PService {
	List<BuyP2PDto> getTodayNoAsignadas(String account);
    List<BuyP2PDto> getTodayNoAsignadasAllAccounts();

    /**
     * TODAS las compras P2P pendientes, sin filtrar por fecha.
     *
     * Las dos de arriba solo miran el día de hoy. El balance, en cambio, cuenta todo lo
     * pendiente desde siempre, así que una compra de ayer sin asignar seguía restando en la card
     * "Asignar" pero no aparecía en ninguna pantalla: no había forma de asignarla.
     */
    List<BuyP2PDto> getNoAsignadasTodas();

    String processAssignAccountCop(Integer buyId, List<AssignAccountDto> accounts);

}
