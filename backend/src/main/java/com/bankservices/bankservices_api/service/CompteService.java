
package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.dto.request.CompteRequest;
import com.bankservices.bankservices_api.dto.response.CompteResponse;

import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.entity.CompteBancaire;
import com.bankservices.bankservices_api.entity.CompteCourant;
import com.bankservices.bankservices_api.entity.CompteEpargne;

import com.bankservices.bankservices_api.enums.TypeCompte;
import com.bankservices.bankservices_api.enums.StatutCompte;

import com.bankservices.bankservices_api.exception.ResourceNotFoundException;

import com.bankservices.bankservices_api.notification.AccountNotificationEvent;

import com.bankservices.bankservices_api.repository.ClientRepository;
import com.bankservices.bankservices_api.repository.CompteRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CompteService {

    private final CompteRepository compteRepository;
    private final ClientRepository clientRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CompteService(
            CompteRepository compteRepository,
            ClientRepository clientRepository,
            ApplicationEventPublisher eventPublisher) {

        this.compteRepository = compteRepository;
        this.clientRepository = clientRepository;
        this.eventPublisher = eventPublisher;
    }

    // =====================================
    // CREATE A BANK ACCOUNT
    // =====================================

    @Transactional
    public CompteResponse createCompte(CompteRequest request) {

        Client client = clientRepository.findById(request.getIdClient())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Client not found: " + request.getIdClient()));

        CompteBancaire compte;

        if (request.getTypeCompte() == TypeCompte.COURANT) {

            CompteCourant courant = new CompteCourant();

            BigDecimal decouvert =
                    request.getDecouvertAutorise() != null
                            ? request.getDecouvertAutorise()
                            : BigDecimal.ZERO;

            if (decouvert.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "Overdraft cannot be negative");
            }

            courant.setDecouvertAutorise(decouvert);
            compte = courant;

        } else if (request.getTypeCompte() == TypeCompte.EPARGNE) {

            CompteEpargne epargne = new CompteEpargne();

            BigDecimal taux =
                    request.getTauxInteret() != null
                            ? request.getTauxInteret()
                            : BigDecimal.ZERO;

            if (taux.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "Interest rate cannot be negative");
            }

            epargne.setTauxInteret(taux);
            compte = epargne;

        } else {
            throw new IllegalArgumentException("Invalid account type");
        }

        compte.setNumero(request.getNumero());
        compte.setSolde(BigDecimal.ZERO);
        compte.setStatut(StatutCompte.ACTIVE);
        compte.setClient(client);

        client.getComptes().add(compte);

        CompteBancaire savedCompte = compteRepository.save(compte);

        publishAccountNotification(
                savedCompte,
                "BankServices - New account created",
                "Hello " + client.getPrenom() + ",\n\n"
                        + "A new bank account has been created for you.\n\n"
                        + "Account number: " + savedCompte.getNumero() + "\n"
                        + "Account type: " + getAccountType(savedCompte) + "\n"
                        + "Initial balance: " + savedCompte.getSolde() + "\n"
                        + "Status: ACTIVE\n\n"
                        + "Thank you for choosing BankServices."
        );

        return convertToResponse(savedCompte);
    }

    // =====================================
    // GET ALL ACCOUNTS
    // =====================================

    @Transactional(readOnly = true)
    public List<CompteResponse> getAllComptes() {

        return compteRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // =====================================
    // GET ACCOUNT BY ID
    // =====================================

    @Transactional(readOnly = true)
    public CompteResponse getCompteById(Integer id) {

        CompteBancaire compte = compteRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Account not found: " + id));

        return convertToResponse(compte);
    }

    // =====================================
    // GET ACCOUNTS BY CLIENT
    // =====================================

    @Transactional(readOnly = true)
    public List<CompteResponse> getComptesByClient(Integer idClient) {

        Client client = clientRepository.findById(idClient)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Client not found: " + idClient));

        return client.getComptes()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // =====================================
    // CLOSE BANK ACCOUNT
    // =====================================

    @Transactional
    public CompteResponse closeCompte(Integer id) {

        if (id == null) {
            throw new IllegalArgumentException("Account ID is required");
        }

        CompteBancaire compte =
                compteRepository.findByIdForUpdate(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Account not found: " + id));

        if (compte.getStatut() == StatutCompte.CLOSED) {
            throw new IllegalStateException(
                    "This account is already closed");
        }

        if (compte.getSolde() == null ||
                compte.getSolde().compareTo(BigDecimal.ZERO) != 0) {

            throw new IllegalStateException(
                    "Account cannot be closed because its balance is not zero");
        }

        compte.setStatut(StatutCompte.CLOSED);

        publishAccountNotification(
                compte,
                "BankServices - Account closed",
                "Hello " + compte.getClient().getPrenom() + ",\n\n"
                        + "Your bank account has been successfully closed.\n\n"
                        + "Account number: " + compte.getNumero() + "\n"
                        + "Account type: " + getAccountType(compte) + "\n"
                        + "Status: CLOSED\n\n"
                        + "If you have questions, please contact BankServices."
        );

        return convertToResponse(compte);
    }

    // =====================================
    // REOPEN BANK ACCOUNT
    // =====================================

    @Transactional
    public CompteResponse reopenCompte(Integer id) {

        if (id == null) {
            throw new IllegalArgumentException("Account ID is required");
        }

        CompteBancaire compte =
                compteRepository.findByIdForUpdate(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Account not found: " + id));

        if (compte.getStatut() == StatutCompte.ACTIVE) {
            throw new IllegalStateException(
                    "This account is already active");
        }

        compte.setStatut(StatutCompte.ACTIVE);

        publishAccountNotification(
                compte,
                "BankServices - Account reopened",
                "Hello " + compte.getClient().getPrenom() + ",\n\n"
                        + "Your bank account has been successfully reopened.\n\n"
                        + "Account number: " + compte.getNumero() + "\n"
                        + "Account type: " + getAccountType(compte) + "\n"
                        + "Status: ACTIVE\n\n"
                        + "Thank you for choosing BankServices."
        );

        return convertToResponse(compte);
    }

    // =====================================
    // PUBLISH ACCOUNT NOTIFICATION
    // =====================================

    private void publishAccountNotification(
            CompteBancaire compte,
            String subject,
            String message) {

        eventPublisher.publishEvent(
                new AccountNotificationEvent(
                        compte.getClient().getEmail(),
                        subject,
                        message
                )
        );
    }

    // =====================================
    // GET ACCOUNT TYPE
    // =====================================

    private String getAccountType(CompteBancaire compte) {

        return compte instanceof CompteCourant
                ? "COURANT"
                : "EPARGNE";
    }

    // =====================================
    // CONVERT ENTITY TO RESPONSE DTO
    // =====================================

    private CompteResponse convertToResponse(CompteBancaire compte) {

        return new CompteResponse(
                compte.getIdCompte(),
                compte.getNumero(),
                compte.getSolde(),
                compte instanceof CompteCourant
                        ? TypeCompte.COURANT
                        : TypeCompte.EPARGNE,
                compte.getDecouvertAutorise(),
                compte.getTauxInteret(),
                compte.getDateCreation(),
                compte.getClient().getIdClient(),
                compte.getStatut()
        );
    }
}
