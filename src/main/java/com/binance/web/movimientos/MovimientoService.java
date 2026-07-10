package com.binance.web.movimientos;


import java.time.LocalDateTime;
import java.util.List;

import com.binance.web.Entity.Movimiento;
import com.binance.web.model.AjusteSaldoDto;
import com.binance.web.model.PagoClienteAClienteDto;
import com.binance.web.model.PagoClienteAProveedorDto;
import com.binance.web.model.PagoProveedorAClienteDto;

public interface MovimientoService {
	
	Movimiento RegistrarTransferencia(Integer idCuentoFrom, Integer idCuentaTo, Double monto);
	/** Traspaso entre cajas (efectivo). SIN comisión 4x1000. */
	Movimiento RegistrarTransferenciaCaja(Integer cajaFromId, Integer cajaToId, Double monto);
	/**
	 * @param tipoRetiro "CAJERO" o "CORRESPONSAL"
	 */
	Movimiento RegistrarRetiro(Integer cuentaId, Integer cajaId, Double monto, String tipoRetiro);
	Movimiento RegistrarDeposito(Integer cuentaId,Integer cajaId, Double monto);
	Movimiento registrarPagoCliente(Integer cuentaId, Integer clienteId, Double monto);
	List<Movimiento> listar();
	List<Movimiento> listarRetiros();
	List<Movimiento> listarDepositos();
	List<Movimiento> listarTransferencias();
	List<Movimiento> listarPagos();
	Movimiento actualizarMovimiento(Integer id, Double monto, Integer cuentaOrigenId, Integer cuentaDestinoId, Integer cajaId);
	Movimiento registrarPagoProveedor(Integer cuentaCopId, Integer cajaId, Integer proveedorOrigenId, Integer proveedorDestinoId,Integer clienteId, Double monto);

	/** El proveedor nos da efectivo → ENTRA a una caja (sin 4x1000). */
	Movimiento registrarPagoProveedorACaja(Integer proveedorId, Integer cajaId, Double monto);
	List<Movimiento> listarPagosProveedorPorId(Integer proveedorId);
	List<Movimiento> listarMovimientosPorCliente(Integer clienteId);
	Movimiento registrarPagoCaja(Integer clienteId, Integer cajaId, Double monto);
	List<Movimiento> listarPagosCuentaPorId(Integer cajaId);
	Movimiento registrarPagoClienteACliente(PagoClienteAClienteDto dto);
	List<Movimiento> listarMovimientosPorCaja(Integer cajaId);
	/** Proyección liviana (1 query) de los movimientos de una caja, para carga rápida. */
	List<MovimientoDTO> listarMovimientosCajaLite(Integer cajaId);
	Movimiento registrarPagoClienteAProveedor(PagoClienteAProveedorDto dto);
	Movimiento registrarPagoClienteAClienteCop(Integer clienteOrigenId, Integer clienteDestinoId, Double montoCop);
	Movimiento registrarPagoProveedorACliente(PagoProveedorAClienteDto dto);
	
	Movimiento registrarAjusteSaldo(AjusteSaldoDto dto);
	Movimiento registrarPagoCuentaCopACliente(Integer cuentaCopId, Integer clienteId, Double monto);
	List<Movimiento> listarAjustesCliente(Integer clienteId);
	List<Movimiento> listarAjustesProveedor(Integer proveedorId);
	List<Movimiento> listarAjustesCuentaCop(Integer cuentaId);
	List<Movimiento> listarAjustesCaja(Integer cajaId);

	/** Igual que listarMovimientosCajaLite pero acotado a un rango de fechas (para el bot de Telegram). */
	List<MovimientoDTO> listarMovimientosCajaLiteEntreFechas(Integer cajaId, LocalDateTime desde, LocalDateTime hasta);
	/** Igual que listarAjustesCaja pero acotado a un rango de fechas (para el bot de Telegram). */
	List<Movimiento> listarAjustesCajaEntreFechas(Integer cajaId, LocalDateTime desde, LocalDateTime hasta);

	/** Elimina un movimiento revirtiendo sus efectos de saldo (solo PAGO PROVEEDOR por ahora). */
	void eliminarMovimiento(Integer id);

}
