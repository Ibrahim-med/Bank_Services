package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.dto.request.BillRequest;
import com.bankservices.bankservices_api.dto.response.BillResponse;
import com.bankservices.bankservices_api.entity.Bill;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.bankservices.bankservices_api.support.TestFixtures.client;
import static com.bankservices.bankservices_api.support.TestFixtures.utilisateur;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillServiceTest {

    @Mock private BillRepository billRepository;
    @Mock private BillPaymentRepository billPaymentRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private BillService service;
    private Client alice;

    @BeforeEach
    void setUp() {
        service = new BillService(billRepository, billPaymentRepository, clientRepository,
                utilisateurRepository, eventPublisher);
        alice = client(1, "Alice", "Martin", "alice@example.test");
        Utilisateur aliceUser = utilisateur(5, "alice", "CLIENT", alice);
        when(utilisateurRepository.findByUsername("alice")).thenReturn(Optional.of(aliceUser));
        when(clientRepository.findById(1)).thenReturn(Optional.of(alice));
        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> {
            Bill saved = invocation.getArgument(0);
            saved.setIdBill(50);
            return saved;
        });
    }

    private static BillRequest request(String amount, LocalDate issue, LocalDate due) {
        BillRequest request = new BillRequest();
        request.setReference(" WAT-2026-11 ");
        request.setProvider("City Water");
        request.setCategory(BillCategory.WATER);
        request.setAmount(new BigDecimal(amount));
        request.setIssueDate(issue);
        request.setDueDate(due);
        request.setIdClient(1);
        return request;
    }

    private static Bill bill(Integer id, Client owner, String reference, BillCategory category,
                             BillStatus status, LocalDate due) {
        Bill bill = new Bill();
        bill.setIdBill(id);
        bill.setReference(reference);
        bill.setProvider("Provider " + id);
        bill.setCategory(category);
        bill.setAmount(new BigDecimal("10.00"));
        bill.setIssueDate(due.minusDays(30));
        bill.setDueDate(due);
        bill.setStatus(status);
        bill.setClient(owner);
        return bill;
    }

    // ---------------- ADMIN ----------------

    @Test
    void createBill_storesUnpaidBill_andNotifiesTheClient() {
        BillResponse response = service.createBill(
                request("45.30", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)));

        ArgumentCaptor<Bill> captor = ArgumentCaptor.forClass(Bill.class);
        verify(billRepository).save(captor.capture());
        Bill saved = captor.getValue();
        assertThat(saved.getReference()).isEqualTo("WAT-2026-11");
        assertThat(saved.getStatus()).isEqualTo(BillStatus.UNPAID);
        assertThat(saved.getAmount()).isEqualByComparingTo("45.30");
        assertThat(saved.getClient()).isSameAs(alice);

        assertThat(response.idBill()).isEqualTo(50);
        assertThat(response.clientName()).isEqualTo("Alice Martin");
        verify(eventPublisher).publishEvent(any(AccountNotificationEvent.class));
    }

    @Test
    void createBill_dueDateBeforeIssueDate_isRejected() {
        assertThatThrownBy(() -> service.createBill(
                request("45.30", LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("due date");

        verify(billRepository, never()).save(any());
    }

    @Test
    void createBill_zeroAmount_isRejected() {
        assertThatThrownBy(() -> service.createBill(
                request("0.00", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))))
                .isInstanceOf(IllegalArgumentException.class);

        verify(billRepository, never()).save(any());
    }

    @Test
    void createBill_duplicateReference_isRejected() {
        when(billRepository.existsByReferenceIgnoreCase("WAT-2026-11")).thenReturn(true);

        assertThatThrownBy(() -> service.createBill(
                request("45.30", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))))
                .isInstanceOf(IllegalStateException.class);

        verify(billRepository, never()).save(any());
    }

    @Test
    void createBill_unknownClient_isNotFound() {
        BillRequest request = request("45.30", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        request.setIdClient(99);
        when(clientRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createBill(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancelBill_onlyWhenUnpaid() {
        Bill unpaid = bill(1, alice, "A-1", BillCategory.WATER, BillStatus.UNPAID, LocalDate.now());
        Bill paid = bill(2, alice, "A-2", BillCategory.WATER, BillStatus.PAID, LocalDate.now());
        when(billRepository.findByIdForUpdate(1)).thenReturn(Optional.of(unpaid));
        when(billRepository.findByIdForUpdate(2)).thenReturn(Optional.of(paid));

        assertThat(service.cancelBill(1).status()).isEqualTo(BillStatus.CANCELLED);

        assertThatThrownBy(() -> service.cancelBill(2))
                .isInstanceOf(IllegalStateException.class);
        assertThat(paid.getStatus()).isEqualTo(BillStatus.PAID);
    }

    // ---------------- CLIENT ----------------

    @Test
    void clientBills_filtersByStatusCategoryReferenceAndOverdue() {
        LocalDate past = LocalDate.now().minusDays(5);
        LocalDate future = LocalDate.now().plusDays(5);

        when(billRepository.findByClient_IdClientOrderByDueDateDesc(1)).thenReturn(List.of(
                bill(1, alice, "ELEC-001", BillCategory.ELECTRICITY, BillStatus.UNPAID, past),
                bill(2, alice, "ELEC-002", BillCategory.ELECTRICITY, BillStatus.UNPAID, future),
                bill(3, alice, "NET-003", BillCategory.INTERNET, BillStatus.PAID, past),
                bill(4, alice, "TEL-004", BillCategory.TELEPHONE, BillStatus.CANCELLED, past)
        ));

        assertThat(service.getClientBills("alice", null, null, null)).hasSize(4);
        assertThat(service.getClientBills("alice", "unpaid", null, null))
                .extracting(BillResponse::idBill).containsExactly(1, 2);
        assertThat(service.getClientBills("alice", "PAID", null, null))
                .extracting(BillResponse::idBill).containsExactly(3);
        assertThat(service.getClientBills("alice", "OVERDUE", null, null))
                .extracting(BillResponse::idBill).containsExactly(1);
        assertThat(service.getClientBills("alice", null, BillCategory.ELECTRICITY, null))
                .extracting(BillResponse::idBill).containsExactly(1, 2);
        assertThat(service.getClientBills("alice", null, null, "net"))
                .extracting(BillResponse::idBill).containsExactly(3);

        // overdue flag: unpaid + past due only (paid/cancelled bills are never overdue)
        assertThat(service.getClientBills("alice", null, null, null))
                .extracting(BillResponse::overdue).containsExactly(true, false, false, false);
    }

    @Test
    void clientBills_unknownStatus_isRejected() {
        assertThatThrownBy(() -> service.getClientBills("alice", "LATE", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clientBill_ofAnotherClient_isDenied() {
        Client bob = client(2, "Bob", "Durand", "bob@example.test");
        when(billRepository.findById(9)).thenReturn(Optional.of(
                bill(9, bob, "B-9", BillCategory.WATER, BillStatus.UNPAID, LocalDate.now())));

        assertThatThrownBy(() -> service.getClientBill("alice", 9))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void clientBill_unknown_isNotFound() {
        when(billRepository.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getClientBill("alice", 404))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void userWithoutClientProfile_isDenied() {
        when(utilisateurRepository.findByUsername("admin"))
                .thenReturn(Optional.of(utilisateur(1, "admin", "ADMIN", null)));

        assertThatThrownBy(() -> service.getClientBills("admin", null, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }
}
