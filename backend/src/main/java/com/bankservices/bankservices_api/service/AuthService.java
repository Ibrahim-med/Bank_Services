
package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.dto.request.RegisterRequest;
import com.bankservices.bankservices_api.dto.request.LoginRequest;
import com.bankservices.bankservices_api.dto.response.LoginResponse;
import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.enums.StatutUtilisateur;
import com.bankservices.bankservices_api.exception.AccountNotActiveException;
import com.bankservices.bankservices_api.repository.ClientRepository;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UtilisateurRepository utilisateurRepository,
            ClientRepository clientRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.utilisateurRepository = utilisateurRepository;
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public String register(RegisterRequest request) {

        if (utilisateurRepository.existsByUsername(
                request.getUsername())) {
            throw new IllegalArgumentException(
                    "Username already exists");
        }

        Client client = clientRepository
                .findById(request.getIdClient())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Client not found"));

        if (utilisateurRepository.existsByClient_IdClient(
                client.getIdClient())) {
            throw new IllegalArgumentException(
                    "This client already has an account");
        }

        Utilisateur utilisateur = new Utilisateur();

        utilisateur.setUsername(request.getUsername());
        utilisateur.setPassword(
                passwordEncoder.encode(request.getPassword()));
        utilisateur.setRole("CLIENT");
        utilisateur.setClient(client);

        utilisateurRepository.save(utilisateur);

        return "User registered successfully";
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        Utilisateur utilisateur = utilisateurRepository
                .findByUsername(request.getUsername())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid username or password"));

        if (!passwordEncoder.matches(
                request.getPassword(),
                utilisateur.getPassword())) {
            throw new IllegalArgumentException(
                    "Invalid username or password");
        }

        // Checked only AFTER the password, so the account status is never
        // revealed to someone who does not know the credentials.
        if (utilisateur.getStatut() == StatutUtilisateur.PENDING_ACTIVATION) {
            throw new AccountNotActiveException(
                    "Your account is not activated yet. Use the activation link sent to your email address.");
        }

        if (utilisateur.getStatut() != StatutUtilisateur.ACTIVE) {
            throw new AccountNotActiveException(
                    "This account has been disabled. Please contact BankServices.");
        }

        String token = jwtService.generateToken(
                utilisateur.getUsername());

        Integer idClient = utilisateur.getClient() != null
                ? utilisateur.getClient().getIdClient()
                : null;

        return new LoginResponse(
                token,
                "Bearer",
                utilisateur.getUsername(),
                utilisateur.getRole(),
                idClient
        );
    }
}
