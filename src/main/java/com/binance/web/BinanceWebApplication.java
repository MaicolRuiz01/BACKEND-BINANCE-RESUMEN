package com.binance.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BinanceWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(BinanceWebApplication.class, args);
    }
}
