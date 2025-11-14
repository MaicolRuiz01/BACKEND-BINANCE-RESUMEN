package com.binance.web.movimientos;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.Movimiento;
import com.binance.web.Repository.MovimientoRepository;
import com.binance.web.model.MovimientoVistaDTO;

@Service
public class MovimientoVistaService {

    @Autowired
    private MovimientoRepository movimientoRepo;

    /* =====================================================
       MÉTODOS DE CÁLCULO DE SIGNO Y MONTO
       ===================================================== */

    private double basePesosCliente(Movimiento m, boolean esOrigen) {
        if (m.getUsdt() != null) {
            return esOrigen
                ? (m.getPesosOrigen() != null ? m.getPesosOrigen() : 0.0)
                : (m.getPesosDestino() != null ? m.getPesosDestino() : 0.0);
        }
        return m.getMonto() != null ? m.getMonto() : 0.0;
    }

    private double signoYMontoParaCliente(Integer clienteId, Movimiento m) {
        if (m.getClienteOrigen() != null && m.getClienteOrigen().getId().equals(clienteId)) {
            double v = basePesosCliente(m, true);
            return +v;
        }
        if (m.getPagoCliente() != null && m.getPagoCliente().getId().equals(clienteId)) {
            double v = basePesosCliente(m, false);
            return -v;
        }
        if ("PAGO".equalsIgnoreCase(m.getTipo())
            && m.getPagoCliente()!=null && m.getPagoCliente().getId().equals(clienteId)) {
            double v = m.getMonto()!=null ? m.getMonto() : 0.0;
            return +v;
        }
        if (m.getClienteOrigen() != null && m.getClienteOrigen().getId().equals(clienteId)
            && m.getCaja()!=null && m.getMonto()!=null) {
            return +(m.getMonto());
        }
        return 0.0;
    }

    private double basePesosProveedor(Movimiento m, boolean esOrigen) {
        if (m.getUsdt()!=null) {
            return esOrigen ? (m.getPesosOrigen()!=null? m.getPesosOrigen():0.0)
                            : (m.getPesosDestino()!=null? m.getPesosDestino():0.0);
        }
        return m.getMonto()!=null? m.getMonto():0.0;
    }

    private double signoYMontoParaProveedor(Integer proveedorId, Movimiento m) {
        if ("PAGO PROVEEDOR".equalsIgnoreCase(m.getTipo())
            && m.getPagoProveedor()!=null && m.getPagoProveedor().getId().equals(proveedorId)) {
            return -(m.getMonto()!=null? m.getMonto():0.0);
        }
        if ("PAGO EN USDT CLIENTE PROVEEDOR".equalsIgnoreCase(m.getTipo())
            && m.getPagoProveedor()!=null && m.getPagoProveedor().getId().equals(proveedorId)) {
            return -basePesosProveedor(m, false);
        }
        if ("PAGO EN USDT PROVEEDOR CLIENTE".equalsIgnoreCase(m.getTipo())
            && m.getProveedorOrigen()!=null && m.getProveedorOrigen().getId().equals(proveedorId)) {
            return +basePesosProveedor(m, true);
        }
        if (m.getProveedorOrigen()!=null && m.getProveedorOrigen().getId().equals(proveedorId)) {
            return +(m.getMonto()!=null? m.getMonto():0.0);
        }
        return 0.0;
    }

    private double signoYMontoParaCuentaCop(Integer cuentaId, Movimiento m) {
        if ("TRANSFERENCIA".equalsIgnoreCase(m.getTipo())) {
            if (m.getCuentaOrigen()!=null && m.getCuentaOrigen().getId().equals(cuentaId)) {
                double base = (m.getMonto()!=null? m.getMonto():0.0) + (m.getComision()!=null? m.getComision():0.0);
                return -base;
            }
            if (m.getCuentaDestino()!=null && m.getCuentaDestino().getId().equals(cuentaId)) {
                return +(m.getMonto()!=null? m.getMonto():0.0);
            }
        }
        if ("RETIRO".equalsIgnoreCase(m.getTipo())
            && m.getCuentaOrigen()!=null && m.getCuentaOrigen().getId().equals(cuentaId)) {
            double base = (m.getMonto()!=null? m.getMonto():0.0) + (m.getComision()!=null? m.getComision():0.0);
            return -base;
        }
        if ("DEPOSITO".equalsIgnoreCase(m.getTipo())
            && m.getCuentaDestino()!=null && m.getCuentaDestino().getId().equals(cuentaId)) {
            return +(m.getMonto()!=null? m.getMonto():0.0);
        }
        if ("PAGO PROVEEDOR".equalsIgnoreCase(m.getTipo())
            && m.getCuentaOrigen()!=null && m.getCuentaOrigen().getId().equals(cuentaId)) {
            double base = (m.getMonto()!=null? m.getMonto():0.0) + (m.getComision()!=null? m.getComision():0.0);
            return -base;
        }
        return 0.0;
    }

    private double signoYMontoParaCaja(Integer cajaId, Movimiento m) {
        if (m.getCaja()==null || !m.getCaja().getId().equals(cajaId)) return 0.0;
        if ("RETIRO".equalsIgnoreCase(m.getTipo())) {
            return +(m.getMonto()!=null? m.getMonto():0.0);
        }
        if ("DEPOSITO".equalsIgnoreCase(m.getTipo())) {
            return -(m.getMonto()!=null? m.getMonto():0.0);
        }
        if ("PAGO PROVEEDOR".equalsIgnoreCase(m.getTipo())) {
            return -(m.getMonto()!=null? m.getMonto():0.0);
        }
        if (m.getClienteOrigen()!=null && m.getMonto()!=null) {
            return +(m.getMonto());
        }
        return 0.0;
    }

    /* =====================================================
       MÉTODOS DE VISTA POR ENTIDAD
       ===================================================== */

    public List<MovimientoVistaDTO> vistaPorCliente(Integer clienteId) {
        List<Movimiento> movs = movimientoRepo
            .findByPagoCliente_IdOrClienteOrigen_IdOrderByFechaDesc(clienteId, clienteId);
        return movs.stream().map(m -> {
            double signed = signoYMontoParaCliente(clienteId, m);
            MovimientoVistaDTO dto = new MovimientoVistaDTO();
            dto.setId(m.getId());
            dto.setTipo(m.getTipo());
            dto.setFecha(m.getFecha());
            dto.setMontoSigned(signed);
            dto.setEntrada(signed > 0);
            dto.setSalida(signed < 0);
            dto.setDetalle(
                m.getClienteOrigen()!=null && m.getClienteOrigen().getId().equals(clienteId) ? "Cliente origen"
              : m.getPagoCliente()!=null && m.getPagoCliente().getId().equals(clienteId) ? "Cliente destino"
              : "-"
            );
            return dto;
        }).toList();
    }

    public List<MovimientoVistaDTO> vistaPorProveedor(Integer proveedorId) {
        List<Movimiento> movs = movimientoRepo
            .findByPagoProveedor_IdOrProveedorOrigen_IdOrderByFechaDesc(proveedorId, proveedorId);
        return movs.stream().map(m -> {
            double signed = signoYMontoParaProveedor(proveedorId, m);
            MovimientoVistaDTO dto = new MovimientoVistaDTO();
            dto.setId(m.getId());
            dto.setTipo(m.getTipo());
            dto.setFecha(m.getFecha());
            dto.setMontoSigned(signed);
            dto.setEntrada(signed > 0);
            dto.setSalida(signed < 0);
            dto.setDetalle(signed > 0 ? "Proveedor origen" : "Proveedor destino");
            return dto;
        }).toList();
    }

    public List<MovimientoVistaDTO> vistaPorCuentaCop(Integer cuentaId) {
        List<Movimiento> movs = movimientoRepo
            .findByCuentaOrigen_IdOrCuentaDestino_IdOrderByFechaDesc(cuentaId, cuentaId);
        return movs.stream().map(m -> {
            double signed = signoYMontoParaCuentaCop(cuentaId, m);
            MovimientoVistaDTO dto = new MovimientoVistaDTO();
            dto.setId(m.getId());
            dto.setTipo(m.getTipo());
            dto.setFecha(m.getFecha());
            dto.setMontoSigned(signed);
            dto.setEntrada(signed > 0);
            dto.setSalida(signed < 0);
            dto.setDetalle(
                m.getCuentaOrigen()!=null && m.getCuentaOrigen().getId().equals(cuentaId) ? "Cuenta origen"
              : m.getCuentaDestino()!=null && m.getCuentaDestino().getId().equals(cuentaId) ? "Cuenta destino"
              : "-"
            );
            return dto;
        }).toList();
    }

    public List<MovimientoVistaDTO> vistaPorCaja(Integer cajaId) {
        List<Movimiento> movs = movimientoRepo.findByCaja_IdOrderByFechaDesc(cajaId);
        return movs.stream().map(m -> {
            double signed = signoYMontoParaCaja(cajaId, m);
            MovimientoVistaDTO dto = new MovimientoVistaDTO();
            dto.setId(m.getId());
            dto.setTipo(m.getTipo());
            dto.setFecha(m.getFecha());
            dto.setMontoSigned(signed);
            dto.setEntrada(signed > 0);
            dto.setSalida(signed < 0);
            dto.setDetalle("Caja");
            return dto;
        }).toList();
    }
}
