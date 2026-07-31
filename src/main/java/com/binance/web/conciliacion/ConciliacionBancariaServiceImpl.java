package com.binance.web.conciliacion;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BankType;
import com.binance.web.Repository.AccountCopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ConciliacionBancariaServiceImpl implements ConciliacionBancariaService {

    private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}");

    private final AccountCopRepository accountCopRepository;

    public ConciliacionBancariaServiceImpl(AccountCopRepository accountCopRepository) {
        this.accountCopRepository = accountCopRepository;
    }

    /** Minúsculas, sin tildes, espacios colapsados — mismo criterio que usa el
     *  bot (_normalizar_nombre en conciliacion_bancaria.py) para que "Víctor"
     *  empareje con "Victor" sin importar el acento. */
    private static String normalizar(String s) {
        if (s == null) return "";
        String sinTildes = DIACRITICOS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        return sinTildes.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    @Override
    @Transactional
    public ConciliacionResponseDto procesarResultado(ConciliacionResultadoDto request) {
        ConciliacionResponseDto response = new ConciliacionResponseDto();
        if (request == null || request.getResultados() == null || request.getResultados().isEmpty()) {
            return response;
        }

        List<AccountCop> cuentasBancolombia = accountCopRepository.findByBankType(BankType.BANCOLOMBIA);

        // Agrupa por nombre normalizado — si dos cuentas normalizan igual, el
        // nombre queda AMBIGUO y se reporta como "no encontrado" en vez de
        // arriesgarse a actualizar la cuenta equivocada.
        Map<String, List<AccountCop>> porNombre = new HashMap<>();
        for (AccountCop c : cuentasBancolombia) {
            porNombre.computeIfAbsent(normalizar(c.getName()), k -> new ArrayList<>()).add(c);
        }

        for (ConciliacionResultadoDto.Item item : request.getResultados()) {
            String nombreOriginal = item.getCuenta() != null ? item.getCuenta() : "(sin nombre)";
            List<AccountCop> candidatos = porNombre.get(normalizar(nombreOriginal));

            if (candidatos == null || candidatos.size() != 1) {
                response.getNoEncontrados().add(nombreOriginal);
                continue;
            }

            AccountCop cuenta = candidatos.get(0);
            boolean disponible = Boolean.TRUE.equals(item.getDisponible());

            cuenta.setUltimaConciliacion(LocalDateTime.now());
            cuenta.setDisponibleBanco(item.getDisponible());

            if (disponible) {
                cuenta.setUltimoErrorConciliacion(null);
                Double saldoReal = item.getSaldoRealBanco();
                Double saldoPochonance = cuenta.getBalance();
                cuenta.setUltimoDesfaseBanco(
                        (saldoReal != null && saldoPochonance != null)
                                ? Math.round((saldoReal - saldoPochonance) * 100.0) / 100.0
                                : null);
            } else {
                cuenta.setUltimoDesfaseBanco(null);
                cuenta.setUltimoErrorConciliacion(item.getError());
            }

            accountCopRepository.save(cuenta);
            response.getActualizados().add(nombreOriginal);
        }

        return response;
    }
}
