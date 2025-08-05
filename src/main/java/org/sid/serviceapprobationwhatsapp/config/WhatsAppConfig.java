package org.sid.serviceapprobationwhatsapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WhatsAppConfig {

    @Value("${whatsapp.api.token}")
    private String whatsappApiToken;

    @Value("${whatsapp.api.url}")
    private String whatsappApiUrl;

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl(whatsappApiUrl)
                .defaultHeader("Authorization", "Bearer " + whatsappApiToken)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
