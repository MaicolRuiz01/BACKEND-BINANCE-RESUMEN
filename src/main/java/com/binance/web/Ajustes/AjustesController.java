package com.binance.web.Ajustes;

import com.binance.web.Ajustes.AjustesService;

import java.util.List;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.Ajustes;
import com.binance.web.Entity.Supplier;
import com.binance.web.Entity.Cliente;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@RequestMapping("/ajustes")
@CrossOrigin
public class AjustesController {


@Autowired
private AjustesService ajustesService;



@GetMapping("/listar")
public List<Ajustes> getAjustes() {
    return ajustesService.allAjustes();
}

@PostMapping("/crear")
public Ajustes crearAjuste(@RequestBody Ajustes ajuste) {
    return ajustesService.crearAjuste(ajuste);
}

@GetMapping("/cliente/{id}")
public List<Ajustes> obtenerAjustesPorCliente(@PathVariable Integer id) {
    return ajustesService.obtenerajustesporUsuarioCL(id);
}

@GetMapping("/proveedor/{id}")
public List<Ajustes> obtenerajustesporUsuarioPR(@PathVariable Integer id) {
    return ajustesService.obtenerajustesporUsuarioPR(id);
}






}