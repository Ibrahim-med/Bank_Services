package com.bankservices.bankservices_api.integration;

import com.bankservices.bankservices_api.dto.request.TransactionRequest;
import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.entity.CompteBancaire;
import com.bankservices.bankservices_api.entity.CompteCourant;
import com.bankservices.bankservices_api.entity.CompteEpargne;
import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.enums.StatutCompte;
import com.bankservices.bankservices_api.enums.TypeTransaction;
import com.bankservices.bankservices_api.exception.InsufficientBalanceException;
import com.bankservices.bankservices_api.repository.ClientRepository;
import com.bankservices.bankservices_api.repository.CompteRepository;
import com.bankservices.bankservices_api.repository.PasswordResetTokenRepository;
import com.bankservices.bankservices_api.repository.TransactionRepository;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;
import com.bankservices.bankservices_api.service.TransactionService;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.bankservices.bankservices_api.support.TestFixtures.authenticateAs;
import static com.bankservices.bankservices_api.support.TestFixtures.clearAuthentication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests: full Spring context + in-memory H2 + real JWT security.
 * Requests go through MockMvc with tokens obtained from /api/auth/login.
 * Data is committed for real (no test-level transaction), so rollback of failed
 * operations and row locking are verified against the database.
 * JavaMailSender is mocked: no e-mail ever leaves the test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankingApiIntegrationTest {

    private static final String ADMIN_PASSWORD = "AdminTestPass1";
    private static final String ALICE_PASSWORD = "AliceTestPass1";
    private static final String BOB_PASSWORD = "BobTestPass1";

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionService transactionService;
    @Autowired private ClientRepository clientRepository;
    @Autowired private CompteRepository compteRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private com.bankservices.bankservices_api.repository.BillPaymentRepository billPaymentRepository;
    @Autowired private com.bankservices.bankservices_api.repository.BillRepository billRepository;
    @Autowired private com.bankservices.bankservices_api.repository.AccountActivationTokenRepository activationTokenRepository;

    @MockitoBean
    private JavaMailSender mailSender;

    private Client alice;
    private Client bob;
    private Integer aliceCurrentId;
    private Integer aliceSavingsId;
    private Integer bobCurrentId;

    // ------------------------------------------------------------
    // Fixture: admin, Alice (2 accounts) and Bob (1 account)
    // ------------------------------------------------------------

    @BeforeEach
    void setUp() {
        cleanDatabase();

        createUser("admin", ADMIN_PASSWORD, "ADMIN", null);
        alice = createClient("Alice", "alice@example.test");
        bob = createClient("Bob", "bob@example.test");
        createUser("alice", ALICE_PASSWORD, "CLIENT", alice);
        createUser("bob", BOB_PASSWORD, "CLIENT", bob);

        aliceCurrentId = createCurrent(alice, "IT-ALICE-CUR", "1000.00", "200.00");
        aliceSavingsId = createSavings(alice, "IT-ALICE-SAV", "0.00");
        bobCurrentId = createCurrent(bob, "IT-BOB-CUR", "100.00", "0.00");

        reset(mailSender);
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
        cleanDatabase();
    }

    private void cleanDatabase() {
        // Tables added by the activation / bill features reference the ones below.
        billPaymentRepository.deleteAll();
        billRepository.deleteAll();
        activationTokenRepository.deleteAll();
        transactionRepository.deleteAll();
        tokenRepository.deleteAll();
        utilisateurRepository.deleteAll();
        compteRepository.deleteAll();
        clientRepository.deleteAll();
    }

    private Client createClient(String prenom, String email) {
        Client client = new Client();
        client.setNom("Test");
        client.setPrenom(prenom);
        client.setEmail(email);
        return clientRepository.save(client);
    }

    private void createUser(String username, String password, String role, Client client) {
        Utilisateur user = new Utilisateur();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setClient(client);
        utilisateurRepository.save(user);
    }

    private Integer createCurrent(Client owner, String numero, String solde, String decouvert) {
        CompteCourant compte = new CompteCourant();
        compte.setNumero(numero);
        compte.setSolde(new BigDecimal(solde));
        compte.setDecouvertAutorise(new BigDecimal(decouvert));
        compte.setClient(owner);
        return compteRepository.save(compte).getIdCompte();
    }

    private Integer createSavings(Client owner, String numero, String solde) {
        CompteEpargne compte = new CompteEpargne();
        compte.setNumero(numero);
        compte.setSolde(new BigDecimal(solde));
        compte.setTauxInteret(new BigDecimal("2.00"));
        compte.setClient(owner);
        return compteRepository.save(compte).getIdCompte();
    }

    private BigDecimal balanceOf(Integer accountId) {
        return compteRepository.findById(accountId).orElseThrow().getSolde();
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private ResultActions postTransaction(String token, String type, String amount,
                                          Integer source, Integer destination) throws Exception {
        String json = """
                {"typeTransaction":"%s","montant":%s,"description":"IT",
                 "idCompteSource":%s,"idCompteDestination":%s}
                """.formatted(type, amount, source, destination);
        return mockMvc.perform(post("/api/transactions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    // ============================================================
    // AUTHENTICATION
    // ============================================================

    @Nested
    @DisplayName("Authentication with real JWT")
    class Authentication {

        @Test
        void login_returnsTokenRoleAndClientId_andTokenGrantsAccess() throws Exception {
            String body = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"alice\",\"password\":\"" + ALICE_PASSWORD + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("CLIENT"))
                    .andExpect(jsonPath("$.idClient").value(alice.getIdClient()))
                    .andReturn().getResponse().getContentAsString();
            String token = JsonPath.read(body, "$.token");

            mockMvc.perform(get("/api/comptes/client/" + alice.getIdClient())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        void login_withWrongPassword_isRejected() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"alice\",\"password\":\"WrongPass99\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }

        @Test
        void protectedEndpoint_withoutToken_returns401() throws Exception {
            mockMvc.perform(get("/api/comptes")).andExpect(status().isUnauthorized());
        }

        @Test
        void protectedEndpoint_withForgedToken_returns401() throws Exception {
            mockMvc.perform(get("/api/comptes")
                            .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.forged"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void tokenOfDeletedUser_isRejectedWith401() throws Exception {
            String token = login("bob", BOB_PASSWORD);
            utilisateurRepository.delete(utilisateurRepository.findByUsername("bob").orElseThrow());

            mockMvc.perform(get("/api/comptes/" + bobCurrentId).header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void register_thenLogin_newClientSeesOnlyOwnAccounts() throws Exception {
            Client carol = createClient("Carol", "carol@example.test");
            createCurrent(carol, "IT-CAROL-CUR", "5.00", "0.00");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"carol\",\"password\":\"CarolTestPass1\",\"idClient\":"
                                    + carol.getIdClient() + "}"))
                    .andExpect(status().isCreated());

            String token = login("carol", "CarolTestPass1");

            mockMvc.perform(get("/api/comptes/client/" + carol.getIdClient())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].numero").value("IT-CAROL-CUR"));
            mockMvc.perform(get("/api/comptes/client/" + alice.getIdClient())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        @Test
        void passwordResetFlow_resetsPasswordAndTokenIsSingleUse() throws Exception {
            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"alice@example.test\"}"))
                    .andExpect(status().isOk());

            ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(mail.capture());
            String text = mail.getValue().getText();
            String rawToken = text.substring(text.indexOf("token=") + 6).split("\\s")[0];

            String resetBody = "{\"token\":\"" + rawToken + "\",\"newPassword\":\"BrandNewPass1\"}";
            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON).content(resetBody))
                    .andExpect(status().isOk());

            login("alice", "BrandNewPass1");
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"alice\",\"password\":\"" + ALICE_PASSWORD + "\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON).content(resetBody))
                    .andExpect(status().isBadRequest());
        }
    }

    // ============================================================
    // AUTHORIZATION
    // ============================================================

    @Nested
    @DisplayName("Role and ownership restrictions")
    class Authorization {

        @Test
        void client_cannotUseAdminEndpoints() throws Exception {
            String token = login("alice", ALICE_PASSWORD);

            mockMvc.perform(get("/api/clients").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/comptes").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/transactions").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/comptes/" + aliceSavingsId + "/close")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        @Test
        void client_cannotReadAnotherClientsAccountOrProfile() throws Exception {
            String token = login("alice", ALICE_PASSWORD);

            mockMvc.perform(get("/api/comptes/" + bobCurrentId).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/clients/" + bob.getIdClient()).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/transactions/client/" + bob.getIdClient())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        @Test
        void client_cannotWithdrawFromAnotherClientsAccount_andBalanceIsUnchanged() throws Exception {
            String token = login("alice", ALICE_PASSWORD);

            postTransaction(token, "RETRAIT", "50.00", bobCurrentId, null)
                    .andExpect(status().isForbidden());

            assertThat(balanceOf(bobCurrentId)).isEqualByComparingTo("100.00");
            assertThat(transactionRepository.count()).isZero();
        }

        @Test
        void admin_canCreateClientAndAccountThroughApi() throws Exception {
            String token = login("admin", ADMIN_PASSWORD);

            String clientBody = mockMvc.perform(post("/api/clients")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nom\":\"Test\",\"prenom\":\"Dave\",\"email\":\"dave@example.test\"}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            Integer daveId = JsonPath.read(clientBody, "$.idClient");

            mockMvc.perform(post("/api/comptes")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"numero\":\"IT-DAVE-SAV\",\"typeCompte\":\"EPARGNE\",\"tauxInteret\":1.5,\"idClient\":"
                                    + daveId + "}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.typeCompte").value("EPARGNE"))
                    .andExpect(jsonPath("$.solde").value(0))
                    .andExpect(jsonPath("$.idClient").value(daveId));

            assertThat(compteRepository.findAll())
                    .anyMatch(c -> c.getNumero().equals("IT-DAVE-SAV") && c instanceof CompteEpargne);
        }
    }

    // ============================================================
    // FINANCIAL OPERATIONS & CONSISTENCY
    // ============================================================

    @Nested
    @DisplayName("Financial operations persisted in the database")
    class FinancialOperations {

        @Test
        void deposit_persistsNewBalanceAndValidatedTransaction() throws Exception {
            String token = login("admin", ADMIN_PASSWORD);

            postTransaction(token, "DEPOT", "250.25", null, bobCurrentId)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statut").value("VALIDEE"));

            assertThat(balanceOf(bobCurrentId)).isEqualByComparingTo("350.25");
            assertThat(transactionRepository.count()).isEqualTo(1);
        }

        @Test
        void withdrawalUsingOverdraft_persistsNegativeBalance() throws Exception {
            String token = login("alice", ALICE_PASSWORD);

            postTransaction(token, "RETRAIT", "1150.00", aliceCurrentId, null)
                    .andExpect(status().isCreated());

            assertThat(balanceOf(aliceCurrentId)).isEqualByComparingTo("-150.00");
        }

        @Test
        void withdrawalBeyondOverdraft_returns400_andDatabaseIsUnchanged() throws Exception {
            String token = login("alice", ALICE_PASSWORD);

            postTransaction(token, "RETRAIT", "1200.01", aliceCurrentId, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Insufficient available balance."));

            assertThat(balanceOf(aliceCurrentId)).isEqualByComparingTo("1000.00");
            assertThat(transactionRepository.count()).isZero();
        }

        @Test
        void transferBetweenOwnAccounts_movesMoneyAndConservesTotal() throws Exception {
            String token = login("alice", ALICE_PASSWORD);

            postTransaction(token, "VIREMENT", "400.00", aliceCurrentId, aliceSavingsId)
                    .andExpect(status().isCreated());

            assertThat(balanceOf(aliceCurrentId)).isEqualByComparingTo("600.00");
            assertThat(balanceOf(aliceSavingsId)).isEqualByComparingTo("400.00");
            assertThat(balanceOf(aliceCurrentId).add(balanceOf(aliceSavingsId)))
                    .isEqualByComparingTo("1000.00");
        }

        @Test
        void adminTransferBetweenClients_updatesBothClientsBalances() throws Exception {
            String token = login("admin", ADMIN_PASSWORD);

            postTransaction(token, "VIREMENT", "75.50", aliceCurrentId, bobCurrentId)
                    .andExpect(status().isCreated());

            assertThat(balanceOf(aliceCurrentId)).isEqualByComparingTo("924.50");
            assertThat(balanceOf(bobCurrentId)).isEqualByComparingTo("175.50");
        }

        @Test
        void transferToClosedAccount_isRolledBack_noBalanceChangesNoTransaction() throws Exception {
            CompteBancaire savings = compteRepository.findById(aliceSavingsId).orElseThrow();
            savings.setStatut(StatutCompte.CLOSED);
            compteRepository.save(savings);
            String token = login("alice", ALICE_PASSWORD);

            postTransaction(token, "VIREMENT", "100.00", aliceCurrentId, aliceSavingsId)
                    .andExpect(status().isConflict());

            assertThat(balanceOf(aliceCurrentId)).isEqualByComparingTo("1000.00");
            assertThat(balanceOf(aliceSavingsId)).isEqualByComparingTo("0.00");
            assertThat(transactionRepository.count()).isZero();
        }

        @Test
        void transferToNonExistentAccount_returns404_andSourceUnchanged() throws Exception {
            String token = login("admin", ADMIN_PASSWORD);

            postTransaction(token, "VIREMENT", "10.00", aliceCurrentId, 999_999)
                    .andExpect(status().isNotFound());

            assertThat(balanceOf(aliceCurrentId)).isEqualByComparingTo("1000.00");
        }

        @Test
        void invalidAmount_returns400_andNothingIsPersisted() throws Exception {
            String token = login("admin", ADMIN_PASSWORD);

            postTransaction(token, "DEPOT", "0", null, bobCurrentId).andExpect(status().isBadRequest());
            postTransaction(token, "DEPOT", "-5", null, bobCurrentId).andExpect(status().isBadRequest());

            assertThat(balanceOf(bobCurrentId)).isEqualByComparingTo("100.00");
            assertThat(transactionRepository.count()).isZero();
        }

        @Test
        void closeAccount_withZeroBalance_thenDepositIsRejected() throws Exception {
            String token = login("admin", ADMIN_PASSWORD);

            mockMvc.perform(patch("/api/comptes/" + aliceSavingsId + "/close")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statut").value("CLOSED"));
            postTransaction(token, "DEPOT", "10.00", null, aliceSavingsId)
                    .andExpect(status().isConflict());

            assertThat(balanceOf(aliceSavingsId)).isEqualByComparingTo("0.00");
        }

        @Test
        void closeAccount_withNonZeroBalance_returns409_andStaysActive() throws Exception {
            String token = login("admin", ADMIN_PASSWORD);

            mockMvc.perform(patch("/api/comptes/" + aliceCurrentId + "/close")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict());

            assertThat(compteRepository.findById(aliceCurrentId).orElseThrow().getStatut())
                    .isEqualTo(StatutCompte.ACTIVE);
        }

        @Test
        void clientHistory_containsAllOwnOperationsNewestFirst() throws Exception {
            String admin = login("admin", ADMIN_PASSWORD);
            String aliceToken = login("alice", ALICE_PASSWORD);
            postTransaction(admin, "DEPOT", "10.00", null, aliceCurrentId).andExpect(status().isCreated());
            postTransaction(aliceToken, "RETRAIT", "20.00", aliceCurrentId, null).andExpect(status().isCreated());
            postTransaction(aliceToken, "VIREMENT", "30.00", aliceCurrentId, aliceSavingsId)
                    .andExpect(status().isCreated());
            postTransaction(admin, "DEPOT", "99.00", null, bobCurrentId).andExpect(status().isCreated());

            mockMvc.perform(get("/api/transactions/client/" + alice.getIdClient())
                            .header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].typeTransaction").value("VIREMENT"))
                    .andExpect(jsonPath("$[2].typeTransaction").value("DEPOT"));
        }

        @Test
        void successfulDeposit_sendsNotificationAfterCommit_failedOperationSendsNone() throws Exception {
            String token = login("admin", ADMIN_PASSWORD);

            postTransaction(token, "RETRAIT", "5000.00", bobCurrentId, null)
                    .andExpect(status().isBadRequest());
            verify(mailSender, never()).send(any(SimpleMailMessage.class));

            postTransaction(token, "DEPOT", "10.00", null, bobCurrentId).andExpect(status().isCreated());

            ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
            verify(mailSender).send(mail.capture());
            assertThat(mail.getValue().getTo()).containsExactly("bob@example.test");
            assertThat(mail.getValue().getSubject()).contains("Deposit");
        }
    }

    // ============================================================
    // CONCURRENCY
    // ============================================================

    @Test
    void concurrentWithdrawals_neverOverdrawAccount_thanksToPessimisticLocking() throws Exception {
        Integer accountId = createCurrent(bob, "IT-CONCURRENT", "500.00", "0.00");
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            Callable<Boolean> withdraw = () -> {
                authenticateAs("admin", "ADMIN");
                try {
                    start.await();
                    TransactionRequest request = new TransactionRequest();
                    request.setTypeTransaction(TypeTransaction.RETRAIT);
                    request.setMontant(new BigDecimal("100.00"));
                    request.setIdCompteSource(accountId);
                    transactionService.executeTransaction(request);
                    return true;
                } catch (InsufficientBalanceException expected) {
                    return false;
                } finally {
                    clearAuthentication();
                }
            };
            results.add(pool.submit(withdraw));
        }

        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get(30, TimeUnit.SECONDS)) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(5);
        assertThat(balanceOf(accountId)).isEqualByComparingTo("0.00");
        assertThat(transactionRepository.count()).isEqualTo(5);
    }
}
