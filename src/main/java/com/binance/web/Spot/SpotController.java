package com.binance.web.Spot;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/spot")
public class SpotController {
	
	@Autowired
    private SpotService spotOrderService;

    // Endpoint para crear una nueva orden Spot
    @PostMapping
    public ResponseEntity<Spot> createSpotOrder(@RequestBody Spot spotOrder) {
        spotOrderService.saveSpot(spotOrder);
        return ResponseEntity.status(201).body(spotOrder);
    }

    // Endpoint para obtener todas las órdenes Spot
    @GetMapping
    public ResponseEntity<List<Spot>> getAllSpotOrders() {
        List<Spot> orders = spotOrderService.findAllOrdersSpot();
        return ResponseEntity.ok(orders);
    }

    // Endpoint para obtener una orden Spot por ID
    @GetMapping("/{id}")
    public ResponseEntity<Spot> getSpotOrderById(@PathVariable Integer id) {
        Spot order = spotOrderService.findByIdSpot(id);
        return order != null ? ResponseEntity.ok(order) : ResponseEntity.notFound().build();
    }

    // Endpoint para actualizar una orden Spot
    @PutMapping("/{id}")
    public ResponseEntity<Spot> updateSpotOrder(@PathVariable Integer id, @RequestBody Spot spotOrder) {
        spotOrderService.updateSpotOrder(id, spotOrder);
        return ResponseEntity.ok(spotOrder);
    }
	

}
