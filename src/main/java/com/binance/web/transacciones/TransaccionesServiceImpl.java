package com.binance.web.transacciones;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.Transacciones;
import com.binance.web.Repository.AccountBinanceRepository;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class TransaccionesServiceImpl implements TransaccionesService {
	
	private final TransaccionesRepository transaccionesRepository;
    private final AccountBinanceRepository accountBinanceRepository;

    public TransaccionesServiceImpl(TransaccionesRepository transaccionesRepository,
            AccountBinanceRepository accountBinanceRepository) {
    	this.transaccionesRepository = transaccionesRepository;
    	this.accountBinanceRepository = accountBinanceRepository;
    }

    @Override
    public Transacciones guardarTransaccion(TransaccionesDTO dto) throws IllegalArgumentException {
        AccountBinance cuentaTo;
        AccountBinance cuentaFrom = accountBinanceRepository.findByName(dto.getCuentaFrom());

        if (dto.getTipo().equalsIgnoreCase("BINANCEPAY")) {
            cuentaTo = accountBinanceRepository.findByUserBinance(dto.getCuentaTo());
        } else {
            Optional<AccountBinance> cuentaToOpt = accountBinanceRepository.findAll().stream()
                .filter(a -> a.getAddress() != null && a.getAddress().equals(dto.getCuentaTo()))
                .findFirst();
            cuentaTo = cuentaToOpt.orElse(null);
        }

        if (cuentaTo == null || cuentaFrom == null) {
            throw new IllegalArgumentException("CuentaTo o CuentaFrom no encontrada");
        }

        Transacciones transaccion = new Transacciones();
        transaccion.setCantidad(dto.getMonto());
        transaccion.setIdtransaccion(dto.getIdtransaccion());
        transaccion.setFecha(dto.getFecha());
        transaccion.setTipo(dto.getTipo());
        transaccion.setCuentaTo(cuentaTo);
        transaccion.setCuentaFrom(cuentaFrom);

        // Ajustar balances
        Double montoTo = cuentaTo.getBalance();
        Double montoFrom = cuentaFrom.getBalance();

        cuentaTo.setBalance(montoTo + dto.getMonto());
        cuentaFrom.setBalance(montoFrom - dto.getMonto());

        accountBinanceRepository.save(cuentaTo);
        accountBinanceRepository.save(cuentaFrom);

        return transaccionesRepository.save(transaccion);
    }


}
