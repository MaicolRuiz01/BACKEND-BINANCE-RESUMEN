package com.binance.web.Spot;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.binance.web.Entity.Spot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    
    @GetMapping("/trx-precio/{date}")
    public ResponseEntity<String> getTrxPriceByDate(@PathVariable String date) {
        try {
            // Validar el formato de la fecha (yyyy-MM-dd)
            LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);

            // Construir la URL de la API de CoinGecko
            String apiUrl = String.format("https://api.coingecko.com/api/v3/coins/tron/history?date=%s", 
                LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));

            // Realizar la solicitud HTTP
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.getForObject(apiUrl, String.class);

            // Procesar la respuesta JSON para extraer el precio
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            JsonNode marketData = root.path("market_data");
            if (marketData.isMissingNode()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Datos no disponibles para la fecha proporcionada.");
            }
            double price = marketData.path("current_price").path("usd").asDouble();

            return ResponseEntity.ok("Precio de TRX el " + date + ": $" + price);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body("Formato de fecha inválido. Utiliza 'yyyy-MM-dd'.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al obtener los datos: " + e.getMessage());
        }
    }


}
