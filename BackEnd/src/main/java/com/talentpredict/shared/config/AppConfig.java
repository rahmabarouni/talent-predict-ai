package com.talentpredict.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableScheduling
public class AppConfig {

    /** Default RestTemplate — used by all services (45 s read timeout). */
    @Bean
    @Primary
    public RestTemplate restTemplate(
            @Value("${http.client.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${http.client.read-timeout-ms:45000}") long readTimeoutMs) {
        int safeConnect = (int) Math.max(1000L, Math.min(Integer.MAX_VALUE, connectTimeoutMs));
        int safeRead = (int) Math.max(1000L, Math.min(Integer.MAX_VALUE, readTimeoutMs));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(safeConnect);
        requestFactory.setReadTimeout(safeRead);
        return new RestTemplate(requestFactory);
    }

    /**
     * Short-timeout RestTemplate dedicated to n8n webhook calls.
     * n8n can hang indefinitely when Ollama is slow; 30 s prevents thread starvation.
     * Inject with @Qualifier("n8nRestTemplate").
     */
    @Bean("n8nRestTemplate")
    public RestTemplate n8nRestTemplate(
            @Value("${n8n.http.connect-timeout-ms:4000}") long connectTimeoutMs,
            @Value("${n8n.http.read-timeout-ms:60000}") long readTimeoutMs) {
        int safeConnect = (int) Math.max(1000L, Math.min(Integer.MAX_VALUE, connectTimeoutMs));
        int safeRead = (int) Math.max(1000L, Math.min(Integer.MAX_VALUE, readTimeoutMs));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(safeConnect);
        requestFactory.setReadTimeout(safeRead);
        return new RestTemplate(requestFactory);
    }
}
