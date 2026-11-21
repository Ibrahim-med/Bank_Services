
package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.exception.ResourceNotFoundException;
import com.bankservices.bankservices_api.dto.request.ClientRequest;
import com.bankservices.bankservices_api.dto.response.ClientResponse;
import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.enums.StatutUtilisateur;
import com.bankservices.bankservices_api.repository.AccountActivationTokenRepository;
import com.bankservices.bankservices_api.repository.BillRepository;
import com.bankservices.bankservices_api.repository.ClientRepository;
import com.bankservices.bankservices_api.repository.PasswordResetTokenRepository;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final BillRepository billRepository;
    private final AccountActivationTokenRepository activationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public ClientService(
            ClientRepository clientRepository,
            UtilisateurRepository utilisateurRepository,
            BillRepository billRepository,
            AccountActivationTokenRepository activationTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository) {

        this.clientRepository = clientRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.billRepository = billRepository;
        this.activationTokenRepository = activationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    // Create a client
    @Transactional
    public ClientResponse createClient(ClientRequest request) {

        Client client = new Client();

        client.setNom(request.getNom());
        client.setPrenom(request.getPrenom());
        client.setEmail(request.getEmail());
        client.setTelephone(request.getTelephone());
        client.setAdresse(request.getAdresse());

        Client savedClient = clientRepository.save(client);

        return convertToResponse(savedClient, null);
    }

    // Get all clients (with their online banking login status)
    @Transactional(readOnly = true)
    public List<ClientResponse> getAllClients() {

        Map<Integer, Utilisateur> loginsByClient = utilisateurRepository
                .findByClientIsNotNull()
                .stream()
                .collect(Collectors.toMap(
                        utilisateur -> utilisateur.getClient().getIdClient(),
                        Function.identity()));

        return clientRepository.findAll()
                .stream()
                .map(client -> convertToResponse(
                        client, loginsByClient.get(client.getIdClient())))
                .toList();
    }

    // Get client by ID
    @Transactional(readOnly = true)
    public ClientResponse getClientById(Integer id) {

        Client client = clientRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Client not found: " + id));

        return convertToResponse(client, findLogin(id));
    }

    // Update a client
    @Transactional
    public ClientResponse updateClient(
            Integer id, ClientRequest request) {

        Client client = clientRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Client not found: " + id));

        client.setNom(request.getNom());
        client.setPrenom(request.getPrenom());
        client.setEmail(request.getEmail());
        client.setTelephone(request.getTelephone());
        client.setAdresse(request.getAdresse());

        Client updatedClient = clientRepository.save(client);

        return convertToResponse(updatedClient, findLogin(id));
    }

    // Delete a client
    @Transactional
    public void deleteClient(Integer id) {

        Client client = clientRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Client not found: " + id));

        if (!client.getComptes().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot delete a client who has bank accounts.");
        }

        if (billRepository.existsByClient_IdClient(id)) {
            throw new IllegalStateException(
                    "Cannot delete a client who has bills.");
        }

        Utilisateur login = findLogin(id);

        if (login != null) {
            // A login that was never activated has never been used: it is
            // removed together with the client (e.g. a client registered by
            // mistake). An activated or disabled login is kept for audit.
            if (login.getStatut() != StatutUtilisateur.PENDING_ACTIVATION) {
                throw new IllegalStateException(
                        "Cannot delete a client who has an online banking login.");
            }

            activationTokenRepository.deleteByUtilisateur_IdUtilisateur(login.getIdUtilisateur());
            passwordResetTokenRepository.deleteByUtilisateur_IdUtilisateur(login.getIdUtilisateur());
            utilisateurRepository.delete(login);
            utilisateurRepository.flush();
        }

        clientRepository.delete(client);
    }

    private Utilisateur findLogin(Integer idClient) {
        return utilisateurRepository.findByClient_IdClient(idClient).orElse(null);
    }

    // Convert Entity to DTO
    private ClientResponse convertToResponse(Client client, Utilisateur login) {

        ClientResponse response = new ClientResponse(
                client.getIdClient(),
                client.getNom(),
                client.getPrenom(),
                client.getEmail(),
                client.getTelephone(),
                client.getAdresse()
        );

        if (login != null) {
            response.setUsername(login.getUsername());
            response.setAccountStatus(login.getStatut());
        }

        return response;
    }
}
