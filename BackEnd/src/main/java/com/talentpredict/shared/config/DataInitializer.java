package com.talentpredict.shared.config;

import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByEmail("admin@talentpredict.com")) {
            User admin = new User();
            admin.setFirstName("Admin");
            admin.setLastName("TalentPredict");
            admin.setEmail("admin@talentpredict.com");
            admin.setPassword(passwordEncoder.encode("Admin@123"));
            admin.setRole(User.Role.ADMIN);
            admin.setIsActive(true);
            admin.setEmailVerified(true);
            admin.setEmailVerifiedAt(Instant.now());

            userRepository.save(admin);
            log.info("=======================================================");
            log.info("  Default admin account created:");
            log.info("  Email   : admin@talentpredict.com");
            log.info("  Password: Admin@123");
            log.info("  Role    : ADMIN");
            log.info("=======================================================");
        } else {
            log.info("Admin account already exists. Skipping seed.");
        }
    }
}
