package com.genzai.config;

import com.genzai.user.entity.Role;
import com.genzai.user.entity.User;
import com.genzai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedUserIfNotExists("demo@genzai.com", "demo1234", "Demo Student", Role.STUDENT);
        seedUserIfNotExists("admin@genzai.com", "demo1234", "GenZ AI Administrator", Role.ADMIN);
    }

    private void seedUserIfNotExists(String email, String rawPassword, String fullName, Role role) {
        if (!userRepository.existsByEmail(email)) {
            User user = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(rawPassword))
                    .fullName(fullName)
                    .role(role)
                    .build();
            userRepository.save(user);
            log.info("Successfully seeded demo user: {}", email);
        }
    }
}
