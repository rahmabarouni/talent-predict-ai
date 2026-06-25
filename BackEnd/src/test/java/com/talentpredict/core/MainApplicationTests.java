package com.talentpredict.core;

import com.talentpredict.TalentPredictApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Main application context test.
 * Verifies that the Spring Boot application context loads correctly.
 */
@SpringBootTest(classes = TalentPredictApplication.class)
@ActiveProfiles("test")
class MainApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the application context loads without errors
    }
}
