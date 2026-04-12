package com.example.invoice_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")        // Áp dụng cho tất cả endpoint
                        .allowedOrigins("*")      // Cho tất cả origin
                        .allowedMethods("*")      // Cho tất cả method: GET, POST, PUT, DELETE...
                        .allowedHeaders("*")      // Cho tất cả header
                        .allowCredentials(false); // Nếu không dùng cookie/session
            }
        };
    }
}
