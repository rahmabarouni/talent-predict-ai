package com.talentpredict;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.cdimascio.dotenv.Dotenv;

import lombok.extern.slf4j.Slf4j;

/**
 * TalentPredict Backend — Spring Boot 3.4.2 / Java 25
 *
 * NOTE: Camunda BPM is installed but DISABLED.
 * - All Camunda auto-configurations are excluded in application.properties
 * - No BPMN process files (.bpmn) or JavaDelegates are implemented yet
 * - To re-enable Camunda:
 * 1. Uncomment Camunda dependencies in pom.xml
 * 2. Remove spring.autoconfigure.exclude lines from application.properties
 * 3. Add .bpmn files to src/main/resources/processes/
 * - Planned workflows: Formation approval, Employee onboarding
 */
@SpringBootApplication
@RestController
@Slf4j
@EnableAsync
@EnableScheduling
public class TalentPredictApplication {

    public static void main(String[] args) {
        // Load .env file and set system properties
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry -> {
            if (System.getProperty(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        });

        SpringApplication.run(TalentPredictApplication.class, args);
        log.info("=================================================");
        log.info("   TalentPredict API is running on port 8081     ");
        log.info("   Camunda BPM: DISABLED (no BPMN processes)     ");
        log.info("   Environment: .env file loaded                 ");
        log.info("=================================================");
    }

    @GetMapping("/api/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("TalentPredict API is running!");
    }
}
