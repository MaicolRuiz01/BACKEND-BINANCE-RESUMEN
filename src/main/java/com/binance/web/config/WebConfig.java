package com.binance.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
<<<<<<< HEAD
        registry.addMapping("/*")
                .allowedOrigins("*") // Cambia esto por la URL de tu frontend
=======
        registry.addMapping("/**")
                .allowedOrigins("*", "http://localhost:4200") // Cambia esto por la URL de tu frontend
>>>>>>> origin
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*");
    }
}
