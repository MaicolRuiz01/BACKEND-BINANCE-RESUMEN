package com.binance.web.SaleP2P;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.binance.web.Entity.SaleP2P;

public interface SaleP2PService {
	List<SaleP2PDto> findAllSaleP2P();
	List<SaleP2PDto> getLastSaleP2pToday(String account);
    SaleP2P findByIdSaleP2P(Integer id);
    void saveSaleP2P(SaleP2P saleP2P);
    void updateSaleP2P(Integer id, SaleP2P sale);
    void deleteSaleP2P(Integer id);
    List<SaleP2P> obtenerVentasEntreFechas(LocalDateTime inicio, LocalDateTime fin);
    String processAssignAccountCop(Integer saleId, List<AssignAccountDto> accounts);
    void saveUtilitydefinitive(List<SaleP2P> rangeSales, Double averageRate);
    List<SaleP2P> obtenerVentasPorFecha(LocalDate fecha);
    Double obtenerComisionesPorFecha(LocalDate fecha);

}
