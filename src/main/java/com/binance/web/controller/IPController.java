package com.binance.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.InetSocketAddress;

@RestController
@RequestMapping("/api")
public class IPController {

    @GetMapping("/check-ip")
    public String getServerIp() {
        try {
            // Usar Proxy para verificar qué IP está saliendo
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("52.67.10.183", 80));
            URL url = new URL("https://api64.ipify.org?format=json");

            HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
            conn.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            return "Tu IP pública (detectada por Binance): " + content.toString();

        } catch (Exception e) {
            return "Error al obtener IP: " + e.getMessage();
        }
    }
}
