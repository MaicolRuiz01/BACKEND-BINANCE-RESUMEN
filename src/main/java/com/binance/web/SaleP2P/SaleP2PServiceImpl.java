package com.binance.web.SaleP2P;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.AccountBinance.AccountBinance;
import com.binance.web.AccountBinance.AccountBinanceRepository;
import com.binance.web.AccountCop.AccountCop;
import com.binance.web.AccountCop.AccountCopService;
import com.binance.web.Supplier.SupplierService;

@Service
public class SaleP2PServiceImpl implements SaleP2PService{

	@Autowired
	private SaleP2PRepository saleP2PRepository;
	
	@Autowired
	private AccountCopService accountCopService;
	
	@Autowired
	private SupplierService supplierService;
	
	@Autowired
	private AccountBinanceRepository accountBinanceRepository;

	@Override
	public List<SaleP2P> findAllSaleP2P() {
		List<SaleP2P> salesP2P = saleP2PRepository.findAll();
		return salesP2P;
	}
	
	@Override
	public SaleP2P findByIdSaleP2P(Integer id) {
		SaleP2P saleP2P = saleP2PRepository.findById(id).get();
		return saleP2P;
	}
	
	@Override
	public void saveSaleP2P(SaleP2P saleP2P) {
		saleP2PRepository.save(saleP2P);
	}
	
	@Override
	public void updateSaleP2P(Integer id, SaleP2P sale) {
		SaleP2P saleP2P = saleP2PRepository.findById(id).orElse(null);
		saleP2PRepository.save(saleP2P);
	}
	
	@Override
	public void deleteSaleP2P(Integer id) {
		saleP2PRepository.deleteById(id);
	}
	
	//este metodo hace el proceso de asignar una cuenta colombiana a una venta solo necesitamos enviarle una venta dto
	@Override
	public void processAssignAccountCop(SaleP2PDto saleDto) {
	    // Convertir el DTO de la venta a una entidad
	    SaleP2P sale = convertDtoToSaleP2p(saleDto);

	    // Asignar las cuentas COP si los IDs de las cuentas están presentes
	    if (saleDto.getAccountCopIds() != null && !saleDto.getAccountCopIds().isEmpty()) {
	        sale = assignAccountCop(saleDto.getAccountCopIds(), sale);  // Cambié accountCopId a accountCopIds
	    }

	    // Asignar la cuenta Binance como antes
	    sale = assignAccountBiannce(sale, saleDto.getNameAccountBinance());

	    // Restar la deuda del proveedor
	    supplierService.subtractSupplierDebt(saleDto.getPesosCop(), saleDto.getTaxType(), saleDto.getDate());

	    // Guardar la venta actualizada
	    saveSaleP2P(sale);
	}

	
	/*
	 * para que funcione hay que enviarle el id de la cuenta colombiana y la venta,
	 * el metodo encuentra la entidad por el nombre que le pase
	 * este metodo no es necesario llamarlo porque este lo usa otro metodo llamado processAssignAccountCop
	 */
	
	// Método modificado para asignar múltiples cuentas COP
	private SaleP2P assignAccountCop(List<Integer> accountCopIds, SaleP2P sale) {
	    // Lista de cuentas COP asociadas a la venta
	    List<AccountCop> accountCops = new ArrayList<>();

	    // Buscamos las cuentas COP por sus IDs y las agregamos a la lista
	    for (Integer accountCopId : accountCopIds) {
	        AccountCop accountCop = accountCopService.findByIdAccountCop(accountCopId);
	        if (accountCop != null) {
	            accountCops.add(accountCop);
	        }
	    }

	    // Asignamos la lista de cuentas al objeto SaleP2P
	    sale.setAccountCops(accountCops);

	    // Como tu modelo ya contiene la lista de cuentas, puedes actualizar el nombre
	    // de la cuenta en la venta si es necesario (dependiendo de la lógica)
	    if (!accountCops.isEmpty()) {
	        sale.setNameAccount(accountCops.get(0).getName());  // Esto es un ejemplo, ajusta según necesites
	    }

	    return sale;
	}

	
	//para que funcione hay que enviarle una venta y un nombre, el metodo encuentra la entidad por el nombre que le pase
	private SaleP2P assignAccountBiannce(SaleP2P sale, String name) {
		AccountBinance accountBinance = accountBinanceRepository.findByName(name);
		sale.setBinanceAccount(accountBinance);
		return sale;
	}
	
	//metodo que recibe un dto de sale y lo convierte a una entidad
	private SaleP2P convertDtoToSaleP2p(SaleP2PDto saleDto) {
		SaleP2P sale = new SaleP2P();
		sale.setNumberOrder(saleDto.getNumberOrder());
		sale.setDate(saleDto.getDate());
		sale.setTaxType(saleDto.getTaxType());
		sale.setPesosCop(saleDto.getPesosCop());
		sale.setNameAccount(saleDto.getNameAccount());
		return sale;
	}
}
