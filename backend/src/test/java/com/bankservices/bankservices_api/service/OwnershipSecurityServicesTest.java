package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.repository.CompteRepository;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.bankservices.bankservices_api.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the two ownership checks used in @PreAuthorize expressions:
 * {@link ClientSecurityService} ("clientSecurity") and
 * {@link CompteSecurityService} ("compteSecurity").
 */
@ExtendWith(MockitoExtension.class)
class OwnershipSecurityServicesTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private CompteRepository compteRepository;

    private Client alice;
    private Client bob;

    @BeforeEach
    void setUp() {
        alice = client(1, "Alice", "Test", "alice@example.test");
        bob = client(2, "Bob", "Test", "bob@example.test");
    }

    @Nested
    @DisplayName("ClientSecurityService.isOwner")
    class ClientOwnership {

        private ClientSecurityService service;

        @BeforeEach
        void init() {
            service = new ClientSecurityService(utilisateurRepository);
        }

        @Test
        void isOwner_whenUserIsLinkedToRequestedClient_returnsTrue() {
            when(utilisateurRepository.findByUsername("alice"))
                    .thenReturn(Optional.of(utilisateur(5, "alice", "CLIENT", alice)));

            assertThat(service.isOwner(1, "alice")).isTrue();
        }

        @Test
        void isOwner_whenUserIsLinkedToAnotherClient_returnsFalse() {
            when(utilisateurRepository.findByUsername("alice"))
                    .thenReturn(Optional.of(utilisateur(5, "alice", "CLIENT", alice)));

            assertThat(service.isOwner(2, "alice")).isFalse();
        }

        @Test
        void isOwner_whenUserHasNoLinkedClient_returnsFalse() {
            when(utilisateurRepository.findByUsername("admin"))
                    .thenReturn(Optional.of(utilisateur(1, "admin", "ADMIN", null)));

            assertThat(service.isOwner(1, "admin")).isFalse();
        }

        @Test
        void isOwner_whenUserDoesNotExist_returnsFalse() {
            when(utilisateurRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThat(service.isOwner(1, "ghost")).isFalse();
        }
    }

    @Nested
    @DisplayName("CompteSecurityService.isOwner")
    class AccountOwnership {

        private CompteSecurityService service;

        @BeforeEach
        void init() {
            service = new CompteSecurityService(compteRepository, utilisateurRepository);
        }

        @Test
        void isOwner_whenAccountBelongsToUsersClient_returnsTrue() {
            when(compteRepository.findById(10)).thenReturn(Optional.of(compteCourant(10, alice, "0", "0")));
            when(utilisateurRepository.findByUsername("alice"))
                    .thenReturn(Optional.of(utilisateur(5, "alice", "CLIENT", alice)));

            assertThat(service.isOwner(10, "alice")).isTrue();
        }

        @Test
        void isOwner_whenAccountBelongsToAnotherClient_returnsFalse() {
            when(compteRepository.findById(20)).thenReturn(Optional.of(compteCourant(20, bob, "0", "0")));
            when(utilisateurRepository.findByUsername("alice"))
                    .thenReturn(Optional.of(utilisateur(5, "alice", "CLIENT", alice)));

            assertThat(service.isOwner(20, "alice")).isFalse();
        }

        @Test
        void isOwner_whenAccountDoesNotExist_returnsFalse() {
            when(compteRepository.findById(404)).thenReturn(Optional.empty());

            assertThat(service.isOwner(404, "alice")).isFalse();
        }

        @Test
        void isOwner_whenUserDoesNotExist_returnsFalse() {
            when(compteRepository.findById(10)).thenReturn(Optional.of(compteCourant(10, alice, "0", "0")));
            when(utilisateurRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThat(service.isOwner(10, "ghost")).isFalse();
        }

        @Test
        void isOwner_whenUserHasNoLinkedClient_returnsFalse() {
            when(compteRepository.findById(10)).thenReturn(Optional.of(compteCourant(10, alice, "0", "0")));
            when(utilisateurRepository.findByUsername("admin"))
                    .thenReturn(Optional.of(utilisateur(1, "admin", "ADMIN", null)));

            assertThat(service.isOwner(10, "admin")).isFalse();
        }
    }
}
