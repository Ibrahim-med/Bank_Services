package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.dto.request.TransactionRequest;
import com.bankservices.bankservices_api.dto.response.TransactionResponse;
import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.entity.CompteBancaire;
import com.bankservices.bankservices_api.entity.CompteCourant;
import com.bankservices.bankservices_api.entity.CompteEpargne;
import com.bankservices.bankservices_api.entity.TransactionBancaire;
import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.enums.StatutCompte;
import com.bankservices.bankservices_api.enums.StatutTransaction;
import com.bankservices.bankservices_api.enums.TypeTransaction;
import com.bankservices.bankservices_api.exception.InsufficientBalanceException;
import com.bankservices.bankservices_api.exception.ResourceNotFoundException;
import com.bankservices.bankservices_api.notification.AccountNotificationEvent;
import com.bankservices.bankservices_api.repository.CompteRepository;
import com.bankservices.bankservices_api.repository.TransactionRepository;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.bankservices.bankservices_api.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TransactionService}: deposits, withdrawals, transfers,
 * balance/overdraft rules, ownership authorization, notifications and history.
 * All collaborators are Mockito mocks; the SecurityContext is set explicitly.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private CompteRepository compteRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private CompteSecurityService compteSecurityService;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TransactionService transactionService;

    private Client alice;
    private Client bob;

    @BeforeEach
    void setUp() {
        alice = client(1, "Alice", "Test", "alice@example.test");
        bob = client(2, "Bob", "Test", "bob@example.test");
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private TransactionRequest request(TypeTransaction type, String amount,
                                       Integer sourceId, Integer destinationId) {
        TransactionRequest request = new TransactionRequest();
        request.setTypeTransaction(type);
        request.setMontant(amount == null ? null : new BigDecimal(amount));
        request.setDescription("Unit test operation");
        request.setIdCompteSource(sourceId);
        request.setIdCompteDestination(destinationId);
        return request;
    }

    private void lockReturns(CompteBancaire compte) {
        when(compteRepository.findByIdForUpdate(compte.getIdCompte()))
                .thenReturn(Optional.of(compte));
    }

    private void saveReturnsArgumentWithId() {
        when(transactionRepository.save(any(TransactionBancaire.class)))
                .thenAnswer(invocation -> {
                    TransactionBancaire saved = invocation.getArgument(0);
                    saved.setIdTransaction(99);
                    return saved;
                });
    }

    private List<AccountNotificationEvent> capturedNotifications(int expected) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(expected)).publishEvent(captor.capture());
        return captor.getAllValues().stream()
                .map(AccountNotificationEvent.class::cast)
                .toList();
    }

    // ============================================================
    // DEPOSIT
    // ============================================================

    @Nested
    @DisplayName("Deposit (DEPOT)")
    class Deposit {

        @Test
        void deposit_asAdmin_increasesBalanceAndReturnsValidatedTransaction() {
            // Arrange
            authenticateAs("admin", "ADMIN");
            CompteCourant account = compteCourant(10, alice, "100.00", "0.00");
            lockReturns(account);
            saveReturnsArgumentWithId();

            // Act
            TransactionResponse response = transactionService.executeTransaction(
                    request(TypeTransaction.DEPOT, "250.50", null, 10));

            // Assert
            assertThat(account.getSolde()).isEqualByComparingTo("350.50");
            assertThat(response.getIdTransaction()).isEqualTo(99);
            assertThat(response.getTypeTransaction()).isEqualTo(TypeTransaction.DEPOT);
            assertThat(response.getStatut()).isEqualTo(StatutTransaction.VALIDEE);
            assertThat(response.getMontant()).isEqualByComparingTo("250.50");
            assertThat(response.getIdCompteSource()).isNull();
            assertThat(response.getIdCompteDestination()).isEqualTo(10);
        }

        @Test
        void deposit_asOwningClient_isAllowed() {
            authenticateAs("alice", "CLIENT");
            CompteCourant account = compteCourant(10, alice, "0.00", "0.00");
            lockReturns(account);
            when(compteSecurityService.isOwner(10, "alice")).thenReturn(true);
            saveReturnsArgumentWithId();

            transactionService.executeTransaction(
                    request(TypeTransaction.DEPOT, "40.00", null, 10));

            assertThat(account.getSolde()).isEqualByComparingTo("40.00");
        }

        @Test
        void deposit_asClientOnAnotherClientsAccount_isDeniedAndBalanceUnchanged() {
            authenticateAs("alice", "CLIENT");
            CompteCourant bobsAccount = compteCourant(20, bob, "500.00", "0.00");
            lockReturns(bobsAccount);
            when(compteSecurityService.isOwner(20, "alice")).thenReturn(false);

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.DEPOT, "10.00", null, 20)))
                    .isInstanceOf(AccessDeniedException.class);

            assertThat(bobsAccount.getSolde()).isEqualByComparingTo("500.00");
            verify(transactionRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        void deposit_toNonExistentAccount_throwsResourceNotFound() {
            authenticateAs("admin", "ADMIN");
            when(compteRepository.findByIdForUpdate(404)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.DEPOT, "10.00", null, 404)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("404");

            verify(transactionRepository, never()).save(any());
        }

        @Test
        void deposit_withSourceAccountProvided_isRejectedBeforeAnyLookup() {
            authenticateAs("admin", "ADMIN");

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.DEPOT, "10.00", 1, 2)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("only a destination");

            verifyNoInteractions(compteRepository, transactionRepository);
        }

        @Test
        void deposit_withoutDestinationAccount_isRejected() {
            authenticateAs("admin", "ADMIN");

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.DEPOT, "10.00", null, null)))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(compteRepository, transactionRepository);
        }

        @Test
        void deposit_onClosedAccount_throwsIllegalStateAndBalanceUnchanged() {
            authenticateAs("admin", "ADMIN");
            CompteCourant closed = compteCourant(10, alice, "75.00", "0.00");
            closed.setStatut(StatutCompte.CLOSED);
            lockReturns(closed);

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.DEPOT, "10.00", null, 10)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("closed");

            assertThat(closed.getSolde()).isEqualByComparingTo("75.00");
            verify(transactionRepository, never()).save(any());
        }

        @Test
        void deposit_publishesNotificationToAccountOwnerEmail() {
            authenticateAs("admin", "ADMIN");
            lockReturns(compteCourant(10, alice, "0.00", "0.00"));
            saveReturnsArgumentWithId();

            transactionService.executeTransaction(
                    request(TypeTransaction.DEPOT, "15.00", null, 10));

            AccountNotificationEvent event = capturedNotifications(1).get(0);
            assertThat(event.email()).isEqualTo("alice@example.test");
            assertThat(event.subject()).contains("Deposit");
            assertThat(event.message()).contains("TEST-ACC-10").contains("15.00");
        }

        @Test
        void deposit_whenOwnerHasNoEmail_publishesNoNotification() {
            authenticateAs("admin", "ADMIN");
            alice.setEmail(null);
            lockReturns(compteCourant(10, alice, "0.00", "0.00"));
            saveReturnsArgumentWithId();

            transactionService.executeTransaction(
                    request(TypeTransaction.DEPOT, "15.00", null, 10));

            verifyNoInteractions(eventPublisher);
        }
    }

    // ============================================================
    // WITHDRAWAL
    // ============================================================

    @Nested
    @DisplayName("Withdrawal (RETRAIT)")
    class Withdrawal {

        @Test
        void withdrawal_withSufficientBalance_decreasesBalance() {
            authenticateAs("admin", "ADMIN");
            CompteCourant account = compteCourant(10, alice, "1000.00", "0.00");
            lockReturns(account);
            saveReturnsArgumentWithId();

            TransactionResponse response = transactionService.executeTransaction(
                    request(TypeTransaction.RETRAIT, "300.00", 10, null));

            assertThat(account.getSolde()).isEqualByComparingTo("700.00");
            assertThat(response.getIdCompteSource()).isEqualTo(10);
            assertThat(response.getIdCompteDestination()).isNull();
        }

        @Test
        void withdrawal_ofEntireBalance_leavesZero() {
            authenticateAs("admin", "ADMIN");
            CompteCourant account = compteCourant(10, alice, "123.45", "0.00");
            lockReturns(account);
            saveReturnsArgumentWithId();

            transactionService.executeTransaction(
                    request(TypeTransaction.RETRAIT, "123.45", 10, null));

            assertThat(account.getSolde()).isEqualByComparingTo("0.00");
        }

        @Test
        void withdrawal_withinAuthorizedOverdraft_allowsNegativeBalance() {
            authenticateAs("admin", "ADMIN");
            CompteCourant account = compteCourant(10, alice, "100.00", "500.00");
            lockReturns(account);
            saveReturnsArgumentWithId();

            transactionService.executeTransaction(
                    request(TypeTransaction.RETRAIT, "400.00", 10, null));

            assertThat(account.getSolde()).isEqualByComparingTo("-300.00");
        }

        @Test
        void withdrawal_exactlyReachingOverdraftLimit_isAllowed() {
            authenticateAs("admin", "ADMIN");
            CompteCourant account = compteCourant(10, alice, "100.00", "500.00");
            lockReturns(account);
            saveReturnsArgumentWithId();

            transactionService.executeTransaction(
                    request(TypeTransaction.RETRAIT, "600.00", 10, null));

            assertThat(account.getSolde()).isEqualByComparingTo("-500.00");
        }

        @Test
        void withdrawal_beyondOverdraftLimit_throwsInsufficientBalanceAndBalanceUnchanged() {
            authenticateAs("admin", "ADMIN");
            CompteCourant account = compteCourant(10, alice, "100.00", "500.00");
            lockReturns(account);

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.RETRAIT, "600.01", 10, null)))
                    .isInstanceOf(InsufficientBalanceException.class);

            assertThat(account.getSolde()).isEqualByComparingTo("100.00");
            verify(transactionRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        void withdrawal_fromSavingsAccount_cannotGoBelowZero() {
            authenticateAs("admin", "ADMIN");
            CompteEpargne savings = compteEpargne(11, alice, "50.00", "2.50");
            lockReturns(savings);

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.RETRAIT, "50.01", 11, null)))
                    .isInstanceOf(InsufficientBalanceException.class);

            assertThat(savings.getSolde()).isEqualByComparingTo("50.00");
        }

        @Test
        void withdrawal_fromNonExistentAccount_throwsResourceNotFound() {
            authenticateAs("admin", "ADMIN");
            when(compteRepository.findByIdForUpdate(404)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.RETRAIT, "10.00", 404, null)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void withdrawal_withDestinationAccountProvided_isRejected() {
            authenticateAs("admin", "ADMIN");

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.RETRAIT, "10.00", 1, 2)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("only a source");

            verifyNoInteractions(compteRepository);
        }

        @Test
        void withdrawal_byClientFromAccountTheyDoNotOwn_isDenied() {
            authenticateAs("alice", "CLIENT");
            CompteCourant bobsAccount = compteCourant(20, bob, "500.00", "0.00");
            lockReturns(bobsAccount);
            when(compteSecurityService.isOwner(20, "alice")).thenReturn(false);

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.RETRAIT, "100.00", 20, null)))
                    .isInstanceOf(AccessDeniedException.class);

            assertThat(bobsAccount.getSolde()).isEqualByComparingTo("500.00");
        }

        @Test
        void withdrawal_fromClosedAccount_throwsIllegalState() {
            authenticateAs("admin", "ADMIN");
            CompteCourant closed = compteCourant(10, alice, "500.00", "0.00");
            closed.setStatut(StatutCompte.CLOSED);
            lockReturns(closed);

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.RETRAIT, "10.00", 10, null)))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(closed.getSolde()).isEqualByComparingTo("500.00");
        }
    }

    // ============================================================
    // TRANSFER
    // ============================================================

    @Nested
    @DisplayName("Transfer (VIREMENT)")
    class Transfer {

        @Test
        void transfer_movesAmountAndConservesTotalMoney() {
            authenticateAs("admin", "ADMIN");
            CompteCourant source = compteCourant(10, alice, "1000.00", "0.00");
            CompteCourant destination = compteCourant(20, bob, "200.00", "0.00");
            lockReturns(source);
            lockReturns(destination);
            saveReturnsArgumentWithId();
            BigDecimal totalBefore = source.getSolde().add(destination.getSolde());

            TransactionResponse response = transactionService.executeTransaction(
                    request(TypeTransaction.VIREMENT, "250.00", 10, 20));

            assertThat(source.getSolde()).isEqualByComparingTo("750.00");
            assertThat(destination.getSolde()).isEqualByComparingTo("450.00");
            assertThat(source.getSolde().add(destination.getSolde()))
                    .isEqualByComparingTo(totalBefore);
            assertThat(response.getIdCompteSource()).isEqualTo(10);
            assertThat(response.getIdCompteDestination()).isEqualTo(20);
        }

        @Test
        void transfer_usingOverdraft_isAllowedWithinLimit() {
            authenticateAs("admin", "ADMIN");
            CompteCourant source = compteCourant(10, alice, "0.00", "300.00");
            CompteCourant destination = compteCourant(20, bob, "0.00", "0.00");
            lockReturns(source);
            lockReturns(destination);
            saveReturnsArgumentWithId();

            transactionService.executeTransaction(
                    request(TypeTransaction.VIREMENT, "300.00", 10, 20));

            assertThat(source.getSolde()).isEqualByComparingTo("-300.00");
            assertThat(destination.getSolde()).isEqualByComparingTo("300.00");
        }

        @Test
        void transfer_withInsufficientBalance_throwsAndLeavesBothBalancesUnchanged() {
            authenticateAs("admin", "ADMIN");
            CompteCourant source = compteCourant(10, alice, "100.00", "0.00");
            CompteCourant destination = compteCourant(20, bob, "50.00", "0.00");
            lockReturns(source);
            lockReturns(destination);

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.VIREMENT, "100.01", 10, 20)))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("Insufficient");

            assertThat(source.getSolde()).isEqualByComparingTo("100.00");
            assertThat(destination.getSolde()).isEqualByComparingTo("50.00");
            verify(transactionRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        void transfer_toSameAccount_isRejected() {
            authenticateAs("admin", "ADMIN");

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.VIREMENT, "10.00", 10, 10)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("same account");

            verifyNoInteractions(compteRepository);
        }

        @Test
        void transfer_withMissingSourceOrDestination_isRejected() {
            authenticateAs("admin", "ADMIN");

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.VIREMENT, "10.00", null, 20)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.VIREMENT, "10.00", 10, null)))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(compteRepository);
        }

        @Test
        void transfer_toNonExistentAccount_throwsResourceNotFoundAndSourceUnchanged() {
            authenticateAs("admin", "ADMIN");
            CompteCourant source = compteCourant(10, alice, "100.00", "0.00");
            lockReturns(source);
            when(compteRepository.findByIdForUpdate(404)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.VIREMENT, "10.00", 10, 404)))
                    .isInstanceOf(ResourceNotFoundException.class);

            assertThat(source.getSolde()).isEqualByComparingTo("100.00");
        }

        @Test
        void transfer_locksAccountsInAscendingIdOrderToAvoidDeadlocks() {
            authenticateAs("admin", "ADMIN");
            CompteCourant source = compteCourant(50, alice, "100.00", "0.00");
            CompteCourant destination = compteCourant(7, bob, "0.00", "0.00");
            lockReturns(source);
            lockReturns(destination);
            saveReturnsArgumentWithId();

            transactionService.executeTransaction(
                    request(TypeTransaction.VIREMENT, "10.00", 50, 7));

            InOrder inOrder = inOrder(compteRepository);
            inOrder.verify(compteRepository).findByIdForUpdate(7);
            inOrder.verify(compteRepository).findByIdForUpdate(50);
        }

        @Test
        void transfer_betweenOwnAccountsAsClient_isAllowed() {
            authenticateAs("alice", "CLIENT");
            CompteCourant current = compteCourant(10, alice, "500.00", "0.00");
            CompteEpargne savings = compteEpargne(11, alice, "0.00", "1.50");
            lockReturns(current);
            lockReturns(savings);
            when(compteSecurityService.isOwner(10, "alice")).thenReturn(true);
            when(compteSecurityService.isOwner(11, "alice")).thenReturn(true);
            saveReturnsArgumentWithId();

            transactionService.executeTransaction(
                    request(TypeTransaction.VIREMENT, "200.00", 10, 11));

            assertThat(current.getSolde()).isEqualByComparingTo("300.00");
            assertThat(savings.getSolde()).isEqualByComparingTo("200.00");
        }

        /**
         * Documents CURRENT behaviour: the service also checks ownership of the
         * destination account, so a CLIENT cannot transfer to another customer.
         */
        @Test
        void transfer_byClientToAnotherClientsAccount_isCurrentlyDenied() {
            authenticateAs("alice", "CLIENT");
            CompteCourant source = compteCourant(10, alice, "500.00", "0.00");
            CompteCourant bobsAccount = compteCourant(20, bob, "0.00", "0.00");
            lockReturns(source);
            lockReturns(bobsAccount);
            when(compteSecurityService.isOwner(10, "alice")).thenReturn(true);
            when(compteSecurityService.isOwner(20, "alice")).thenReturn(false);

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.VIREMENT, "50.00", 10, 20)))
                    .isInstanceOf(AccessDeniedException.class);

            assertThat(source.getSolde()).isEqualByComparingTo("500.00");
            assertThat(bobsAccount.getSolde()).isEqualByComparingTo("0.00");
        }

        @Test
        void transfer_fromAccountNotOwnedByClient_isDenied() {
            authenticateAs("alice", "CLIENT");
            CompteCourant bobsAccount = compteCourant(20, bob, "500.00", "0.00");
            CompteCourant alicesAccount = compteCourant(10, alice, "0.00", "0.00");
            lockReturns(bobsAccount);
            lockReturns(alicesAccount);
            when(compteSecurityService.isOwner(20, "alice")).thenReturn(false);

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.VIREMENT, "50.00", 20, 10)))
                    .isInstanceOf(AccessDeniedException.class);

            assertThat(bobsAccount.getSolde()).isEqualByComparingTo("500.00");
        }

        @Test
        void transfer_toClosedAccount_throwsIllegalStateAndBalancesUnchanged() {
            authenticateAs("admin", "ADMIN");
            CompteCourant source = compteCourant(10, alice, "500.00", "0.00");
            CompteCourant closed = compteCourant(20, bob, "0.00", "0.00");
            closed.setStatut(StatutCompte.CLOSED);
            lockReturns(source);
            lockReturns(closed);

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.VIREMENT, "50.00", 10, 20)))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(source.getSolde()).isEqualByComparingTo("500.00");
            assertThat(closed.getSolde()).isEqualByComparingTo("0.00");
        }

        @Test
        void transfer_notifiesBothSenderAndRecipient() {
            authenticateAs("admin", "ADMIN");
            lockReturns(compteCourant(10, alice, "500.00", "0.00"));
            lockReturns(compteCourant(20, bob, "0.00", "0.00"));
            saveReturnsArgumentWithId();

            transactionService.executeTransaction(
                    request(TypeTransaction.VIREMENT, "50.00", 10, 20));

            List<AccountNotificationEvent> events = capturedNotifications(2);
            assertThat(events).extracting(AccountNotificationEvent::email)
                    .containsExactly("alice@example.test", "bob@example.test");
            assertThat(events.get(0).subject()).contains("Transfer sent");
            assertThat(events.get(1).subject()).contains("Transfer received");
        }
    }

    // ============================================================
    // INPUT VALIDATION & AUTHENTICATION
    // ============================================================

    @Nested
    @DisplayName("Validation and authentication")
    class ValidationAndAuthentication {

        @Test
        void executeTransaction_withNullRequest_throwsIllegalArgument() {
            assertThatThrownBy(() -> transactionService.executeTransaction(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void executeTransaction_withZeroAmount_isRejected() {
            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.DEPOT, "0.00", null, 10)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
            verifyNoInteractions(compteRepository);
        }

        @Test
        void executeTransaction_withNegativeAmount_isRejected() {
            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.RETRAIT, "-50.00", 10, null)))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(compteRepository);
        }

        @Test
        void executeTransaction_withNullAmount_isRejected() {
            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.DEPOT, null, null, 10)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void executeTransaction_withNullType_isRejected() {
            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(null, "10.00", null, 10)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("type");
        }

        @Test
        void executeTransaction_withoutAuthentication_isDenied() {
            CompteCourant account = compteCourant(10, alice, "100.00", "0.00");
            lockReturns(account);

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.DEPOT, "10.00", null, 10)))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Authentication required");

            assertThat(account.getSolde()).isEqualByComparingTo("100.00");
        }

        @Test
        void executeTransaction_withUnknownRole_isDenied() {
            authenticateAs("someone", "GUEST");
            CompteCourant account = compteCourant(10, alice, "100.00", "0.00");
            lockReturns(account);

            assertThatThrownBy(() -> transactionService.executeTransaction(
                    request(TypeTransaction.RETRAIT, "10.00", 10, null)))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(compteSecurityService);
        }
    }

    // ============================================================
    // READ OPERATIONS / HISTORY
    // ============================================================

    @Nested
    @DisplayName("Transaction history and lookup")
    class History {

        private TransactionBancaire transfer(Integer id, CompteBancaire src, CompteBancaire dst) {
            TransactionBancaire t = new TransactionBancaire();
            t.setIdTransaction(id);
            t.setTypeTransaction(TypeTransaction.VIREMENT);
            t.setMontant(new BigDecimal("25.00"));
            t.setStatut(StatutTransaction.VALIDEE);
            t.setDateTransaction(LocalDateTime.of(2026, 3, 1, 10, 0));
            t.setCompteSource(src);
            t.setCompteDestination(dst);
            return t;
        }

        @Test
        void getAllTransactions_mapsEntitiesToResponses() {
            CompteCourant a = compteCourant(10, alice, "0", "0");
            CompteCourant b = compteCourant(20, bob, "0", "0");
            when(transactionRepository.findAll()).thenReturn(List.of(transfer(1, a, b)));

            List<TransactionResponse> result = transactionService.getAllTransactions();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIdCompteSource()).isEqualTo(10);
            assertThat(result.get(0).getIdCompteDestination()).isEqualTo(20);
            assertThat(result.get(0).getMontant()).isEqualByComparingTo("25.00");
        }

        @Test
        void getTransactionById_asAdmin_returnsTransaction() {
            authenticateAs("admin", "ADMIN");
            CompteCourant a = compteCourant(10, alice, "0", "0");
            CompteCourant b = compteCourant(20, bob, "0", "0");
            when(transactionRepository.findById(1)).thenReturn(Optional.of(transfer(1, a, b)));

            TransactionResponse response = transactionService.getTransactionById(1);

            assertThat(response.getIdTransaction()).isEqualTo(1);
            verifyNoInteractions(compteSecurityService);
        }

        @Test
        void getTransactionById_asClientOwningInvolvedAccounts_returnsTransaction() {
            authenticateAs("alice", "CLIENT");
            CompteCourant a = compteCourant(10, alice, "0", "0");
            CompteCourant a2 = compteCourant(11, alice, "0", "0");
            when(transactionRepository.findById(1)).thenReturn(Optional.of(transfer(1, a, a2)));
            when(compteSecurityService.isOwner(10, "alice")).thenReturn(true);
            when(compteSecurityService.isOwner(11, "alice")).thenReturn(true);

            assertThat(transactionService.getTransactionById(1).getIdTransaction()).isEqualTo(1);
        }

        @Test
        void getTransactionById_asClientNotOwningAccounts_isDenied() {
            authenticateAs("alice", "CLIENT");
            CompteCourant b = compteCourant(20, bob, "0", "0");
            CompteCourant b2 = compteCourant(21, bob, "0", "0");
            when(transactionRepository.findById(1)).thenReturn(Optional.of(transfer(1, b, b2)));
            when(compteSecurityService.isOwner(20, "alice")).thenReturn(false);

            assertThatThrownBy(() -> transactionService.getTransactionById(1))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void getTransactionById_whenMissing_throwsResourceNotFound() {
            when(transactionRepository.findById(404)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.getTransactionById(404))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("404");
        }

        @Test
        void getTransactionById_withNoLinkedAccount_isDenied() {
            authenticateAs("admin", "ADMIN");
            when(transactionRepository.findById(1)).thenReturn(Optional.of(transfer(1, null, null)));

            assertThatThrownBy(() -> transactionService.getTransactionById(1))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void getTransactionsByClient_forOwnClientId_returnsRepositoryResults() {
            authenticateAs("alice", "CLIENT");
            when(utilisateurRepository.findByUsername("alice"))
                    .thenReturn(Optional.of(utilisateur(5, "alice", "CLIENT", alice)));
            CompteCourant a = compteCourant(10, alice, "0", "0");
            CompteCourant b = compteCourant(20, bob, "0", "0");
            when(transactionRepository
                    .findDistinctByCompteSource_Client_IdClientOrCompteDestination_Client_IdClientOrderByDateTransactionDesc(1, 1))
                    .thenReturn(List.of(transfer(2, b, a), transfer(1, a, b)));

            List<TransactionResponse> history = transactionService.getTransactionsByClient(1);

            assertThat(history).extracting(TransactionResponse::getIdTransaction)
                    .containsExactly(2, 1);
        }

        @Test
        void getTransactionsByClient_forAnotherClientId_isDenied() {
            authenticateAs("alice", "CLIENT");
            when(utilisateurRepository.findByUsername("alice"))
                    .thenReturn(Optional.of(utilisateur(5, "alice", "CLIENT", alice)));

            assertThatThrownBy(() -> transactionService.getTransactionsByClient(2))
                    .isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(transactionRepository);
        }

        @Test
        void getTransactionsByClient_forUserWithoutLinkedClient_isDenied() {
            authenticateAs("admin", "ADMIN");
            when(utilisateurRepository.findByUsername("admin"))
                    .thenReturn(Optional.of(utilisateur(1, "admin", "ADMIN", null)));

            assertThatThrownBy(() -> transactionService.getTransactionsByClient(1))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void getTransactionsByClient_whenAuthenticatedUserMissing_throwsResourceNotFound() {
            authenticateAs("ghost", "CLIENT");
            when(utilisateurRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.getTransactionsByClient(1))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void getTransactionsByClient_withoutAuthentication_isDenied() {
            assertThatThrownBy(() -> transactionService.getTransactionsByClient(1))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }
}
