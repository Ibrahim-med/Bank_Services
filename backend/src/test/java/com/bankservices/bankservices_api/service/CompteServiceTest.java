package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.dto.request.CompteRequest;
import com.bankservices.bankservices_api.dto.response.CompteResponse;
import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.entity.CompteBancaire;
import com.bankservices.bankservices_api.entity.CompteCourant;
import com.bankservices.bankservices_api.entity.CompteEpargne;
import com.bankservices.bankservices_api.enums.StatutCompte;
import com.bankservices.bankservices_api.enums.TypeCompte;
import com.bankservices.bankservices_api.exception.ResourceNotFoundException;
import com.bankservices.bankservices_api.notification.AccountNotificationEvent;
import com.bankservices.bankservices_api.repository.ClientRepository;
import com.bankservices.bankservices_api.repository.CompteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.bankservices.bankservices_api.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CompteService}: account creation (current/savings),
 * retrieval, closing and reopening, plus e-mail notification events.
 */
@ExtendWith(MockitoExtension.class)
class CompteServiceTest {

    @Mock private CompteRepository compteRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CompteService compteService;

    private Client alice;

    @BeforeEach
    void setUp() {
        alice = client(1, "Alice", "Test", "alice@example.test");
    }

    private CompteRequest request(TypeCompte type, String decouvert, String taux) {
        CompteRequest request = new CompteRequest();
        request.setNumero("NEW-ACC-001");
        request.setTypeCompte(type);
        request.setDecouvertAutorise(decouvert == null ? null : new BigDecimal(decouvert));
        request.setTauxInteret(taux == null ? null : new BigDecimal(taux));
        request.setIdClient(1);
        return request;
    }

    private void saveReturnsArgumentWithId() {
        when(compteRepository.save(any(CompteBancaire.class))).thenAnswer(invocation -> {
            CompteBancaire saved = invocation.getArgument(0);
            saved.setIdCompte(42);
            return saved;
        });
    }

    private AccountNotificationEvent capturedNotification() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return (AccountNotificationEvent) captor.getValue();
    }

    // ============================================================
    // CREATE
    // ============================================================

    @Nested
    @DisplayName("Account creation")
    class Create {

        @Test
        void createCompte_courant_createsActiveCurrentAccountWithZeroBalanceAndOverdraft() {
            when(clientRepository.findById(1)).thenReturn(Optional.of(alice));
            saveReturnsArgumentWithId();

            CompteResponse response = compteService.createCompte(
                    request(TypeCompte.COURANT, "500.00", null));

            ArgumentCaptor<CompteBancaire> saved = ArgumentCaptor.forClass(CompteBancaire.class);
            verify(compteRepository).save(saved.capture());
            assertThat(saved.getValue()).isInstanceOf(CompteCourant.class);
            assertThat(response.getIdCompte()).isEqualTo(42);
            assertThat(response.getTypeCompte()).isEqualTo(TypeCompte.COURANT);
            assertThat(response.getSolde()).isEqualByComparingTo("0");
            assertThat(response.getDecouvertAutorise()).isEqualByComparingTo("500.00");
            assertThat(response.getStatut()).isEqualTo(StatutCompte.ACTIVE);
            assertThat(response.getIdClient()).isEqualTo(1);
            assertThat(alice.getComptes()).contains(saved.getValue());
        }

        @Test
        void createCompte_epargne_createsSavingsAccountWithInterestRate() {
            when(clientRepository.findById(1)).thenReturn(Optional.of(alice));
            saveReturnsArgumentWithId();

            CompteResponse response = compteService.createCompte(
                    request(TypeCompte.EPARGNE, null, "3.25"));

            assertThat(response.getTypeCompte()).isEqualTo(TypeCompte.EPARGNE);
            assertThat(response.getTauxInteret()).isEqualByComparingTo("3.25");
            assertThat(response.getDecouvertAutorise()).isEqualByComparingTo("0");
        }

        @Test
        void createCompte_courantWithoutOverdraft_defaultsOverdraftToZero() {
            when(clientRepository.findById(1)).thenReturn(Optional.of(alice));
            saveReturnsArgumentWithId();

            CompteResponse response = compteService.createCompte(
                    request(TypeCompte.COURANT, null, null));

            assertThat(response.getDecouvertAutorise()).isEqualByComparingTo("0");
        }

        @Test
        void createCompte_epargneWithoutRate_defaultsRateToZero() {
            when(clientRepository.findById(1)).thenReturn(Optional.of(alice));
            saveReturnsArgumentWithId();

            CompteResponse response = compteService.createCompte(
                    request(TypeCompte.EPARGNE, null, null));

            assertThat(response.getTauxInteret()).isEqualByComparingTo("0");
        }

        @Test
        void createCompte_publishesAccountCreatedNotification() {
            when(clientRepository.findById(1)).thenReturn(Optional.of(alice));
            saveReturnsArgumentWithId();

            compteService.createCompte(request(TypeCompte.COURANT, "0", null));

            AccountNotificationEvent event = capturedNotification();
            assertThat(event.email()).isEqualTo("alice@example.test");
            assertThat(event.subject()).contains("New account created");
            assertThat(event.message()).contains("NEW-ACC-001").contains("COURANT");
        }

        @Test
        void createCompte_forNonExistentClient_throwsResourceNotFound() {
            when(clientRepository.findById(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> compteService.createCompte(
                    request(TypeCompte.COURANT, "0", null)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Client not found");

            verify(compteRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        void createCompte_withNegativeOverdraft_isRejected() {
            when(clientRepository.findById(1)).thenReturn(Optional.of(alice));

            assertThatThrownBy(() -> compteService.createCompte(
                    request(TypeCompte.COURANT, "-1.00", null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Overdraft");

            verify(compteRepository, never()).save(any());
        }

        @Test
        void createCompte_withNegativeInterestRate_isRejected() {
            when(clientRepository.findById(1)).thenReturn(Optional.of(alice));

            assertThatThrownBy(() -> compteService.createCompte(
                    request(TypeCompte.EPARGNE, null, "-0.50")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Interest rate");

            verify(compteRepository, never()).save(any());
        }

        @Test
        void createCompte_withNullAccountType_isRejected() {
            when(clientRepository.findById(1)).thenReturn(Optional.of(alice));

            assertThatThrownBy(() -> compteService.createCompte(request(null, null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid account type");

            verify(compteRepository, never()).save(any());
        }
    }

    // ============================================================
    // READ
    // ============================================================

    @Nested
    @DisplayName("Account retrieval")
    class Read {

        @Test
        void getAllComptes_returnsEveryAccountWithCorrectType() {
            CompteCourant courant = compteCourant(1, alice, "100.00", "200.00");
            CompteEpargne epargne = compteEpargne(2, alice, "900.00", "2.00");
            when(compteRepository.findAll()).thenReturn(List.of(courant, epargne));

            List<CompteResponse> result = compteService.getAllComptes();

            assertThat(result).extracting(CompteResponse::getTypeCompte)
                    .containsExactly(TypeCompte.COURANT, TypeCompte.EPARGNE);
            assertThat(result).extracting(CompteResponse::getSolde)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("100.00"), new BigDecimal("900.00"));
        }

        @Test
        void getCompteById_returnsMappedAccount() {
            when(compteRepository.findById(1))
                    .thenReturn(Optional.of(compteCourant(1, alice, "55.10", "0")));

            CompteResponse response = compteService.getCompteById(1);

            assertThat(response.getNumero()).isEqualTo("TEST-ACC-1");
            assertThat(response.getSolde()).isEqualByComparingTo("55.10");
            assertThat(response.getIdClient()).isEqualTo(1);
        }

        @Test
        void getCompteById_whenMissing_throwsResourceNotFound() {
            when(compteRepository.findById(404)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> compteService.getCompteById(404))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("404");
        }

        @Test
        void getComptesByClient_returnsOnlyThatClientsAccounts() {
            compteCourant(1, alice, "10", "0");
            compteEpargne(2, alice, "20", "1");
            when(clientRepository.findById(1)).thenReturn(Optional.of(alice));

            List<CompteResponse> result = compteService.getComptesByClient(1);

            assertThat(result).hasSize(2).allMatch(c -> c.getIdClient().equals(1));
        }

        @Test
        void getComptesByClient_forClientWithoutAccounts_returnsEmptyList() {
            when(clientRepository.findById(1)).thenReturn(Optional.of(alice));

            assertThat(compteService.getComptesByClient(1)).isEmpty();
        }

        @Test
        void getComptesByClient_whenClientMissing_throwsResourceNotFound() {
            when(clientRepository.findById(9)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> compteService.getComptesByClient(9))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ============================================================
    // CLOSE / REOPEN
    // ============================================================

    @Nested
    @DisplayName("Close and reopen")
    class CloseAndReopen {

        @Test
        void closeCompte_withZeroBalance_closesAccountAndNotifies() {
            CompteCourant account = compteCourant(1, alice, "0.00", "0");
            when(compteRepository.findByIdForUpdate(1)).thenReturn(Optional.of(account));

            CompteResponse response = compteService.closeCompte(1);

            assertThat(response.getStatut()).isEqualTo(StatutCompte.CLOSED);
            assertThat(account.getStatut()).isEqualTo(StatutCompte.CLOSED);
            assertThat(capturedNotification().subject()).contains("Account closed");
        }

        @Test
        void closeCompte_withPositiveBalance_isRejected() {
            CompteCourant account = compteCourant(1, alice, "0.01", "0");
            when(compteRepository.findByIdForUpdate(1)).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> compteService.closeCompte(1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("balance is not zero");

            assertThat(account.getStatut()).isEqualTo(StatutCompte.ACTIVE);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        void closeCompte_withNegativeBalance_isRejected() {
            CompteCourant account = compteCourant(1, alice, "-20.00", "100");
            when(compteRepository.findByIdForUpdate(1)).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> compteService.closeCompte(1))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(account.getStatut()).isEqualTo(StatutCompte.ACTIVE);
        }

        @Test
        void closeCompte_whenAlreadyClosed_isRejected() {
            CompteCourant account = compteCourant(1, alice, "0", "0");
            account.setStatut(StatutCompte.CLOSED);
            when(compteRepository.findByIdForUpdate(1)).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> compteService.closeCompte(1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already closed");
        }

        @Test
        void closeCompte_whenMissing_throwsResourceNotFound() {
            when(compteRepository.findByIdForUpdate(404)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> compteService.closeCompte(404))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void closeCompte_withNullId_isRejected() {
            assertThatThrownBy(() -> compteService.closeCompte(null))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(compteRepository);
        }

        @Test
        void reopenCompte_whenClosed_reactivatesAccountAndNotifies() {
            CompteCourant account = compteCourant(1, alice, "0", "0");
            account.setStatut(StatutCompte.CLOSED);
            when(compteRepository.findByIdForUpdate(1)).thenReturn(Optional.of(account));

            CompteResponse response = compteService.reopenCompte(1);

            assertThat(response.getStatut()).isEqualTo(StatutCompte.ACTIVE);
            assertThat(capturedNotification().subject()).contains("Account reopened");
        }

        @Test
        void reopenCompte_whenAlreadyActive_isRejected() {
            when(compteRepository.findByIdForUpdate(1))
                    .thenReturn(Optional.of(compteCourant(1, alice, "0", "0")));

            assertThatThrownBy(() -> compteService.reopenCompte(1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already active");
            verifyNoInteractions(eventPublisher);
        }

        @Test
        void reopenCompte_whenMissing_throwsResourceNotFound() {
            when(compteRepository.findByIdForUpdate(404)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> compteService.reopenCompte(404))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void reopenCompte_withNullId_isRejected() {
            assertThatThrownBy(() -> compteService.reopenCompte(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
