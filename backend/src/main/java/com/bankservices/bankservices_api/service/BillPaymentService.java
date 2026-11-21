
package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.dto.response.BillPaymentResponse;
import com.bankservices.bankservices_api.entity.Bill;
import com.bankservices.bankservices_api.entity.BillPayment;
import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.entity.CompteBancaire;
import com.bankservices.bankservices_api.entity.CompteCourant;
import com.bankservices.bankservices_api.entity.TransactionBancaire;
import com.bankservices.bankservices_api.enums.BillPaymentStatus;
import com.bankservices.bankservices_api.enums.BillStatus;
import com.bankservices.bankservices_api.enums.StatutCompte;
import com.bankservices.bankservices_api.enums.StatutTransaction;
import com.bankservices.bankservices_api.enums.TypeCompte;
import com.bankservices.bankservices_api.enums.TypeTransaction;
import com.bankservices.bankservices_api.exception.InsufficientBalanceException;
import com.bankservices.bankservices_api.exception.ResourceNotFoundException;
import com.bankservices.bankservices_api.notification.AccountNotificationEvent;
import com.bankservices.bankservices_api.repository.BillPaymentRepository;
import com.bankservices.bankservices_api.repository.BillRepository;
import com.bankservices.bankservices_api.repository.CompteRepository;
import com.bankservices.bankservices_api.repository.TransactionRepository;

import org.hibernate.Hibernate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Payment of a bill from one of the client's own bank accounts.
 *
 * Financial guarantees:
 *  - single database transaction (@Transactional): debit, transaction record,
 *    bill status and payment record are committed together or not at all;
 *  - the amount is ALWAYS read from the bill in the database (never from the client);
 *  - the bill row is locked (SELECT ... FOR UPDATE) before any check, so two
 *    concurrent attempts to pay the same bill are serialized and the second
 *    one sees status PAID and is rejected;
 *  - the account row is locked before reading its balance (same lock as
 *    deposits / withdrawals / transfers);
 *  - lock order is always bill -> account, transfers only lock accounts,
 *    so no lock cycle (deadlock) can be formed;
 *  - UNIQUE(bill_payment.id_bill) is a final database-level guard against
 *    a double payment.
 */
@Service
public class BillPaymentService {

    private static final DateTimeFormatter REFERENCE_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BillRepository billRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final CompteRepository compteRepository;
    private final TransactionRepository transactionRepository;
    private final BillService billService;
    private final ApplicationEventPublisher eventPublisher;

    public BillPaymentService(
            BillRepository billRepository,
            BillPaymentRepository billPaymentRepository,
            CompteRepository compteRepository,
            TransactionRepository transactionRepository,
            BillService billService,
            ApplicationEventPublisher eventPublisher) {

        this.billRepository = billRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.compteRepository = compteRepository;
        this.transactionRepository = transactionRepository;
        this.billService = billService;
        this.eventPublisher = eventPublisher;
    }

    // =====================================
    // PAY A BILL
    // =====================================

    @Transactional
    public BillPaymentResponse payBill(String username, Integer idBill, Integer idCompte) {

        if (idBill == null) {
            throw new IllegalArgumentException("Bill ID is required");
        }

        if (idCompte == null) {
            throw new IllegalArgumentException("Bank account is required");
        }

        // 1. Authenticated client
        Client client = billService.resolveClient(username);

        // 2-3. Bill exists (locked) and belongs to the client
        Bill bill = billRepository.findByIdForUpdate(idBill)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + idBill));

        if (!bill.getClient().getIdClient().equals(client.getIdClient())) {
            throw new AccessDeniedException("You are not authorized to pay this bill");
        }

        // 4. Bill is unpaid
        if (bill.getStatus() == BillStatus.PAID) {
            throw new IllegalStateException("This bill has already been paid.");
        }

        if (bill.getStatus() != BillStatus.UNPAID) {
            throw new IllegalStateException("This bill has been cancelled and cannot be paid.");
        }

        // 6. Authoritative amount from the database
        BigDecimal amount = bill.getAmount();

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid bill amount. Please contact BankServices.");
        }

        // 5. Account exists (locked), belongs to the client and is active
        CompteBancaire compte = compteRepository.findByIdForUpdate(idCompte)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + idCompte));

        if (compte.getClient() == null
                || !compte.getClient().getIdClient().equals(client.getIdClient())) {
            throw new AccessDeniedException("You are not authorized to use this account");
        }

        if (compte.getStatut() != StatutCompte.ACTIVE) {
            throw new IllegalStateException(
                    "This account is closed and cannot be used for transactions.");
        }

        // 7. Sufficient funds (same rule as withdrawals: overdraft allowed up to its limit)
        BigDecimal newBalance = compte.getSolde().subtract(amount);
        BigDecimal overdraft = compte.getDecouvertAutorise() == null
                ? BigDecimal.ZERO
                : compte.getDecouvertAutorise();

        if (newBalance.compareTo(overdraft.negate()) < 0) {
            throw new InsufficientBalanceException("Insufficient available balance.");
        }

        // 8. Debit
        compte.setSolde(newBalance);

        // 9. Transaction history record
        TransactionBancaire transaction = new TransactionBancaire();
        transaction.setTypeTransaction(TypeTransaction.PAIEMENT_FACTURE);
        transaction.setMontant(amount);
        transaction.setStatut(StatutTransaction.VALIDEE);
        transaction.setDescription(truncate(
                "Bill payment " + bill.getReference() + " - " + bill.getProvider(), 255));
        transaction.setCompteSource(compte);
        transaction.setCompteDestination(null);

        TransactionBancaire savedTransaction = transactionRepository.save(transaction);

        // 10. Bill marked as paid
        LocalDateTime now = LocalDateTime.now();
        bill.setStatus(BillStatus.PAID);
        bill.setPaidAt(now);

        // Payment record (receipt)
        BillPayment payment = new BillPayment();
        payment.setReference(generatePaymentReference(now));
        payment.setBill(bill);
        payment.setCompte(compte);
        payment.setTransaction(savedTransaction);
        payment.setAmount(amount);
        payment.setPaymentDate(now);
        payment.setStatus(BillPaymentStatus.COMPLETED);

        BillPayment savedPayment = billPaymentRepository.save(payment);

        // Confirmation e-mail, sent only after a successful commit
        eventPublisher.publishEvent(new AccountNotificationEvent(
                client.getEmail(),
                "BankServices - Bill payment confirmation",
                "Hello " + client.getPrenom() + ",\n\n"
                        + "Your bill has been paid successfully.\n\n"
                        + "Payment reference: " + savedPayment.getReference() + "\n"
                        + "Bill reference: " + bill.getReference() + "\n"
                        + "Provider: " + bill.getProvider() + "\n"
                        + "Amount: " + amount.toPlainString() + "\n"
                        + "Account: " + maskAccountNumber(compte.getNumero()) + "\n\n"
                        + "Thank you for choosing BankServices."
        ));

        // 11. Confirmation / receipt
        return toResponse(savedPayment);
    }

    // =====================================
    // PAYMENT HISTORY
    // =====================================

    @Transactional(readOnly = true)
    public List<BillPaymentResponse> getPayments(String username) {

        Client client = billService.resolveClient(username);

        return billPaymentRepository
                .findByBill_Client_IdClientOrderByPaymentDateDesc(client.getIdClient())
                .stream()
                .map(BillPaymentService::toResponse)
                .toList();
    }

    // =====================================
    // RECEIPT
    // =====================================

    @Transactional(readOnly = true)
    public BillPaymentResponse getReceipt(String username, Integer idBillPayment) {

        Client client = billService.resolveClient(username);

        BillPayment payment = billPaymentRepository.findById(idBillPayment)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found: " + idBillPayment));

        if (!payment.getBill().getClient().getIdClient().equals(client.getIdClient())) {
            throw new AccessDeniedException("You are not authorized to view this payment");
        }

        return toResponse(payment);
    }

    // =====================================
    // HELPERS
    // =====================================

    private String generatePaymentReference(LocalDateTime now) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String reference = "PAY-" + now.format(REFERENCE_DATE) + "-"
                    + SecureTokens.randomAlphanumeric(8);

            if (!billPaymentRepository.existsByReference(reference)) {
                return reference;
            }
        }
        throw new IllegalStateException("Could not generate a unique payment reference.");
    }

    static String maskAccountNumber(String numero) {
        if (numero == null || numero.isBlank()) {
            return "****";
        }
        String visible = numero.length() <= 4 ? numero : numero.substring(numero.length() - 4);
        return "****" + visible;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    static BillPaymentResponse toResponse(BillPayment payment) {

        Bill bill = payment.getBill();
        // Lazy associations are Hibernate proxies of the abstract base class:
        // unproxy so that the COURANT / EPARGNE subtype check is reliable.
        CompteBancaire compte = Hibernate.unproxy(payment.getCompte(), CompteBancaire.class);
        Client client = bill.getClient();

        return new BillPaymentResponse(
                payment.getIdBillPayment(),
                payment.getReference(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getPaymentDate(),
                bill.getIdBill(),
                bill.getReference(),
                bill.getProvider(),
                bill.getCategory(),
                bill.getIssueDate(),
                bill.getDueDate(),
                compte.getIdCompte(),
                maskAccountNumber(compte.getNumero()),
                compte instanceof CompteCourant ? TypeCompte.COURANT : TypeCompte.EPARGNE,
                payment.getTransaction().getIdTransaction(),
                (client.getPrenom() + " " + client.getNom()).trim()
        );
    }
}
