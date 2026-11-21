
package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.dto.request.TransactionRequest;
import com.bankservices.bankservices_api.dto.response.TransactionResponse;

import com.bankservices.bankservices_api.entity.CompteBancaire;
import com.bankservices.bankservices_api.entity.TransactionBancaire;
import com.bankservices.bankservices_api.entity.Utilisateur;

import com.bankservices.bankservices_api.enums.TypeTransaction;
import com.bankservices.bankservices_api.enums.StatutTransaction;
import com.bankservices.bankservices_api.enums.StatutCompte;

import com.bankservices.bankservices_api.exception.ResourceNotFoundException;
import com.bankservices.bankservices_api.exception.InsufficientBalanceException;

import com.bankservices.bankservices_api.notification.AccountNotificationEvent;

import com.bankservices.bankservices_api.repository.CompteRepository;
import com.bankservices.bankservices_api.repository.TransactionRepository;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TransactionService {

    private final CompteRepository compteRepository;
    private final TransactionRepository transactionRepository;
    private final CompteSecurityService compteSecurityService;
    private final UtilisateurRepository utilisateurRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TransactionService(
            CompteRepository compteRepository,
            TransactionRepository transactionRepository,
            CompteSecurityService compteSecurityService,
            UtilisateurRepository utilisateurRepository,
            ApplicationEventPublisher eventPublisher) {

        this.compteRepository = compteRepository;
        this.transactionRepository = transactionRepository;
        this.compteSecurityService = compteSecurityService;
        this.utilisateurRepository = utilisateurRepository;
        this.eventPublisher = eventPublisher;
    }

    // =====================================
    // MAIN TRANSACTION METHOD
    // =====================================

    @Transactional
    public TransactionResponse executeTransaction(
            TransactionRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Transaction request is required");
        }

        validerMontant(request.getMontant());

        TypeTransaction type = request.getTypeTransaction();

        if (type == null) {
            throw new IllegalArgumentException(
                    "Transaction type is required");
        }

        TransactionBancaire transaction =
                new TransactionBancaire();

        transaction.setTypeTransaction(type);
        transaction.setMontant(request.getMontant());
        transaction.setDescription(request.getDescription());
        transaction.setStatut(StatutTransaction.VALIDEE);

        switch (type) {

            case DEPOT:
                executeDeposit(request, transaction);
                break;

            case RETRAIT:
                executeWithdrawal(request, transaction);
                break;

            case VIREMENT:
                executeTransfer(request, transaction);
                break;

            default:
                throw new IllegalArgumentException(
                        "Unsupported transaction type");
        }

        TransactionBancaire saved =
                transactionRepository.save(transaction);

        // Publish notification events.
        // The listener sends emails AFTER the database commit.
        sendTransactionNotifications(saved);

        return convertToResponse(saved);
    }

    // =====================================
    // DEPOSIT
    // =====================================

    private void executeDeposit(
            TransactionRequest request,
            TransactionBancaire transaction) {

        if (request.getIdCompteSource() != null ||
                request.getIdCompteDestination() == null) {

            throw new IllegalArgumentException(
                    "A deposit requires only a destination account");
        }

        CompteBancaire destination =
                obtenirCompteVerrouille(
                        request.getIdCompteDestination());

        verifierAutorisation(destination);
        verifierCompteActif(destination);

        destination.setSolde(
                destination.getSolde().add(request.getMontant()));

        transaction.setCompteSource(null);
        transaction.setCompteDestination(destination);
    }

    // =====================================
    // WITHDRAWAL
    // =====================================

    private void executeWithdrawal(
            TransactionRequest request,
            TransactionBancaire transaction) {

        if (request.getIdCompteSource() == null ||
                request.getIdCompteDestination() != null) {

            throw new IllegalArgumentException(
                    "A withdrawal requires only a source account");
        }

        CompteBancaire source =
                obtenirCompteVerrouille(
                        request.getIdCompteSource());

        verifierAutorisation(source);
        verifierCompteActif(source);

        BigDecimal newBalance =
                source.getSolde().subtract(request.getMontant());

        checkAvailableBalance(source, newBalance);

        source.setSolde(newBalance);

        transaction.setCompteSource(source);
        transaction.setCompteDestination(null);
    }

    // =====================================
    // TRANSFER
    // =====================================

    private void executeTransfer(
            TransactionRequest request,
            TransactionBancaire transaction) {

        Integer sourceId = request.getIdCompteSource();
        Integer destinationId = request.getIdCompteDestination();

        if (sourceId == null || destinationId == null) {
            throw new IllegalArgumentException(
                    "A transfer requires source and destination accounts");
        }

        if (sourceId.equals(destinationId)) {
            throw new IllegalArgumentException(
                    "Cannot transfer to the same account");
        }

        // Lock accounts in ascending ID order.
        Integer firstId = Math.min(sourceId, destinationId);
        Integer secondId = Math.max(sourceId, destinationId);

        CompteBancaire firstAccount =
                obtenirCompteVerrouille(firstId);

        CompteBancaire secondAccount =
                obtenirCompteVerrouille(secondId);

        CompteBancaire source =
                sourceId.equals(firstId)
                        ? firstAccount : secondAccount;

        CompteBancaire destination =
                destinationId.equals(firstId)
                        ? firstAccount : secondAccount;

        verifierAutorisation(source);
        verifierAutorisation(destination);

        verifierCompteActif(source);
        verifierCompteActif(destination);

        BigDecimal newSourceBalance =
                source.getSolde().subtract(request.getMontant());

        checkAvailableBalance(source, newSourceBalance);

        BigDecimal newDestinationBalance =
                destination.getSolde().add(request.getMontant());

        source.setSolde(newSourceBalance);
        destination.setSolde(newDestinationBalance);

        transaction.setCompteSource(source);
        transaction.setCompteDestination(destination);
    }

    // =====================================
    // TRANSACTION EMAIL NOTIFICATIONS
    // =====================================

    private void sendTransactionNotifications(
            TransactionBancaire transaction) {

        CompteBancaire source = transaction.getCompteSource();
        CompteBancaire destination = transaction.getCompteDestination();

        String amount = transaction.getMontant().toPlainString();

        String description = transaction.getDescription() == null
                ? "No description"
                : transaction.getDescription();

        switch (transaction.getTypeTransaction()) {

            case DEPOT:

                notifyAccountOwner(
                        destination,
                        "BankServices - Deposit successful",
                        "A deposit has been completed successfully.\n\n" +
                                "Account number: " + destination.getNumero() + "\n" +
                                "Amount: " + amount + "\n" +
                                "Transaction type: DEPOSIT\n" +
                                "Description: " + description
                );
                break;

            case RETRAIT:

                notifyAccountOwner(
                        source,
                        "BankServices - Withdrawal successful",
                        "A withdrawal has been completed successfully.\n\n" +
                                "Account number: " + source.getNumero() + "\n" +
                                "Amount: " + amount + "\n" +
                                "Transaction type: WITHDRAWAL\n" +
                                "Description: " + description
                );
                break;

            case VIREMENT:

                // Notify sender
                notifyAccountOwner(
                        source,
                        "BankServices - Transfer sent",
                        "A transfer has been sent successfully.\n\n" +
                                "Source account: " + source.getNumero() + "\n" +
                                "Destination account: " + destination.getNumero() + "\n" +
                                "Amount: " + amount
                );

                // Notify recipient
                notifyAccountOwner(
                        destination,
                        "BankServices - Transfer received",
                        "A transfer has been received successfully.\n\n" +
                                "Source account: " + source.getNumero() + "\n" +
                                "Destination account: " + destination.getNumero() + "\n" +
                                "Amount: " + amount
                );
                break;

            default:
                throw new IllegalArgumentException(
                        "Unsupported transaction type");
        }
    }

    // =====================================
    // NOTIFY ACCOUNT OWNER
    // =====================================

    private void notifyAccountOwner(
            CompteBancaire compte,
            String subject,
            String message) {

        if (compte == null || compte.getClient() == null) {
            return;
        }

        String email = compte.getClient().getEmail();

        if (email == null || email.isBlank()) {
            return;
        }

        eventPublisher.publishEvent(
                new AccountNotificationEvent(
                        email,
                        subject,
                        message
                )
        );
    }

    // =====================================
    // ACCOUNT STATUS VALIDATION
    // =====================================

    private void verifierCompteActif(CompteBancaire compte) {

        if (compte.getStatut() != StatutCompte.ACTIVE) {
            throw new IllegalStateException(
                    "This account is closed and cannot be used for transactions."
            );
        }
    }

    // =====================================
    // ACCOUNT OWNERSHIP AUTHORIZATION
    // =====================================

    private void verifierAutorisation(CompteBancaire compte) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            throw new AccessDeniedException(
                    "Authentication required");
        }

        boolean isAdmin = authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            return;
        }

        boolean isClient = authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_CLIENT"));

        if (!isClient ||
                !compteSecurityService.isOwner(
                        compte.getIdCompte(),
                        authentication.getName())) {

            throw new AccessDeniedException(
                    "You are not authorized to use this account");
        }
    }

    // =====================================
    // BALANCE VALIDATION
    // =====================================

    private void validerMontant(BigDecimal montant) {

        if (montant == null ||
                montant.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Transaction amount must be positive");
        }
    }

    private void checkAvailableBalance(
            CompteBancaire compte,
            BigDecimal newBalance) {

        BigDecimal overdraft =
                compte.getDecouvertAutorise() == null
                        ? BigDecimal.ZERO
                        : compte.getDecouvertAutorise();

        BigDecimal minimumBalance = overdraft.negate();

        if (newBalance.compareTo(minimumBalance) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient available balance.");
        }
    }

    // =====================================
    // GET ALL TRANSACTIONS (ADMIN)
    // =====================================

    @Transactional(readOnly = true)
    public List<TransactionResponse> getAllTransactions() {

        return transactionRepository.findAll()
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // =====================================
    // GET CLIENT TRANSACTION HISTORY
    // =====================================

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByClient(
            Integer idClient) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            throw new AccessDeniedException(
                    "Authentication required");
        }

        Utilisateur utilisateur =
                utilisateurRepository.findByUsername(
                        authentication.getName()
                ).orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Authenticated user not found"));

        if (utilisateur.getClient() == null ||
                !utilisateur.getClient().getIdClient().equals(idClient)) {

            throw new AccessDeniedException(
                    "You are not authorized to view this transaction history");
        }

        return transactionRepository
                .findDistinctByCompteSource_Client_IdClientOrCompteDestination_Client_IdClientOrderByDateTransactionDesc(
                        idClient,
                        idClient
                )
                .stream()
                .map(this::convertToResponse)
                .toList();
    }

    // =====================================
    // GET TRANSACTION BY ID
    // =====================================

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(Integer id) {

        TransactionBancaire transaction =
                transactionRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Transaction not found: " + id));

        boolean hasSource =
                transaction.getCompteSource() != null;

        boolean hasDestination =
                transaction.getCompteDestination() != null;

        if (!hasSource && !hasDestination) {
            throw new AccessDeniedException(
                    "Transaction has no associated account");
        }

        if (hasSource) {
            verifierAutorisation(
                    transaction.getCompteSource());
        }

        if (hasDestination) {
            verifierAutorisation(
                    transaction.getCompteDestination());
        }

        return convertToResponse(transaction);
    }

    // =====================================
    // ENTITY TO RESPONSE DTO
    // =====================================

    private TransactionResponse convertToResponse(
            TransactionBancaire transaction) {

        Integer sourceId =
                transaction.getCompteSource() != null
                        ? transaction.getCompteSource().getIdCompte()
                        : null;

        Integer destinationId =
                transaction.getCompteDestination() != null
                        ? transaction.getCompteDestination().getIdCompte()
                        : null;

        return new TransactionResponse(
                transaction.getIdTransaction(),
                transaction.getTypeTransaction(),
                transaction.getMontant(),
                transaction.getDateTransaction(),
                transaction.getDescription(),
                transaction.getStatut(),
                sourceId,
                destinationId
        );
    }

    // =====================================
    // ACCOUNT LOCKING (SELECT ... FOR UPDATE)
    // =====================================

    private CompteBancaire obtenirCompteVerrouille(Integer id) {

        if (id == null) {
            throw new IllegalArgumentException("Account ID is required");
        }

        return compteRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Account not found: " + id));
    }
}
