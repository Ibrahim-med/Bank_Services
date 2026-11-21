package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.dto.response.BillPaymentResponse;
import com.bankservices.bankservices_api.entity.Bill;
import com.bankservices.bankservices_api.entity.BillPayment;
import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.entity.CompteCourant;
import com.bankservices.bankservices_api.entity.CompteEpargne;
import com.bankservices.bankservices_api.entity.TransactionBancaire;
import com.bankservices.bankservices_api.enums.BillCategory;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.bankservices.bankservices_api.support.TestFixtures.client;
import static com.bankservices.bankservices_api.support.TestFixtures.compteCourant;
import static com.bankservices.bankservices_api.support.TestFixtures.compteEpargne;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests of the bill payment business rules (ownership, status,
 * authoritative amount, funds, debit, history record, receipt).
 * Atomicity, rollback and concurrency against a real database are
 * covered by BillAndActivationIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillPaymentServiceTest {

    @Mock private BillRepository billRepository;
    @Mock private BillPaymentRepository billPaymentRepository;
    @Mock private CompteRepository compteRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private BillService billService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private BillPaymentService service;

    private Client alice;
    private Client bob;
    private CompteCourant aliceCurrent;
    private Bill aliceBill;

    @BeforeEach
    void setUp() {
        service = new BillPaymentService(billRepository, billPaymentRepository, compteRepository,
                transactionRepository, billService, eventPublisher);

        alice = client(1, "Alice", "Martin", "alice@example.test");
        bob = client(2, "Bob", "Durand", "bob@example.test");
        aliceCurrent = compteCourant(11, alice, "500.00", "100.00");
        aliceBill = bill(100, alice, "120.50", BillStatus.UNPAID);

        when(billService.resolveClient("alice")).thenReturn(alice);
        when(billRepository.findByIdForUpdate(100)).thenReturn(Optional.of(aliceBill));
        when(compteRepository.findByIdForUpdate(11)).thenReturn(Optional.of(aliceCurrent));
        when(transactionRepository.save(any(TransactionBancaire.class))).thenAnswer(invocation -> {
            TransactionBancaire saved = invocation.getArgument(0);
            saved.setIdTransaction(900);
            return saved;
        });
        when(billPaymentRepository.save(any(BillPayment.class))).thenAnswer(invocation -> {
            BillPayment saved = invocation.getArgument(0);
            saved.setIdBillPayment(700);
            return saved;
        });
    }

    private static Bill bill(Integer id, Client owner, String amount, BillStatus status) {
        Bill bill = new Bill();
        bill.setIdBill(id);
        bill.setReference("ELEC-2026-" + id);
        bill.setProvider("City Power");
        bill.setCategory(BillCategory.ELECTRICITY);
        bill.setAmount(new BigDecimal(amount));
        bill.setIssueDate(LocalDate.of(2026, 11, 1));
        bill.setDueDate(LocalDate.of(2026, 11, 30));
        bill.setStatus(status);
        bill.setClient(owner);
        return bill;
    }

    private void assertNothingWasPaid(String expectedBalance) {
        assertThat(aliceCurrent.getSolde()).isEqualByComparingTo(expectedBalance);
        assertThat(aliceBill.getPaidAt()).isNull();
        verify(transactionRepository, never()).save(any());
        verify(billPaymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Nested
    @DisplayName("Successful payment")
    class Success {

        @Test
        void debitsAccount_recordsTransaction_marksBillPaid_andReturnsMaskedReceipt() {
            BillPaymentResponse receipt = service.payBill("alice", 100, 11);

            // Balance: 500.00 - 120.50 (amount read from the bill)
            assertThat(aliceCurrent.getSolde()).isEqualByComparingTo("379.50");

            // Transaction history
            ArgumentCaptor<TransactionBancaire> txCaptor = ArgumentCaptor.forClass(TransactionBancaire.class);
            verify(transactionRepository).save(txCaptor.capture());
            TransactionBancaire tx = txCaptor.getValue();
            assertThat(tx.getTypeTransaction()).isEqualTo(TypeTransaction.PAIEMENT_FACTURE);
            assertThat(tx.getMontant()).isEqualByComparingTo("120.50");
            assertThat(tx.getStatut()).isEqualTo(StatutTransaction.VALIDEE);
            assertThat(tx.getCompteSource()).isSameAs(aliceCurrent);
            assertThat(tx.getCompteDestination()).isNull();
            assertThat(tx.getDescription()).contains("ELEC-2026-100");

            // Bill
            assertThat(aliceBill.getStatus()).isEqualTo(BillStatus.PAID);
            assertThat(aliceBill.getPaidAt()).isNotNull();

            // Payment record
            ArgumentCaptor<BillPayment> paymentCaptor = ArgumentCaptor.forClass(BillPayment.class);
            verify(billPaymentRepository).save(paymentCaptor.capture());
            BillPayment payment = paymentCaptor.getValue();
            assertThat(payment.getBill()).isSameAs(aliceBill);
            assertThat(payment.getCompte()).isSameAs(aliceCurrent);
            assertThat(payment.getTransaction()).isSameAs(tx);
            assertThat(payment.getAmount()).isEqualByComparingTo("120.50");
            assertThat(payment.getReference()).matches("PAY-\\d{8}-[A-Z0-9]{8}");

            // Receipt
            assertThat(receipt.idBillPayment()).isEqualTo(700);
            assertThat(receipt.paymentReference()).isEqualTo(payment.getReference());
            assertThat(receipt.status()).isEqualTo(BillPaymentStatus.COMPLETED);
            assertThat(receipt.amount()).isEqualByComparingTo("120.50");
            assertThat(receipt.billReference()).isEqualTo("ELEC-2026-100");
            assertThat(receipt.provider()).isEqualTo("City Power");
            assertThat(receipt.category()).isEqualTo(BillCategory.ELECTRICITY);
            assertThat(receipt.accountNumberMasked()).isEqualTo("****C-11").doesNotContain("TEST-ACC");
            assertThat(receipt.accountType()).isEqualTo(TypeCompte.COURANT);
            assertThat(receipt.idTransaction()).isEqualTo(900);

            // Confirmation e-mail (sent after commit by the listener)
            verify(eventPublisher).publishEvent(any(AccountNotificationEvent.class));
        }

        @Test
        void overdraftWithinTheAuthorizedLimit_isAllowed() {
            aliceCurrent.setSolde(new BigDecimal("50.00")); // 50 - 120.50 = -70.50 >= -100

            service.payBill("alice", 100, 11);

            assertThat(aliceCurrent.getSolde()).isEqualByComparingTo("-70.50");
            assertThat(aliceBill.getStatus()).isEqualTo(BillStatus.PAID);
        }

        @Test
        void exactBalance_isAllowed() {
            CompteEpargne savings = compteEpargne(12, alice, "120.50", "2.00");
            when(compteRepository.findByIdForUpdate(12)).thenReturn(Optional.of(savings));

            BillPaymentResponse receipt = service.payBill("alice", 100, 12);

            assertThat(savings.getSolde()).isEqualByComparingTo("0.00");
            assertThat(receipt.accountType()).isEqualTo(TypeCompte.EPARGNE);
        }
    }

    @Nested
    @DisplayName("Rejected payments leave everything unchanged")
    class Rejections {

        @Test
        void insufficientFunds() {
            aliceCurrent.setSolde(new BigDecimal("20.00")); // 20 - 120.50 = -100.50 < -100

            assertThatThrownBy(() -> service.payBill("alice", 100, 11))
                    .isInstanceOf(InsufficientBalanceException.class);

            assertNothingWasPaid("20.00");
            assertThat(aliceBill.getStatus()).isEqualTo(BillStatus.UNPAID);
        }

        @Test
        void savingsAccount_hasNoOverdraft() {
            CompteEpargne savings = compteEpargne(12, alice, "100.00", "2.00");
            when(compteRepository.findByIdForUpdate(12)).thenReturn(Optional.of(savings));

            assertThatThrownBy(() -> service.payBill("alice", 100, 12))
                    .isInstanceOf(InsufficientBalanceException.class);

            assertThat(savings.getSolde()).isEqualByComparingTo("100.00");
            assertThat(aliceBill.getStatus()).isEqualTo(BillStatus.UNPAID);
        }

        @Test
        void alreadyPaidBill() {
            aliceBill.setStatus(BillStatus.PAID);

            assertThatThrownBy(() -> service.payBill("alice", 100, 11))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been paid");

            assertNothingWasPaid("500.00");
            verify(compteRepository, never()).findByIdForUpdate(any());
        }

        @Test
        void cancelledBill() {
            aliceBill.setStatus(BillStatus.CANCELLED);

            assertThatThrownBy(() -> service.payBill("alice", 100, 11))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cancelled");

            assertNothingWasPaid("500.00");
        }

        @Test
        void unknownBill() {
            when(billRepository.findByIdForUpdate(404)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.payBill("alice", 404, 11))
                    .isInstanceOf(ResourceNotFoundException.class);

            assertNothingWasPaid("500.00");
        }

        @Test
        void billOfAnotherClient() {
            Bill bobBill = bill(200, bob, "30.00", BillStatus.UNPAID);
            when(billRepository.findByIdForUpdate(200)).thenReturn(Optional.of(bobBill));

            assertThatThrownBy(() -> service.payBill("alice", 200, 11))
                    .isInstanceOf(AccessDeniedException.class);

            assertThat(bobBill.getStatus()).isEqualTo(BillStatus.UNPAID);
            assertNothingWasPaid("500.00");
        }

        @Test
        void accountOfAnotherClient() {
            CompteCourant bobAccount = compteCourant(21, bob, "1000.00", "0.00");
            when(compteRepository.findByIdForUpdate(21)).thenReturn(Optional.of(bobAccount));

            assertThatThrownBy(() -> service.payBill("alice", 100, 21))
                    .isInstanceOf(AccessDeniedException.class);

            assertThat(bobAccount.getSolde()).isEqualByComparingTo("1000.00");
            assertNothingWasPaid("500.00");
            assertThat(aliceBill.getStatus()).isEqualTo(BillStatus.UNPAID);
        }

        @Test
        void unknownAccount() {
            when(compteRepository.findByIdForUpdate(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.payBill("alice", 100, 99))
                    .isInstanceOf(ResourceNotFoundException.class);

            assertNothingWasPaid("500.00");
        }

        @Test
        void closedAccount() {
            aliceCurrent.setStatut(StatutCompte.CLOSED);

            assertThatThrownBy(() -> service.payBill("alice", 100, 11))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("closed");

            assertNothingWasPaid("500.00");
        }

        @Test
        void zeroAmountStoredOnTheBill() {
            aliceBill.setAmount(BigDecimal.ZERO);

            assertThatThrownBy(() -> service.payBill("alice", 100, 11))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid bill amount");

            assertNothingWasPaid("500.00");
        }

        @Test
        void negativeAmountStoredOnTheBill() {
            aliceBill.setAmount(new BigDecimal("-10.00"));

            assertThatThrownBy(() -> service.payBill("alice", 100, 11))
                    .isInstanceOf(IllegalArgumentException.class);

            assertNothingWasPaid("500.00");
        }

        @Test
        void missingAccountId() {
            assertThatThrownBy(() -> service.payBill("alice", 100, null))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(billRepository, compteRepository);
        }

        @Test
        void secondAttemptOnTheSameBill_isRejected() {
            service.payBill("alice", 100, 11);

            assertThatThrownBy(() -> service.payBill("alice", 100, 11))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been paid");

            // Debited exactly once
            assertThat(aliceCurrent.getSolde()).isEqualByComparingTo("379.50");
            verify(transactionRepository, times(1)).save(any());
            verify(billPaymentRepository, times(1)).save(any());
        }
    }

    @Nested
    @DisplayName("History and receipts")
    class History {

        private BillPayment payment(Integer id, Bill bill) {
            TransactionBancaire tx = new TransactionBancaire();
            tx.setIdTransaction(id + 1000);
            BillPayment payment = new BillPayment();
            payment.setIdBillPayment(id);
            payment.setReference("PAY-20261119-ABCDEFGH");
            payment.setBill(bill);
            payment.setCompte(aliceCurrent);
            payment.setTransaction(tx);
            payment.setAmount(bill.getAmount());
            payment.setPaymentDate(LocalDateTime.of(2026, 11, 19, 10, 0));
            return payment;
        }

        @Test
        void paymentHistory_isLimitedToTheAuthenticatedClient() {
            when(billPaymentRepository.findByBill_Client_IdClientOrderByPaymentDateDesc(1))
                    .thenReturn(List.of(payment(1, aliceBill)));

            List<BillPaymentResponse> history = service.getPayments("alice");

            assertThat(history).hasSize(1);
            assertThat(history.getFirst().billReference()).isEqualTo("ELEC-2026-100");
            verify(billPaymentRepository).findByBill_Client_IdClientOrderByPaymentDateDesc(1);
        }

        @Test
        void receipt_ofOwnPayment() {
            when(billPaymentRepository.findById(1)).thenReturn(Optional.of(payment(1, aliceBill)));

            BillPaymentResponse receipt = service.getReceipt("alice", 1);

            assertThat(receipt.paymentReference()).isEqualTo("PAY-20261119-ABCDEFGH");
            assertThat(receipt.clientName()).isEqualTo("Alice Martin");
        }

        @Test
        void receipt_ofAnotherClientsPayment_isDenied() {
            when(billPaymentRepository.findById(2))
                    .thenReturn(Optional.of(payment(2, bill(200, bob, "30.00", BillStatus.PAID))));

            assertThatThrownBy(() -> service.getReceipt("alice", 2))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void receipt_unknown_isNotFound() {
            when(billPaymentRepository.findById(anyInt())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getReceipt("alice", 5))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    void accountNumbersAreMasked() {
        assertThat(BillPaymentService.maskAccountNumber("FR7612345678901234")).isEqualTo("****1234");
        assertThat(BillPaymentService.maskAccountNumber("123")).isEqualTo("****123");
        assertThat(BillPaymentService.maskAccountNumber(null)).isEqualTo("****");
    }

    @Test
    void unknownUser_isDenied() {
        when(billService.resolveClient(anyString())).thenThrow(new AccessDeniedException("no client"));

        assertThatThrownBy(() -> service.payBill("ghost", 100, 11))
                .isInstanceOf(AccessDeniedException.class);

        assertNothingWasPaid("500.00");
    }
}
