package com.creditlens.scheduler;

import com.creditlens.entity.UserEntity;
import com.creditlens.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds default users on first startup:
 *   admin   / password123  → ADMIN
 *   officer / password123  → OFFICER
 *   viewer  / password123  → VIEWER
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository  userRepo;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepo.count() == 0) {
            createUser("admin",   "password123", "ADMIN");
            createUser("officer", "password123", "OFFICER");
            createUser("viewer",  "password123", "VIEWER");
            log.info("=====================================================");
            log.info("Default users seeded:");
            log.info("  admin   / password123  (ADMIN)");
            log.info("  officer / password123  (OFFICER)");
            log.info("  viewer  / password123  (VIEWER)");
            log.info("=====================================================");
        }
    }

    private void createUser(String username, String rawPassword, String role) {
        userRepo.save(UserEntity.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .build());
    }
}
