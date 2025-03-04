package com.binance.web.controller;

@RestController
@RequestMapping("/api/test")
public class TestController {
    @GetMapping("/binance-time")
    public ResponseEntity<String> testBinanceConnection() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.binance.com/api/v3/time"))
                .GET()
                .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.ok(response.body());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }
}
