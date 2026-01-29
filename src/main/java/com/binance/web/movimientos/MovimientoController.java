package com.binance.web.movimientos;

import java.util.Date;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.Movimiento;
import com.binance.web.Repository.MovimientoRepository;
import com.binance.web.model.AjusteSaldoDto;
import com.binance.web.model.MovimientoVistaDTO;
import com.binance.web.model.PagoClienteAClienteDto;
import com.binance.web.model.PagoClienteAProveedorDto;
import com.binance.web.model.PagoProveedorAClienteDto;
import com.binance.web.model.ResumenDiarioDTO;
import com.binance.web.service.ClienteExcelService;
import com.binance.web.service.ProveedorExcelService;


@RestController
@RequestMapping("/movimiento")
public class MovimientoController {

	@Autowired
	private MovimientoService movimientoService;
	@Autowired private MovimientoRepository movimientoRepo;
	@Autowired
	private MovimientoVistaService vistaService;
	@Autowired
	private ProveedorExcelService proveedorExcelService;
	@Autowired
	private ClienteExcelService clienteExcelService;

	@PostMapping("/deposito")
	public Movimiento deposito(@RequestParam Integer cuentaId, @RequestParam Integer cajaId,
			@RequestParam Double monto) {
		return movimientoService.RegistrarDeposito(cuentaId, cajaId, monto);
	}

	@PostMapping("/retiro")
	public Movimiento retiro(@RequestParam Integer cuentaId, @RequestParam Integer cajaId, @RequestParam Double monto) {
		return movimientoService.RegistrarRetiro(cuentaId, cajaId, monto);
	}

	@PostMapping("/transferencia")
	public Movimiento transferencia(@RequestParam Integer origenId, @RequestParam Integer destinoId,
			@RequestParam Double monto) {
		return movimientoService.RegistrarTransferencia(origenId, destinoId, monto);
	}

	@PostMapping("/pago")
	public Movimiento pago(@RequestParam Integer cuentaId, @RequestParam Integer clienteId,
			@RequestParam Double monto) {
		return movimientoService.registrarPagoCliente(cuentaId, clienteId, monto);
	}

	@PostMapping("/pago-proveedor")
	public Movimiento pagoProveedor(@RequestParam(required = false) Integer cuentaId,
			@RequestParam(required = false) Integer caja, @RequestParam(required = false) Integer proveedorOrigen,
			@RequestParam(required = false) Integer clienteId, @RequestParam Integer proveedor,
			@RequestParam Double monto) {
		return movimientoService.registrarPagoProveedor(cuentaId, caja, proveedorOrigen, proveedor, clienteId, monto);
	}

	@PostMapping("/pago-caja")
	public Movimiento pagoClienteCaja(@RequestParam Integer clienteId, @RequestParam Integer cajaId,
			@RequestParam Double monto) {
		return movimientoService.registrarPagoCaja(clienteId, cajaId, monto);
	}

	@GetMapping("/listar")
	public List<Movimiento> listar() {
		return movimientoService.listar();
	}

	@GetMapping("/retiros")
	public List<MovimientoDTO> listarRetiros() {
		return movimientoService.listarRetiros().stream().map(this::mapToDto).toList();
	}

	@GetMapping("/depositos")
	public List<MovimientoDTO> listarDepositos() {
		return movimientoService.listarDepositos().stream().map(this::mapToDto).toList();
	}

	@GetMapping("/transferencias")
	public List<MovimientoDTO> listarTransferencias() {
		return movimientoService.listarTransferencias().stream().map(this::mapToDto).toList();
	}

	@GetMapping("/pagos")
	public List<MovimientoDTO> listarPagos() {
		return movimientoService.listarPagos().stream().map(this::mapToDto).toList();
	}

	@PutMapping("/{id}")
	public ResponseEntity<Movimiento> actualizarMovimiento(@PathVariable Integer id,
			@RequestBody MovimientoUpdateDTO dto) {

		Movimiento actualizado = movimientoService.actualizarMovimiento(id, dto.getMonto(), dto.getCuentaOrigenId(),
				dto.getCuentaDestinoId(), dto.getCajaId());
		return ResponseEntity.ok(actualizado);
	}

	@GetMapping("/pagos-proveedor/{proveedorId}")
	public List<MovimientoDTO> listarPagosProveedorPorId(@PathVariable Integer proveedorId) {
		return movimientoService.listarPagosProveedorPorId(proveedorId).stream().map(this::mapToDto).toList();
	}

	@GetMapping("/pagos-cuenta/{cuentaId}")
	public List<MovimientoDTO> listarMovimientosPorCuentaId(@PathVariable Integer cuentaId) {
		return movimientoService.listarPagosCuentaPorId(cuentaId).stream().map(this::mapToDto).toList();
	}

	@GetMapping("/cliente/{clienteId}")
	public List<MovimientoDTO> listarMovimientosPorCliente(@PathVariable Integer clienteId) {
		return movimientoService.listarMovimientosPorCliente(clienteId).stream().map(this::mapToDto).toList();
	}

	@GetMapping("/test")
	public String getMethodName() {
		return "hola ";
	}

	@PostMapping("/pago-cliente-a-cliente")
	public Movimiento pagoClienteACliente(@RequestBody PagoClienteAClienteDto dto) {
		return movimientoService.registrarPagoClienteACliente(dto);
	}

	@GetMapping("/caja/{cajaId}")
	public List<MovimientoDTO> listarMovimientosPorCaja(@PathVariable Integer cajaId) {
		return movimientoService.listarMovimientosPorCaja(cajaId).stream().map(this::mapToDto).toList();
	}
	
	// MovimientoController.java
	@PostMapping("/pago-cliente-a-proveedor")
	public Movimiento pagoClienteAProveedor(@RequestBody PagoClienteAProveedorDto dto) {
	    return movimientoService.registrarPagoClienteAProveedor(dto);
	}
	
	@PostMapping("/pago-cliente-a-cliente-cop")
	public Movimiento pagoClienteAClienteCop(
	        @RequestParam Integer clienteOrigenId,
	        @RequestParam Integer clienteDestinoId,
	        @RequestParam Double montoCop) {
	    return movimientoService.registrarPagoClienteAClienteCop(clienteOrigenId, clienteDestinoId, montoCop);
	}
	
	@PostMapping("/pago-proveedor-a-cliente")
	public Movimiento pagoProveedorACliente(@RequestBody PagoProveedorAClienteDto dto) {
	    return movimientoService.registrarPagoProveedorACliente(dto);
	}
	
	// MovimientoController.java
	@PostMapping("/ajuste-saldo")
	public Movimiento ajustarSaldo(@RequestBody AjusteSaldoDto dto) {
	    return movimientoService.registrarAjusteSaldo(dto);
	}
	@GetMapping("/vista/cliente/{clienteId}")
	public List<MovimientoVistaDTO> vistaCliente(@PathVariable Integer clienteId) {
	    return vistaService.vistaPorCliente(clienteId);
	}

	@GetMapping("/vista/proveedor/{proveedorId}")
	public List<MovimientoVistaDTO> vistaProveedor(@PathVariable Integer proveedorId) {
	    return vistaService.vistaPorProveedor(proveedorId);
	}

	@GetMapping("/vista/cuenta-cop/{cuentaId}")
	public List<MovimientoVistaDTO> vistaCuenta(@PathVariable Integer cuentaId) {
	    return vistaService.vistaPorCuentaCop(cuentaId);
	}

	@GetMapping("/vista/caja/{cajaId}")
	public List<MovimientoVistaDTO> vistaCaja(@PathVariable Integer cajaId) {
	    return vistaService.vistaPorCaja(cajaId);
	}
	
	@GetMapping("/resumen/cliente/{clienteId}")
    public ResumenDiarioDTO resumenCliente(@PathVariable Integer clienteId) {
        return vistaService.resumenClienteHoy(clienteId);
    }

    @GetMapping("/resumen/proveedor/{proveedorId}")
    public ResumenDiarioDTO resumenProveedor(@PathVariable Integer proveedorId) {
        return vistaService.resumenProveedorHoy(proveedorId);
    }
    
    @GetMapping("/resumen/cuenta-cop/{cuentaId}")
    public ResumenDiarioDTO resumenCuentaCop(@PathVariable Integer cuentaId) {
        return vistaService.resumenCuentaCopHoy(cuentaId);
    }

    
    @PostMapping("/pago-cuenta-cop-a-cliente")
    public Movimiento pagoCuentaCopACliente(
            @RequestParam Integer cuentaId,
            @RequestParam Integer clienteId,
            @RequestParam Double monto) {
        return movimientoService.registrarPagoCuentaCopACliente(cuentaId, clienteId, monto);
    }

    @GetMapping("/ajustes/cliente/{clienteId}")
    public List<MovimientoDTO> ajustesCliente(@PathVariable Integer clienteId) {
        return movimientoService.listarAjustesCliente(clienteId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    // 🔹 Ajustes de saldo de un PROVEEDOR
    @GetMapping("/ajustes/proveedor/{proveedorId}")
    public List<MovimientoDTO> ajustesProveedor(@PathVariable Integer proveedorId) {
        return movimientoService.listarAjustesProveedor(proveedorId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    // 🔹 Ajustes de saldo de una CUENTA COP
    @GetMapping("/ajustes/cuenta-cop/{cuentaId}")
    public List<MovimientoDTO> ajustesCuentaCop(@PathVariable Integer cuentaId) {
        return movimientoService.listarAjustesCuentaCop(cuentaId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    // 🔹 Ajustes de saldo de una CAJA
    @GetMapping("/ajustes/caja/{cajaId}")
    public List<MovimientoDTO> ajustesCaja(@PathVariable Integer cajaId) {
        return movimientoService.listarAjustesCaja(cajaId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    // =========================
    //   MAPEO A MovimientoDTO
    // =========================

    private MovimientoDTO mapToDto(Movimiento movimiento) {
        MovimientoDTO dto = new MovimientoDTO();
        dto.setId(movimiento.getId());
        dto.setTipo(movimiento.getTipo());
        dto.setFecha(movimiento.getFecha());
        dto.setMonto(movimiento.getMonto());

        // Campos “normales”
        dto.setCuentaOrigen(
                movimiento.getCuentaOrigen() != null
                        ? movimiento.getCuentaOrigen().getName()
                        : null
        );
        dto.setCuentaDestino(
                movimiento.getCuentaDestino() != null
                        ? movimiento.getCuentaDestino().getName()
                        : null
        );
        dto.setCaja(
                movimiento.getCaja() != null
                        ? movimiento.getCaja().getName()
                        : null
        );
        dto.setPagoCliente(
                movimiento.getPagoCliente() != null
                        ? movimiento.getPagoCliente().getNombre()
                        : null
        );
        dto.setPagoProveedor(
                movimiento.getPagoProveedor() != null
                        ? movimiento.getPagoProveedor().getName()
                        : null
        );

        // 🔹 Campos específicos de AJUSTE
        dto.setMotivo(movimiento.getMotivo());
        dto.setActor(movimiento.getActor());
        dto.setSaldoAnterior(movimiento.getSaldoAnterior());
        dto.setSaldoNuevo(movimiento.getSaldoNuevo());
        dto.setDiferencia(movimiento.getDiferencia());

        return dto;
    }
    
    @GetMapping("/debug/time")
    public Map<String, String> debugTime() {
        return Map.of(
            "java_default", new Date().toString(),
            "localdatetime_now", LocalDateTime.now().toString(),
            "bogota", ZonedDateTime.now(ZoneId.of("America/Bogota")).toString(),
            "jvm_timezone", TimeZone.getDefault().getID()
        );
    }
    
    @GetMapping(
    		  value = "/excel/proveedor/{proveedorId}",
    		  produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    		)
    		public ResponseEntity<byte[]> excelProveedor(@PathVariable Integer proveedorId) throws Exception {
    		    byte[] file = proveedorExcelService.exportProveedor(proveedorId);

    		    String filename = "proveedor_" + proveedorId + "_movimientos.xlsx";
    		    return ResponseEntity.ok()
    		        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
    		        .body(file);
    		}
    		
    		@GetMapping(
    				  value = "/excel/cliente/{clienteId}",
    				  produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    				)
    				public ResponseEntity<byte[]> excelCliente(@PathVariable Integer clienteId) throws Exception {
    				    byte[] file = clienteExcelService.exportCliente(clienteId);

    				    String filename = "cliente_" + clienteId + "_movimientos.xlsx";
    				    return ResponseEntity.ok()
    				        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
    				        .body(file);
    				}


}
