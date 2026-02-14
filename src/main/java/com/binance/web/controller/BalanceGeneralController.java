package com.binance.web.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.BalanceGeneral;
import com.binance.web.service.BalanceGeneralService;

@RestController
@RequestMapping("/balance-general")
public class BalanceGeneralController {

    @Autowired
    private BalanceGeneralService balanceService;

    @GetMapping("/calcular")
    public void calcular(@RequestParam("fecha") @DateTimeFormat(pattern = "dd-MM-yyyy") LocalDate fecha) {
        balanceService.calcularOBalancear(fecha);
    }

    @GetMapping("/listar")
    public List<BalanceGeneral> listar() {
        return balanceService.listarTodos();
    }

    @GetMapping("/fecha")
    public BalanceGeneral obtener(
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return balanceService.obtenerPorFecha(fecha);
    }

    // trae todos los balces ademas de calcular el de hoy
    @GetMapping("/hoy")
    public List<BalanceGeneral> calcularHoyYListar() {
        LocalDate today = LocalDate.now();
        balanceService.calcularOBalancear(today);
        return balanceService.listarTodos();
    }

    @GetMapping("/cajas/total")
    public Map<String, Double> obtenerTotalDeCajas() {
        double totalCajas = balanceService.obtenerTotalCajas();
        return Map.of("total", totalCajas);
    }

    @GetMapping("/clientes/total")
    public ResponseEntity<Double> obtenerTotalClientes() {
        Double totalClientes = balanceService.obtenerTotalClientes();
        return ResponseEntity.ok(totalClientes);
    }
    @PostMapping("/recalcular/{fecha}")
    public ResponseEntity<BalanceGeneral> recalcularPorFecha(
            @PathVariable @DateTimeFormat(pattern = "dd-MM-yyyy") LocalDate fecha
    ) {
        balanceService.calcularOBalancear(fecha);
        return ResponseEntity.ok(balanceService.obtenerPorFecha(fecha));
    }


}
