package com.binance.web.transacciones;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.BinanceAPI.PaymentController;
import com.binance.web.BinanceAPI.SpotOrdersController;
import com.binance.web.BinanceAPI.TronScanController;
import com.binance.web.BinanceAPI.TronScanService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.Transacciones;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SellDollarsRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class TransaccionesServiceImpl implements TransaccionesService {
	
	private final TransaccionesRepository transaccionesRepository;
    private final AccountBinanceRepository accountBinanceRepository;
    private final TronScanService tronScanService;
    private final TronScanController            tronController;
    private final SpotOrdersController          spotController;
    private final PaymentController             payController;

    private final TransaccionesRepository     transRepo;


   

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
    
    @Override
    public List<TransaccionesDTO> saveAndFetchTodayTraspasos() {
        LocalDate today = LocalDate.now();
        LocalDateTime inicio = today.atStartOfDay();
        LocalDateTime fin    = today.atTime(LocalTime.MAX);

        Set<String> existingIds = transRepo.findAll()
            .stream()
            .map(Transacciones::getIdtransaccion)
            .collect(Collectors.toSet());

        List<TransaccionesDTO> all = new ArrayList<>();

        ResponseEntity<List<TransaccionesDTO>> respTron = tronController.getTrustOutgoingTransfers();
        ResponseEntity<List<TransaccionesDTO>> respSpot = null; // Declara antes del try
        try {
            respSpot = spotController.getTraspasosNoRegistrados(100);
            if (respSpot.hasBody()) all.addAll(respSpot.getBody());
        } catch (Exception e) {
            e.printStackTrace();
        }

        ResponseEntity<List<TransaccionesDTO>> respPay = payController.getTransaccionesNoRegistradas();

        if (respTron.hasBody()) all.addAll(respTron.getBody());
        if (respSpot != null && respSpot.hasBody()) all.addAll(respSpot.getBody());
        if (respPay.hasBody()) all.addAll(respPay.getBody());

        for (TransaccionesDTO dto : all) {
            if (dto.getFecha().toLocalDate().equals(today)
             && !existingIds.contains(dto.getIdtransaccion())) {
                try {
                    guardarTransaccion(dto);
                    existingIds.add(dto.getIdtransaccion());
                } catch (Exception ignored) {}
            }
        }

        return transRepo.findByFechaBetween(inicio, fin)
                        .stream()
                        .map(TransaccionesDTO::fromEntity)
                        .collect(Collectors.toList());
    }

    
    


}
