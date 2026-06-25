package com.talentpredict.shared.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

@Configuration

public class AiWebClientConfig {

    @Bean
    public WebClient talentPredictAiWebClient(
            @Value("${talentpredict.ai.base-url:http://localhost:8000}") String baseUrl) {
        String base = (baseUrl == null || baseUrl.isBlank()) ? "http://localhost:8000" : baseUrl.trim();
        if (base.endsWith("/api")) {
            base = base.substring(0, base.length() - 4);
        }
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(90))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000);
        return WebClient.builder()
                .baseUrl(base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
