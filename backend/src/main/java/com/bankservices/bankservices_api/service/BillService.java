
package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.dto.request.BillRequest;
import com.bankservices.bankservices_api.dto.response.BillResponse;
import com.bankservices.bankservices_api.entity.Bill;
import com.bankservices.bankservices_api.entity.BillPayment;
import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.enums.BillCategory;
import com.bankservices.bankservices_api.enums.BillStatus;
import com.bankservices.bankservices_api.exception.ResourceNotFoundException;
import com.bankservices.bankservices_api.notification.AccountNotificationEvent;
import com.bankservices.bankservices_api.repository.BillPaymentRepository;
import com.bankservices.bankservices_api.repository.BillRepository;
import com.bankservices.bankservices_api.repository.ClientRepository;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bills: issued and cancelled by administrators, consulted by clients.
 * Payment is handled by BillPaymentService.
 */
@Service
public class BillService {

    private final BillRepository billRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final ClientRepository clientRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ApplicationEventPublisher eventPublisher;

    public BillService(
            BillRepository billRepository,
            BillPaymentRepository billPaymentRepository,
            ClientRepository clientRepository,
            UtilisateurRepository utilisateurRepository,
            ApplicationEventPublisher eventPublisher) {

        this.billRepository = billRepository;
        this.billPaymentRepository = billPaymentRepository;
        this.clientRepository = clientRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.eventPublisher = eventPublisher;
    }

    // =====================================
    // ADMIN: ISSUE A BILL
    // =====================================

    @Transactional
    public BillResponse createBill(BillRequest request) {

        Client client = clientRepository.findById(request.getIdClient())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found: " + request.getIdClient()));

        BigDecimal amount = request.getAmount();

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Bill amount must be greater than zero.");
        }

        if (request.getDueDate().isBefore(request.getIssueDate())) {
            throw new IllegalArgumentException("The due date cannot be before the issue date.");
        }

        String reference = request.getReference().trim();

        if (billRepository.existsByReferenceIgnoreCase(reference)) {
            throw new IllegalStateException("A bill with this reference already exists.");
        }

        Bill bill = new Bill();
        bill.setReference(reference);
        bill.setProvider(request.getProvider().trim());
        bill.setCategory(request.getCategory());
        bill.setAmount(amount);
        bill.setIssueDate(request.getIssueDate());
        bill.setDueDate(request.getDueDate());
        bill.setStatus(BillStatus.UNPAID);
        bill.setClient(client);

        Bill saved = billRepository.save(bill);

        eventPublisher.publishEvent(new AccountNotificationEvent(
                client.getEmail(),
                "BankServices - New bill available",
                "Hello " + client.getPrenom() + ",\n\n"
                        + "A new bill is available in your BankServices space.\n\n"
                        + "Reference: " + saved.getReference() + "\n"
                        + "Provider: " + saved.getProvider() + "\n"
                        + "Amount: " + saved.getAmount().toPlainString() + "\n"
                        + "Due date: " + saved.getDueDate() + "\n\n"
                        + "You can pay it from the Bills page of your online banking."
        ));

        return toResponse(saved, null);
    }

    // =====================================
    // ADMIN: LIST ALL BILLS
    // =====================================

    @Transactional(readOnly = true)
    public List<BillResponse> getAllBills() {

        Map<Integer, BillPayment> paymentsByBill = billPaymentRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        payment -> payment.getBill().getIdBill(),
                        Function.identity()));

        return billRepository.findAllByOrderByIssueDateDescIdBillDesc()
                .stream()
                .map(bill -> toResponse(bill, paymentsByBill.get(bill.getIdBill())))
                .toList();
    }

    // =====================================
    // ADMIN: CANCEL AN UNPAID BILL
    // =====================================

    @Transactional
    public BillResponse cancelBill(Integer idBill) {

        // Locked: a cancellation cannot interleave with a payment of the same bill.
        Bill bill = billRepository.findByIdForUpdate(idBill)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + idBill));

        if (bill.getStatus() != BillStatus.UNPAID) {
            throw new IllegalStateException("Only unpaid bills can be cancelled.");
        }

        bill.setStatus(BillStatus.CANCELLED);

        return toResponse(bill, null);
    }

    // =====================================
    // CLIENT: OWN BILLS (optional filters)
    //   status:    UNPAID | PAID | CANCELLED | OVERDUE (unpaid and past due date)
    //   category:  ELECTRICITY | WATER | INTERNET | TELEPHONE | OTHER
    //   reference: case-insensitive "contains" search
    // =====================================

    @Transactional(readOnly = true)
    public List<BillResponse> getClientBills(
            String username, String status, BillCategory category, String reference) {

        Client client = resolveClient(username);

        String statusFilter = status == null || status.isBlank()
                ? null
                : status.trim().toUpperCase(Locale.ROOT);

        if (statusFilter != null && !statusFilter.equals("OVERDUE")) {
            try {
                BillStatus.valueOf(statusFilter);
            } catch (IllegalArgumentException exception) {
                // Fixed message: the submitted value is not reflected back.
                throw new IllegalArgumentException("Unknown bill status.");
            }
        }

        String referenceFilter = reference == null || reference.isBlank()
                ? null
                : reference.trim().toUpperCase(Locale.ROOT);

        Map<Integer, BillPayment> paymentsByBill = billPaymentRepository
                .findByBill_Client_IdClientOrderByPaymentDateDesc(client.getIdClient())
                .stream()
                .collect(Collectors.toMap(
                        payment -> payment.getBill().getIdBill(),
                        Function.identity()));

        return billRepository.findByClient_IdClientOrderByDueDateDesc(client.getIdClient())
                .stream()
                .filter(bill -> category == null || bill.getCategory() == category)
                .filter(bill -> referenceFilter == null
                        || bill.getReference().toUpperCase(Locale.ROOT).contains(referenceFilter))
                .filter(bill -> statusFilter == null
                        || (statusFilter.equals("OVERDUE")
                                ? isOverdue(bill)
                                : bill.getStatus().name().equals(statusFilter)))
                .map(bill -> toResponse(bill, paymentsByBill.get(bill.getIdBill())))
                .toList();
    }

    // =====================================
    // CLIENT: ONE OF THEIR BILLS
    // =====================================

    @Transactional(readOnly = true)
    public BillResponse getClientBill(String username, Integer idBill) {

        Client client = resolveClient(username);

        Bill bill = billRepository.findById(idBill)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + idBill));

        if (!bill.getClient().getIdClient().equals(client.getIdClient())) {
            throw new AccessDeniedException("You are not authorized to view this bill");
        }

        return toResponse(bill, billPaymentRepository.findByBill_IdBill(idBill).orElse(null));
    }

    // =====================================
    // HELPERS
    // =====================================

    Client resolveClient(String username) {

        Utilisateur utilisateur = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found"));

        if (utilisateur.getClient() == null) {
            throw new AccessDeniedException("No client profile is linked to this user");
        }

        return utilisateur.getClient();
    }

    static boolean isOverdue(Bill bill) {
        return bill.getStatus() == BillStatus.UNPAID
                && bill.getDueDate() != null
                && bill.getDueDate().isBefore(LocalDate.now());
    }

    static BillResponse toResponse(Bill bill, BillPayment payment) {

        Client client = bill.getClient();

        return new BillResponse(
                bill.getIdBill(),
                bill.getReference(),
                bill.getProvider(),
                bill.getCategory(),
                bill.getAmount(),
                bill.getIssueDate(),
                bill.getDueDate(),
                bill.getStatus(),
                isOverdue(bill),
                client.getIdClient(),
                (client.getPrenom() + " " + client.getNom()).trim(),
                bill.getPaidAt(),
                payment != null ? payment.getIdBillPayment() : null,
                payment != null ? payment.getReference() : null
        );
    }
}
