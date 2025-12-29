package com.binance.web.serviceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.Cliente;
import com.binance.web.Entity.Supplier;
import com.binance.web.Entity.VentaVES;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.Repository.VentaVesRepository;
import com.binance.web.service.VentaVesService;

import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
@Transactional
public class VentaVesServiceImpl implements VentaVesService {

    private final VentaVesRepository repo;
    private final AccountCopRepository accountCopRepository;
    private final SupplierRepository supplierRepository;
    private final ClienteRepository clienteRepository;

    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    @Override
    public VentaVES create(VentaVES venta) {
        normalize(venta);
        validateAssignment(venta);

        VentaVES saved = repo.save(venta);
        applyBusinessEffect(saved);

        return saved;
    }

    @Override
    public VentaVES update(Long id, VentaVES venta) {
        VentaVES existing = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("VentaVES no encontrada: " + id));

        // revertir efecto anterior
        revertBusinessEffect(existing);

        // actualizar datos
        existing.setDate(venta.getDate() != null ? venta.getDate() : existing.getDate());
        existing.setBolivares(venta.getBolivares());
        existing.setTasa(venta.getTasa());
        existing.setCliente(venta.getCliente());
        existing.setProveedor(venta.getProveedor());
        existing.setCuentaCop(venta.getCuentaCop());

        normalize(existing);
        validateAssignment(existing);

        VentaVES saved = repo.save(existing);
        applyBusinessEffect(saved);

        return saved;
    }

    @Override
    public void delete(Long id) {
        VentaVES existing = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("VentaVES no encontrada: " + id));

        revertBusinessEffect(existing);
        repo.delete(existing);
    }

    // ======================
    // READ METHODS (FALTABAN)
    // ======================

    @Override
    @Transactional(readOnly = true)
    public VentaVES findById(Long id) {
        return repo.findById(id).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VentaVES> findAll() {
        return repo.findAll();
        // Si quieres ordenado por fecha DESC:
        // return repo.findAll(Sort.by(Sort.Direction.DESC, "date"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VentaVES> findByDay(LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();
        return repo.findByDateBetweenOrderByDateDesc(start, end);
    }

    // ======================
    // BUSINESS LOGIC
    // ======================

    private void applyBusinessEffect(VentaVES v) {
        double pesos = v.getPesos() != null ? v.getPesos() : 0.0;

        // 🏦 CUENTA COP: entra COP
        if (v.getCuentaCop() != null && v.getCuentaCop().getId() != null) {
            AccountCop acc = accountCopRepository.findById(v.getCuentaCop().getId())
                    .orElseThrow(() -> new RuntimeException("Cuenta COP no encontrada"));

            double current = acc.getBalance() != null ? acc.getBalance() : 0.0;
            acc.setBalance(current + pesos);
            accountCopRepository.save(acc);
            return;
        }

        // 👤 CLIENTE: se resta saldo (deuda)
        if (v.getCliente() != null && v.getCliente().getId() != null) {
            Cliente c = clienteRepository.findById(v.getCliente().getId())
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

            double saldo = c.getSaldo() != null ? c.getSaldo() : 0.0;
            c.setSaldo(saldo - pesos);
            clienteRepository.save(c);
            return;
        }

        // 🏭 PROVEEDOR: se resta deuda
        if (v.getProveedor() != null && v.getProveedor().getId() != null) {
            Supplier s = supplierRepository.findById(v.getProveedor().getId())
                    .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));

            double bal = s.getBalance() != null ? s.getBalance() : 0.0;
            s.setBalance(bal - pesos);
            supplierRepository.save(s);
        }
    }

    private void revertBusinessEffect(VentaVES v) {
        double pesos = v.getPesos() != null ? v.getPesos() : 0.0;

        // 🏦 revertir cuenta COP
        if (v.getCuentaCop() != null && v.getCuentaCop().getId() != null) {
            AccountCop acc = accountCopRepository.findById(v.getCuentaCop().getId())
                    .orElseThrow(() -> new RuntimeException("Cuenta COP no encontrada"));

            double current = acc.getBalance() != null ? acc.getBalance() : 0.0;
            acc.setBalance(current - pesos);
            accountCopRepository.save(acc);
            return;
        }

        // 👤 revertir cliente
        if (v.getCliente() != null && v.getCliente().getId() != null) {
            Cliente c = clienteRepository.findById(v.getCliente().getId())
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

            double saldo = c.getSaldo() != null ? c.getSaldo() : 0.0;
            c.setSaldo(saldo + pesos);
            clienteRepository.save(c);
            return;
        }

        // 🏭 revertir proveedor
        if (v.getProveedor() != null && v.getProveedor().getId() != null) {
            Supplier s = supplierRepository.findById(v.getProveedor().getId())
                    .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));

            double bal = s.getBalance() != null ? s.getBalance() : 0.0;
            s.setBalance(bal + pesos);
            supplierRepository.save(s);
        }
    }

    // ======================
    // VALIDATION + NORMALIZE
    // ======================

    private void validateAssignment(VentaVES v) {
        int count = 0;

        if (v.getCuentaCop() != null) count++;
        if (v.getCliente() != null) count++;
        if (v.getProveedor() != null) count++;

        if (count != 1) {
            throw new RuntimeException("La venta debe asignarse a UNA sola opción: Cuenta COP, Cliente o Proveedor");
        }
    }

    private void normalize(VentaVES v) {
        if (v.getDate() == null) {
            v.setDate(LocalDateTime.now(ZONE_BOGOTA));
        }

        double bol = v.getBolivares() != null ? v.getBolivares() : 0.0;
        double tasa = v.getTasa() != null ? v.getTasa() : 0.0;
        v.setPesos(bol * tasa);
    }
}