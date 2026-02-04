package com.binance.web.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.binance.web.Entity.SaleP2P;
import com.binance.web.model.AssignAccountDto;
import com.binance.web.model.SaleP2PDto;

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
	String getAllP2PFromBinance(String account, LocalDate from, LocalDate to);
	List<SaleP2PDto> getTodayNoAsignadas(String account);
	List<SaleP2PDto> getTodayNoAsignadasAllAccounts();
	String fixDuplicateAssignmentsAuto(Integer saleP2pId);

}
