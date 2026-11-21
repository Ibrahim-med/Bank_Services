
package com.bankservices.bankservices_api.config;

import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminInitializer.class);

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminInitializer(
            UtilisateurRepository utilisateurRepository,
            PasswordEncoder passwordEncoder) {

        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {

        String username = System.getenv("ADMIN_USERNAME");
        String password = System.getenv("ADMIN_PASSWORD");

        // Do not create an account if the environment variables are missing.
        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {

            logger.info("ADMIN initialization skipped: credentials not configured.");
            return;
        }

        // Avoid creating a duplicate account.
        if (utilisateurRepository.existsByUsername(username)) {
            logger.info("ADMIN initialization skipped: account already exists.");
            return;
        }

        Utilisateur admin = new Utilisateur();

        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole("ADMIN");

        utilisateurRepository.save(admin);

        // Neither the configured username nor the password is ever logged.
        logger.info("ADMIN account initialized successfully.");
    }
}
