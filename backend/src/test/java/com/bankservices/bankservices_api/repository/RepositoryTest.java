package com.bankservices.bankservices_api.repository;

import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.entity.CompteBancaire;
import com.bankservices.bankservices_api.entity.CompteCourant;
import com.bankservices.bankservices_api.entity.CompteEpargne;
import com.bankservices.bankservices_api.entity.PasswordResetToken;
import com.bankservices.bankservices_api.entity.TransactionBancaire;
import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.enums.StatutTransaction;
import com.bankservices.bankservices_api.enums.TypeTransaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository slice tests (@DataJpaTest) against an in-memory H2 database.
 * They verify entity mappings (single-table inheritance, constraints,
 * @PrePersist defaults) and the custom/derived repository queries.
 * Each test runs in a transaction that is rolled back afterwards.
 *
 * replace = NONE: use the H2 database configured in application-test.properties
 * (MySQL compatibility mode) instead of the slice's default embedded H2, whose
 * different settings reject the CHECK constraints Hibernate generates.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private ClientRepository clientRepository;
    @Autowired private CompteRepository compteRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;

    private Client alice;
    private Client bob;

    @BeforeEach
    void setUp() {
        alice = persistClient("Alice", "alice@example.test");
        bob = persistClient("Bob", "bob@example.test");
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private Client persistClient(String prenom, String email) {
        Client client = new Client();
        client.setNom("Test");
        client.setPrenom(prenom);
        client.setEmail(email);
        return em.persist(client);
    }

    private CompteCourant persistCourant(Client owner, String numero, String solde) {
        CompteCourant compte = new CompteCourant();
        compte.setNumero(numero);
        compte.setSolde(new BigDecimal(solde));
        compte.setDecouvertAutorise(new BigDecimal("100.00"));
        compte.setClient(owner);
        return em.persist(compte);
    }

    private CompteEpargne persistEpargne(Client owner, String numero) {
        CompteEpargne compte = new CompteEpargne();
        compte.setNumero(numero);
        compte.setTauxInteret(new BigDecimal("2.75"));
        compte.setClient(owner);
        return em.persist(compte);
    }

    private TransactionBancaire persistTransaction(TypeTransaction type, CompteBancaire src,
                                                   CompteBancaire dst, LocalDateTime date) {
        TransactionBancaire t = new TransactionBancaire();
        t.setTypeTransaction(type);
        t.setMontant(new BigDecimal("10.00"));
        t.setStatut(StatutTransaction.VALIDEE);
        t.setCompteSource(src);
        t.setCompteDestination(dst);
        t.setDateTransaction(date);
        return em.persist(t);
    }

    private Utilisateur persistUser(String username, String role, Client client) {
        Utilisateur u = new Utilisateur();
        u.setUsername(username);
        u.setPassword("$2a$10$fakeHashForTestsOnly");
        u.setRole(role);
        u.setClient(client);
        return em.persist(u);
    }

    // ============================================================

    @Nested
    @DisplayName("Account mapping and CompteRepository")
    class Accounts {

        @Test
        void singleTableInheritance_loadsCorrectSubclassForEachAccountType() {
            Integer courantId = persistCourant(alice, "R-ACC-1", "10.00").getIdCompte();
            Integer epargneId = persistEpargne(alice, "R-ACC-2").getIdCompte();
            em.flush();
            em.clear();

            assertThat(compteRepository.findById(courantId)).get().isInstanceOf(CompteCourant.class);
            assertThat(compteRepository.findById(epargneId)).get().isInstanceOf(CompteEpargne.class);
        }

        @Test
        void persist_setsCreationDateAndDefaultsBalanceAndStatus() {
            CompteEpargne compte = persistEpargne(alice, "R-ACC-3");
            em.flush();

            assertThat(compte.getDateCreation()).isEqualTo(LocalDate.now());
            assertThat(compte.getSolde()).isEqualByComparingTo("0");
            assertThat(compte.getStatut().name()).isEqualTo("ACTIVE");
        }

        @Test
        void balance_isStoredWithTwoDecimalPrecision() {
            Integer id = persistCourant(alice, "R-ACC-4", "1234567.89").getIdCompte();
            em.flush();
            em.clear();

            assertThat(compteRepository.findById(id).orElseThrow().getSolde())
                    .isEqualByComparingTo("1234567.89");
        }

        @Test
        void duplicateAccountNumber_violatesUniqueConstraint() {
            persistCourant(alice, "DUPLICATE", "0");
            em.flush();

            assertThatThrownBy(() -> {
                persistCourant(bob, "DUPLICATE", "0");
                em.flush();
            }).isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
        }

        @Test
        void findByIdForUpdate_returnsAccountWhenPresentAndEmptyOtherwise() {
            Integer id = persistCourant(alice, "R-ACC-5", "50.00").getIdCompte();
            em.flush();

            assertThat(compteRepository.findByIdForUpdate(id)).isPresent();
            assertThat(compteRepository.findByIdForUpdate(999_999)).isEmpty();
        }
    }

    // ============================================================

    @Nested
    @DisplayName("TransactionRepository queries")
    class Transactions {

        @Test
        void persist_setsTransactionDateWhenMissing() {
            CompteCourant account = persistCourant(alice, "T-ACC-0", "0");
            TransactionBancaire t = persistTransaction(TypeTransaction.DEPOT, null, account, null);
            em.flush();

            assertThat(t.getDateTransaction()).isNotNull();
        }

        @Test
        void findByAccount_returnsIncomingAndOutgoingTransactionsNewestFirst() {
            CompteCourant a = persistCourant(alice, "T-ACC-1", "0");
            CompteCourant b = persistCourant(bob, "T-ACC-2", "0");
            LocalDateTime base = LocalDateTime.of(2026, 5, 1, 9, 0);
            TransactionBancaire oldest = persistTransaction(TypeTransaction.DEPOT, null, a, base);
            TransactionBancaire middle = persistTransaction(TypeTransaction.VIREMENT, a, b, base.plusHours(1));
            TransactionBancaire newest = persistTransaction(TypeTransaction.RETRAIT, a, null, base.plusHours(2));
            persistTransaction(TypeTransaction.DEPOT, null, b, base.plusHours(3)); // unrelated to a
            em.flush();

            List<TransactionBancaire> result = transactionRepository
                    .findByCompteSource_IdCompteOrCompteDestination_IdCompteOrderByDateTransactionDesc(
                            a.getIdCompte(), a.getIdCompte());

            assertThat(result).extracting(TransactionBancaire::getIdTransaction)
                    .containsExactly(newest.getIdTransaction(), middle.getIdTransaction(),
                            oldest.getIdTransaction());
        }

        @Test
        void findByClient_includesDepositsWithdrawalsAndTransfersOfThatClientOnly() {
            CompteCourant a1 = persistCourant(alice, "T-ACC-3", "0");
            CompteEpargne a2 = persistEpargne(alice, "T-ACC-4");
            CompteCourant b1 = persistCourant(bob, "T-ACC-5", "0");
            LocalDateTime base = LocalDateTime.of(2026, 5, 2, 9, 0);
            TransactionBancaire deposit = persistTransaction(TypeTransaction.DEPOT, null, a1, base);
            TransactionBancaire withdrawal = persistTransaction(TypeTransaction.RETRAIT, a1, null, base.plusHours(1));
            TransactionBancaire internal = persistTransaction(TypeTransaction.VIREMENT, a1, a2, base.plusHours(2));
            TransactionBancaire received = persistTransaction(TypeTransaction.VIREMENT, b1, a1, base.plusHours(3));
            persistTransaction(TypeTransaction.DEPOT, null, b1, base.plusHours(4)); // Bob only
            em.flush();
            em.clear();

            List<TransactionBancaire> history = transactionRepository
                    .findDistinctByCompteSource_Client_IdClientOrCompteDestination_Client_IdClientOrderByDateTransactionDesc(
                            alice.getIdClient(), alice.getIdClient());

            assertThat(history).extracting(TransactionBancaire::getIdTransaction)
                    .containsExactly(received.getIdTransaction(), internal.getIdTransaction(),
                            withdrawal.getIdTransaction(), deposit.getIdTransaction());
        }
    }

    // ============================================================

    @Nested
    @DisplayName("Users, clients and reset tokens")
    class UsersAndTokens {

        @Test
        void utilisateurQueries_findAndExistChecksWork() {
            persistUser("alice", "CLIENT", alice);
            persistUser("admin", "ADMIN", null);
            em.flush();

            assertThat(utilisateurRepository.findByUsername("alice")).isPresent();
            assertThat(utilisateurRepository.findByUsername("nobody")).isEmpty();
            assertThat(utilisateurRepository.existsByUsername("admin")).isTrue();
            assertThat(utilisateurRepository.existsByClient_IdClient(alice.getIdClient())).isTrue();
            assertThat(utilisateurRepository.existsByClient_IdClient(bob.getIdClient())).isFalse();
        }

        @Test
        void findByClientEmailIgnoreCase_matchesRegardlessOfCase() {
            persistUser("alice", "CLIENT", alice);
            em.flush();

            assertThat(utilisateurRepository.findByClient_EmailIgnoreCase("ALICE@Example.TEST"))
                    .get().extracting(Utilisateur::getUsername).isEqualTo("alice");
            assertThat(utilisateurRepository.findByClient_EmailIgnoreCase("bob@example.test")).isEmpty();
        }

        @Test
        void duplicateUsername_violatesUniqueConstraint() {
            persistUser("alice", "CLIENT", alice);
            em.flush();

            assertThatThrownBy(() -> {
                persistUser("alice", "CLIENT", bob);
                em.flush();
            }).isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
        }

        @Test
        void duplicateClientEmail_violatesUniqueConstraint() {
            assertThatThrownBy(() -> {
                persistClient("Clone", "alice@example.test");
                em.flush();
            }).isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
        }

        @Test
        void clientRepository_savesAndFindsClient() {
            Client saved = clientRepository.save(newClient("Carol", "carol@example.test"));
            em.flush();
            em.clear();

            assertThat(clientRepository.findById(saved.getIdClient()))
                    .get().extracting(Client::getPrenom).isEqualTo("Carol");
        }

        private Client newClient(String prenom, String email) {
            Client c = new Client();
            c.setNom("Test");
            c.setPrenom(prenom);
            c.setEmail(email);
            return c;
        }

        @Test
        void resetTokenQueries_ignoreUsedTokensAndDeleteByUser() {
            Utilisateur user = persistUser("alice", "CLIENT", alice);
            PasswordResetToken active = token(user, "a".repeat(64), false);
            token(user, "b".repeat(64), true);
            em.flush();

            assertThat(tokenRepository.findByTokenHashAndUsedFalse("a".repeat(64)))
                    .get().extracting(PasswordResetToken::getId).isEqualTo(active.getId());
            assertThat(tokenRepository.findByTokenHashAndUsedFalse("b".repeat(64))).isEmpty();

            tokenRepository.deleteByUtilisateur_IdUtilisateur(user.getIdUtilisateur());
            em.flush();

            assertThat(tokenRepository.count()).isZero();
        }

        private PasswordResetToken token(Utilisateur user, String hash, boolean used) {
            PasswordResetToken t = new PasswordResetToken();
            t.setTokenHash(hash);
            t.setUtilisateur(user);
            t.setUsed(used);
            t.setCreatedAt(LocalDateTime.now());
            t.setExpiryDate(LocalDateTime.now().plusMinutes(15));
            return em.persist(t);
        }
    }
}
