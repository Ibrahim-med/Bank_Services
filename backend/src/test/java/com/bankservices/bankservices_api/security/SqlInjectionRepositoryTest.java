package com.bankservices.bankservices_api.security;

import com.bankservices.bankservices_api.entity.Bill;
import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.enums.BillCategory;
import com.bankservices.bankservices_api.enums.BillStatus;
import com.bankservices.bankservices_api.repository.AccountActivationTokenRepository;
import com.bankservices.bankservices_api.repository.BillRepository;
import com.bankservices.bankservices_api.repository.ClientRepository;
import com.bankservices.bankservices_api.repository.PasswordResetTokenRepository;
import com.bankservices.bankservices_api.repository.TransactionRepository;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-level SQL injection tests (isolated in-memory H2 database).
 * Every statement Hibernate sends is captured: payloads must only ever travel
 * as bound parameters, never as part of the SQL text, and must be matched
 * literally (no extra rows, no altered query structure, no dropped tables).
 */
@DataJpaTest(properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.bankservices.bankservices_api.security.SqlCapturingInspector")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SqlInjectionRepositoryTest {

    static Stream<String> payloads() {
        return Stream.of(
                "' OR '1'='1",
                "' OR 1=1 --",
                "admin'--",
                "x'; DROP TABLE client; --",
                "1; DELETE FROM utilisateur",
                "\" OR \"\"=\"",
                "' UNION SELECT username, password FROM utilisateur --",
                "%' OR '%'='",
                "\\'; SELECT SLEEP(5); --");
    }

    @Autowired private TestEntityManager em;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private BillRepository billRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PasswordResetTokenRepository resetTokenRepository;
    @Autowired private AccountActivationTokenRepository activationTokenRepository;

    private Client client;

    @BeforeEach
    void setUp() {
        client = new Client();
        client.setNom("Martin");
        client.setPrenom("Alice");
        client.setEmail("alice@example.test");
        client = em.persist(client);

        Utilisateur user = new Utilisateur();
        user.setUsername("alice");
        user.setPassword("$2a$10$hash");
        user.setRole("CLIENT");
        user.setClient(client);
        em.persist(user);

        Bill bill = new Bill();
        bill.setReference("ELEC-0001");
        bill.setProvider("City Power");
        bill.setCategory(BillCategory.ELECTRICITY);
        bill.setAmount(new BigDecimal("10.00"));
        bill.setIssueDate(LocalDate.now());
        bill.setDueDate(LocalDate.now().plusDays(5));
        bill.setStatus(BillStatus.UNPAID);
        bill.setClient(client);
        em.persist(bill);

        em.flush();
        em.clear();
        SqlCapturingInspector.STATEMENTS.clear();
    }

    @ParameterizedTest
    @MethodSource("payloads")
    void lookupsTreatPayloadsAsLiteralValues(String payload) {
        assertThat(utilisateurRepository.findByUsername(payload)).isEmpty();
        assertThat(utilisateurRepository.existsByUsername(payload)).isFalse();
        assertThat(utilisateurRepository.findByClient_EmailIgnoreCase(payload)).isEmpty();
        assertThat(clientRepository.existsByEmailIgnoreCase(payload)).isFalse();
        assertThat(billRepository.existsByReferenceIgnoreCase(payload)).isFalse();
        assertThat(resetTokenRepository.findByTokenHashAndUsedFalse(payload)).isEmpty();
        assertThat(activationTokenRepository.findByTokenHashForUpdate(payload)).isEmpty();

        // The payload never appears in the SQL text: it was sent as a bound parameter.
        assertThat(SqlCapturingInspector.STATEMENTS).isNotEmpty();
        assertThat(SqlCapturingInspector.STATEMENTS)
                .allSatisfy(sql -> {
                    assertThat(sql).doesNotContain(payload);
                    assertThat(sql).doesNotContainIgnoringCase("drop table");
                    assertThat(sql).doesNotContainIgnoringCase("union select");
                });
        assertThat(SqlCapturingInspector.STATEMENTS)
                .filteredOn(sql -> sql.toLowerCase().contains(" where "))
                .allSatisfy(sql -> assertThat(sql).contains("?"));

        // Nothing was dropped or deleted.
        assertThat(clientRepository.count()).isEqualTo(1);
        assertThat(utilisateurRepository.count()).isEqualTo(1);
        assertThat(billRepository.count()).isEqualTo(1);
    }

    @Test
    void specialCharacters_areStoredAndReturnedExactly() {
        Client tricky = new Client();
        tricky.setNom("O'Brien'); DROP TABLE client; --");
        tricky.setPrenom("Zoë-Ann \"Jo\"");
        tricky.setEmail("o'brien+bank@example.com");
        tricky.setAdresse("12 rue de l'Église\nApt. 3 % _ \\ ;");
        Integer id = em.persistAndFlush(tricky).getIdClient();
        em.clear();

        Client reloaded = clientRepository.findById(id).orElseThrow();
        assertThat(reloaded.getNom()).isEqualTo("O'Brien'); DROP TABLE client; --");
        assertThat(reloaded.getPrenom()).isEqualTo("Zoë-Ann \"Jo\"");
        assertThat(reloaded.getEmail()).isEqualTo("o'brien+bank@example.com");
        assertThat(reloaded.getAdresse()).isEqualTo("12 rue de l'Église\nApt. 3 % _ \\ ;");

        assertThat(clientRepository.existsByEmailIgnoreCase("O'BRIEN+BANK@example.com")).isTrue();
        assertThat(clientRepository.count()).isEqualTo(2);
    }

    @Test
    void usernameWithQuote_matchesOnlyItself() {
        Utilisateur quoted = new Utilisateur();
        quoted.setUsername("o'brien");
        quoted.setPassword("$2a$10$hash");
        quoted.setRole("CLIENT");
        em.persistAndFlush(quoted);

        assertThat(utilisateurRepository.findByUsername("o'brien")).isPresent();
        assertThat(utilisateurRepository.findByUsername("o' OR '1'='1")).isEmpty();
        assertThat(utilisateurRepository.findByUsername("o'brien' --")).isEmpty();
    }

    @Test
    void wildcardCharacters_areNotInterpretedAsPatterns() {
        // Derived "equals" queries must not behave like LIKE: '%' matches nothing.
        assertThat(billRepository.existsByReferenceIgnoreCase("%")).isFalse();
        assertThat(billRepository.existsByReferenceIgnoreCase("ELEC%")).isFalse();
        assertThat(billRepository.existsByReferenceIgnoreCase("elec-0001")).isTrue();
    }

    @Test
    void numericLookups_bindIdentifiers() {
        List<?> transactions = transactionRepository
                .findDistinctByCompteSource_Client_IdClientOrCompteDestination_Client_IdClientOrderByDateTransactionDesc(
                        client.getIdClient(), client.getIdClient());

        assertThat(transactions).isEmpty();
        assertThat(SqlCapturingInspector.STATEMENTS)
                .filteredOn(sql -> sql.toLowerCase().contains("transaction_bancaire"))
                .allSatisfy(sql -> assertThat(sql).contains("?")
                        .doesNotContain("id_client=" + client.getIdClient()));
    }
}
