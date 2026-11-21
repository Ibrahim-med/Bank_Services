package com.bankservices.bankservices_api.integration;

import com.bankservices.bankservices_api.entity.Bill;
import com.bankservices.bankservices_api.entity.BillPayment;
import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.entity.CompteCourant;
import com.bankservices.bankservices_api.entity.TransactionBancaire;
import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.enums.BillCategory;
import com.bankservices.bankservices_api.enums.BillStatus;
import com.bankservices.bankservices_api.enums.StatutTransaction;
import com.bankservices.bankservices_api.enums.StatutUtilisateur;
import com.bankservices.bankservices_api.enums.TypeTransaction;
import com.bankservices.bankservices_api.repository.AccountActivationTokenRepository;
import com.bankservices.bankservices_api.repository.BillPaymentRepository;
import com.bankservices.bankservices_api.repository.BillRepository;
import com.bankservices.bankservices_api.repository.ClientRepository;
import com.bankservices.bankservices_api.repository.CompteRepository;
import com.bankservices.bankservices_api.repository.PasswordResetTokenRepository;
import com.bankservices.bankservices_api.repository.TransactionRepository;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;
import com.bankservices.bankservices_api.service.BillPaymentService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests of client registration/activation and bill payment:
 * full Spring context, in-memory H2, real JWT security, real transactions.
 * JavaMailSender is mocked; the e-mails it receives are inspected
 * (e.g. to read the activation link).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillAndActivationIntegrationTest {

    private static final String ADMIN_PASSWORD = "AdminTestPass1";
    private static final String ALICE_PASSWORD = "AliceTestPass1";
    private static final String BOB_PASSWORD = "BobTestPass1";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("activate-account\\?token=([A-Za-z0-9_-]+)");

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BillPaymentService billPaymentService;
    @Autowired private ClientRepository clientRepository;
    @Autowired private CompteRepository compteRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordResetTokenRepository resetTokenRepository;
    @Autowired private AccountActivationTokenRepository activationTokenRepository;
    @Autowired private BillRepository billRepository;
    @Autowired private BillPaymentRepository billPaymentRepository;

    @MockitoBean
    private JavaMailSender mailSender;

    private Client alice;
    private Client bob;
    private Integer aliceAccountId;
    private Integer bobAccountId;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        createUser("admin", ADMIN_PASSWORD, "ADMIN", null);
        alice = createClient("Alice", "alice@example.test");
        bob = createClient("Bob", "bob@example.test");
        createUser("alice", ALICE_PASSWORD, "CLIENT", alice);
        createUser("bob", BOB_PASSWORD, "CLIENT", bob);

        aliceAccountId = createCurrent(alice, "IT-ALICE-0001", "200.00", "0.00");
        bobAccountId = createCurrent(bob, "IT-BOB-0002", "1000.00", "0.00");

        reset(mailSender);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        billPaymentRepository.deleteAll();
        billRepository.deleteAll();
        transactionRepository.deleteAll();
        activationTokenRepository.deleteAll();
        resetTokenRepository.deleteAll();
        utilisateurRepository.deleteAll();
        compteRepository.deleteAll();
        clientRepository.deleteAll();
    }

    // ------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------

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

    private Integer createBill(Client owner, String reference, String amount) {
        Bill bill = new Bill();
        bill.setReference(reference);
        bill.setProvider("City Power");
        bill.setCategory(BillCategory.ELECTRICITY);
        bill.setAmount(new BigDecimal(amount));
        bill.setIssueDate(LocalDate.now().minusDays(10));
        bill.setDueDate(LocalDate.now().plusDays(20));
        bill.setStatus(BillStatus.UNPAID);
        bill.setClient(owner);
        return billRepository.save(bill).getIdBill();
    }

    private BigDecimal balanceOf(Integer accountId) {
        return compteRepository.findById(accountId).orElseThrow().getSolde();
    }

    private BillStatus statusOf(Integer billId) {
        return billRepository.findById(billId).orElseThrow().getStatus();
    }

    private long billPaymentTransactions() {
        return transactionRepository.findAll().stream()
                .filter(tx -> tx.getTypeTransaction() == TypeTransaction.PAIEMENT_FACTURE)
                .count();
    }

    private ResultActions loginRequest(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    private String login(String username, String password) throws Exception {
        String body = loginRequest(username, password)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private ResultActions pay(String token, Integer billId, Integer accountId) throws Exception {
        return mockMvc.perform(post("/api/client/bills/" + billId + "/pay")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idCompte\":" + accountId + "}"));
    }

    private List<SimpleMailMessage> sentMails() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeast(0)).send(captor.capture());
        return captor.getAllValues();
    }

    private static String activationTokenFrom(SimpleMailMessage mail) {
        Matcher matcher = TOKEN_IN_LINK.matcher(mail.getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private ResultActions activate(String token, String password, String confirm) throws Exception {
        return mockMvc.perform(post("/api/auth/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}"
                        .formatted(token, password, confirm)));
    }

    // ============================================================
    // REGISTRATION & ACTIVATION
    // ============================================================

    @Nested
    @DisplayName("Client registration and activation, end to end")
    class RegistrationAndActivation {

        private static final String NEW_CLIENT = """
                {"nom":"Dupont","prenom":"Élise","email":"elise@example.test","telephone":"0611111111"}
                """;

        @Test
        void adminRegisters_clientActivates_thenLogsIn() throws Exception {
            String adminToken = login("admin", ADMIN_PASSWORD);

            // 1. Admin registers the client
            String body = mockMvc.perform(post("/api/admin/clients")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(NEW_CLIENT))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("edupont"))
                    .andExpect(jsonPath("$.accountStatus").value("PENDING_ACTIVATION"))
                    .andReturn().getResponse().getContentAsString();
            Integer idClient = JsonPath.read(body, "$.idClient");

            Utilisateur pending = utilisateurRepository.findByUsername("edupont").orElseThrow();
            assertThat(pending.getStatut()).isEqualTo(StatutUtilisateur.PENDING_ACTIVATION);
            assertThat(pending.getClient().getIdClient()).isEqualTo(idClient);
            assertThat(activationTokenRepository.count()).isEqualTo(1);

            // 2. Activation e-mail sent after commit: username + link, no password
            List<SimpleMailMessage> mails = sentMails();
            assertThat(mails).hasSize(1);
            SimpleMailMessage mail = mails.getFirst();
            assertThat(mail.getTo()).containsExactly("elise@example.test");
            assertThat(mail.getText()).contains("Your username: edupont");
            String rawToken = activationTokenFrom(mail);

            // Only the hash is stored
            assertThat(activationTokenRepository.findAll().getFirst().getTokenHash()).isNotEqualTo(rawToken);

            // 3. Login refused while pending (even the random initial password is unknown)
            loginRequest("edupont", "Anything123").andExpect(status().isBadRequest());

            // 4. Mismatched confirmation is rejected and the token stays usable
            activate(rawToken, "ElisePass123", "Different123").andExpect(status().isBadRequest());

            // 5. Activation
            activate(rawToken, "ElisePass123", "ElisePass123")
                    .andExpect(status().isOk());

            Utilisateur active = utilisateurRepository.findByUsername("edupont").orElseThrow();
            assertThat(active.getStatut()).isEqualTo(StatutUtilisateur.ACTIVE);
            assertThat(active.getPassword()).isNotEqualTo("ElisePass123");
            assertThat(passwordEncoder.matches("ElisePass123", active.getPassword())).isTrue();

            // 6. Login now works and returns the client id
            loginRequest("edupont", "ElisePass123")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("CLIENT"))
                    .andExpect(jsonPath("$.idClient").value(idClient));

            // 7. The link cannot be used twice
            activate(rawToken, "Another12345", "Another12345")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("This activation link has already been used."));
        }

        @Test
        void pendingUser_withCorrectPassword_getsExplicit403() throws Exception {
            createUser("pending1", "PendingPass1", "CLIENT", createClient("Pat", "pat@example.test"));
            Utilisateur user = utilisateurRepository.findByUsername("pending1").orElseThrow();
            user.setStatut(StatutUtilisateur.PENDING_ACTIVATION);
            utilisateurRepository.save(user);

            loginRequest("pending1", "PendingPass1")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not activated")));
        }

        @Test
        void disabledUser_isRejectedAtLogin_andItsExistingTokenStopsWorking() throws Exception {
            String aliceToken = login("alice", ALICE_PASSWORD);

            Utilisateur aliceUser = utilisateurRepository.findByUsername("alice").orElseThrow();
            aliceUser.setStatut(StatutUtilisateur.DISABLED);
            utilisateurRepository.save(aliceUser);

            loginRequest("alice", ALICE_PASSWORD)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("disabled")));

            mockMvc.perform(get("/api/client/bills").header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void duplicateEmail_isRejectedWith409() throws Exception {
            String adminToken = login("admin", ADMIN_PASSWORD);

            mockMvc.perform(post("/api/admin/clients")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nom\":\"Other\",\"prenom\":\"Alice\",\"email\":\"ALICE@example.test\"}"))
                    .andExpect(status().isConflict());

            assertThat(clientRepository.count()).isEqualTo(2);
        }

        @Test
        void sameName_getsADifferentUsername() throws Exception {
            String adminToken = login("admin", ADMIN_PASSWORD);

            for (String email : List.of("e1@example.test", "e2@example.test")) {
                mockMvc.perform(post("/api/admin/clients")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nom\":\"Dupont\",\"prenom\":\"Eric\",\"email\":\"%s\"}".formatted(email)))
                        .andExpect(status().isCreated());
            }

            assertThat(utilisateurRepository.existsByUsername("edupont")).isTrue();
            assertThat(utilisateurRepository.existsByUsername("edupont1")).isTrue();
        }

        @Test
        void resend_invalidatesThePreviousLink() throws Exception {
            String adminToken = login("admin", ADMIN_PASSWORD);

            String body = mockMvc.perform(post("/api/admin/clients")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(NEW_CLIENT))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            Integer idClient = JsonPath.read(body, "$.idClient");

            mockMvc.perform(post("/api/admin/clients/" + idClient + "/activation-email")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());

            List<SimpleMailMessage> mails = sentMails();
            assertThat(mails).hasSize(2);
            String firstToken = activationTokenFrom(mails.get(0));
            String secondToken = activationTokenFrom(mails.get(1));

            activate(firstToken, "ElisePass123", "ElisePass123").andExpect(status().isBadRequest());
            activate(secondToken, "ElisePass123", "ElisePass123").andExpect(status().isOk());
        }

        @Test
        void expiredLink_isRejected() throws Exception {
            String adminToken = login("admin", ADMIN_PASSWORD);
            mockMvc.perform(post("/api/admin/clients")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(NEW_CLIENT))
                    .andExpect(status().isCreated());
            String rawToken = activationTokenFrom(sentMails().getFirst());

            var stored = activationTokenRepository.findAll().getFirst();
            stored.setExpiryDate(LocalDateTime.now().minusMinutes(1));
            activationTokenRepository.save(stored);

            activate(rawToken, "ElisePass123", "ElisePass123")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("expired")));

            assertThat(utilisateurRepository.findByUsername("edupont").orElseThrow().getStatut())
                    .isEqualTo(StatutUtilisateur.PENDING_ACTIVATION);
        }

        @Test
        void clientCannotRegisterClients() throws Exception {
            String aliceToken = login("alice", ALICE_PASSWORD);

            mockMvc.perform(post("/api/admin/clients")
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(NEW_CLIENT))
                    .andExpect(status().isForbidden());

            assertThat(clientRepository.count()).isEqualTo(2);
        }
    }

    // ============================================================
    // BILL PAYMENT
    // ============================================================

    @Nested
    @DisplayName("Bill payment, end to end")
    class BillPaymentFlow {

        @Test
        void adminIssuesBill_clientPaysIt_balanceHistoryAndReceiptAreConsistent() throws Exception {
            String adminToken = login("admin", ADMIN_PASSWORD);
            String aliceToken = login("alice", ALICE_PASSWORD);

            // Admin issues the bill
            String created = mockMvc.perform(post("/api/admin/bills")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"reference":"ELEC-IT-1","provider":"City Power","category":"ELECTRICITY",
                                     "amount":75.25,"issueDate":"%s","dueDate":"%s","idClient":%d}
                                    """.formatted(LocalDate.now(), LocalDate.now().plusDays(30), alice.getIdClient())))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            Integer billId = JsonPath.read(created, "$.idBill");

            // Client sees it as unpaid
            mockMvc.perform(get("/api/client/bills/unpaid").header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].reference").value("ELEC-IT-1"));

            // Client pays it (any amount in the body is ignored)
            String receipt = mockMvc.perform(post("/api/client/bills/" + billId + "/pay")
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"idCompte\":" + aliceAccountId + ",\"amount\":0.01}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amount").value(75.25))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.accountNumberMasked").value("****0001"))
                    .andReturn().getResponse().getContentAsString();
            Integer paymentId = JsonPath.read(receipt, "$.idBillPayment");
            Integer transactionId = JsonPath.read(receipt, "$.idTransaction");

            // Balance and bill
            assertThat(balanceOf(aliceAccountId)).isEqualByComparingTo("124.75");
            assertThat(statusOf(billId)).isEqualTo(BillStatus.PAID);

            // Transaction history (existing endpoint) contains the bill payment
            mockMvc.perform(get("/api/transactions/client/" + alice.getIdClient())
                            .header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].idTransaction").value(transactionId))
                    .andExpect(jsonPath("$[0].typeTransaction").value("PAIEMENT_FACTURE"))
                    .andExpect(jsonPath("$[0].montant").value(75.25))
                    .andExpect(jsonPath("$[0].idCompteSource").value(aliceAccountId));

            // Payment history and receipt
            mockMvc.perform(get("/api/client/bill-payments").header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].billReference").value("ELEC-IT-1"));
            mockMvc.perform(get("/api/client/bill-payments/" + paymentId + "/receipt")
                            .header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.provider").value("City Power"))
                    .andExpect(jsonPath("$.accountType").value("COURANT"));

            // Paid bill now appears as paid, with its payment reference
            mockMvc.perform(get("/api/client/bills/" + billId).header("Authorization", "Bearer " + aliceToken))
                    .andExpect(jsonPath("$.status").value("PAID"))
                    .andExpect(jsonPath("$.idBillPayment").value(paymentId));

            // Second attempt is refused and nothing moves
            pay(aliceToken, billId, aliceAccountId).andExpect(status().isConflict());
            assertThat(balanceOf(aliceAccountId)).isEqualByComparingTo("124.75");
            assertThat(billPaymentRepository.count()).isEqualTo(1);
            assertThat(billPaymentTransactions()).isEqualTo(1);
        }

        @Test
        void insufficientFunds_changesNothing() throws Exception {
            Integer billId = createBill(alice, "BIG-1", "200.01");
            String aliceToken = login("alice", ALICE_PASSWORD);

            pay(aliceToken, billId, aliceAccountId)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Insufficient available balance."));

            assertThat(balanceOf(aliceAccountId)).isEqualByComparingTo("200.00");
            assertThat(statusOf(billId)).isEqualTo(BillStatus.UNPAID);
            assertThat(billPaymentRepository.count()).isZero();
            assertThat(billPaymentTransactions()).isZero();
        }

        @Test
        void anotherClientsBill_isForbidden_andUntouched() throws Exception {
            Integer bobBill = createBill(bob, "BOB-1", "10.00");
            String aliceToken = login("alice", ALICE_PASSWORD);

            pay(aliceToken, bobBill, aliceAccountId).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/client/bills/" + bobBill).header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isForbidden());

            assertThat(statusOf(bobBill)).isEqualTo(BillStatus.UNPAID);
            assertThat(balanceOf(aliceAccountId)).isEqualByComparingTo("200.00");
        }

        @Test
        void anotherClientsAccount_isForbidden_andNotDebited() throws Exception {
            Integer aliceBill = createBill(alice, "ALICE-1", "10.00");
            String aliceToken = login("alice", ALICE_PASSWORD);

            pay(aliceToken, aliceBill, bobAccountId).andExpect(status().isForbidden());

            assertThat(balanceOf(bobAccountId)).isEqualByComparingTo("1000.00");
            assertThat(statusOf(aliceBill)).isEqualTo(BillStatus.UNPAID);
        }

        @Test
        void unknownBill_returns404() throws Exception {
            String aliceToken = login("alice", ALICE_PASSWORD);

            pay(aliceToken, 999_999, aliceAccountId).andExpect(status().isNotFound());
            assertThat(balanceOf(aliceAccountId)).isEqualByComparingTo("200.00");
        }

        @Test
        void adminCannotPayOnBehalfOfAClient() throws Exception {
            Integer aliceBill = createBill(alice, "ALICE-2", "10.00");
            String adminToken = login("admin", ADMIN_PASSWORD);

            pay(adminToken, aliceBill, aliceAccountId).andExpect(status().isForbidden());
            assertThat(statusOf(aliceBill)).isEqualTo(BillStatus.UNPAID);
        }

        @Test
        void concurrentPaymentsOfTheSameBill_debitExactlyOnce() throws Exception {
            Integer billId = createBill(alice, "RACE-1", "50.00");
            int attempts = 6;

            ExecutorService pool = Executors.newFixedThreadPool(attempts);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> results = new ArrayList<>();

            for (int i = 0; i < attempts; i++) {
                Callable<String> task = () -> {
                    start.await();
                    try {
                        billPaymentService.payBill("alice", billId, aliceAccountId);
                        return "OK";
                    } catch (IllegalStateException alreadyPaid) {
                        return "REJECTED";
                    }
                };
                results.add(pool.submit(task));
            }

            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get());
            }

            assertThat(outcomes).filteredOn("OK"::equals).hasSize(1);
            assertThat(outcomes).filteredOn("REJECTED"::equals).hasSize(attempts - 1);

            assertThat(balanceOf(aliceAccountId)).isEqualByComparingTo("150.00");
            assertThat(statusOf(billId)).isEqualTo(BillStatus.PAID);
            assertThat(billPaymentRepository.count()).isEqualTo(1);
            assertThat(billPaymentTransactions()).isEqualTo(1);
        }

        @Test
        void failureAfterTheDebit_rollsBackEverything() throws Exception {
            Integer billId = createBill(alice, "ROLLBACK-1", "40.00");

            // Simulate inconsistent data: a payment row already exists for this
            // UNPAID bill. The payment passes every business check, debits the
            // account, records the transaction and marks the bill PAID, then the
            // UNIQUE(id_bill) constraint fails when the payment row is inserted.
            Bill bill = billRepository.findById(billId).orElseThrow();
            TransactionBancaire unrelated = new TransactionBancaire();
            unrelated.setTypeTransaction(TypeTransaction.DEPOT);
            unrelated.setMontant(new BigDecimal("1.00"));
            unrelated.setStatut(StatutTransaction.VALIDEE);
            unrelated.setCompteDestination(compteRepository.findById(bobAccountId).orElseThrow());
            unrelated = transactionRepository.save(unrelated);

            BillPayment orphan = new BillPayment();
            orphan.setReference("PAY-ORPHAN-0001");
            orphan.setBill(bill);
            orphan.setCompte(compteRepository.findById(bobAccountId).orElseThrow());
            orphan.setTransaction(unrelated);
            orphan.setAmount(new BigDecimal("40.00"));
            orphan.setPaymentDate(LocalDateTime.now());
            billPaymentRepository.save(orphan);

            long transactionsBefore = transactionRepository.count();
            String aliceToken = login("alice", ALICE_PASSWORD);

            pay(aliceToken, billId, aliceAccountId).andExpect(status().isConflict());

            // Nothing from the failed payment survived
            assertThat(balanceOf(aliceAccountId)).isEqualByComparingTo("200.00");
            assertThat(statusOf(billId)).isEqualTo(BillStatus.UNPAID);
            assertThat(billRepository.findById(billId).orElseThrow().getPaidAt()).isNull();
            assertThat(transactionRepository.count()).isEqualTo(transactionsBefore);
            assertThat(billPaymentRepository.count()).isEqualTo(1);
            verify(mailSender, never()).send(any(SimpleMailMessage.class));
        }

        @Test
        void clientFilters_andOverdueFlag() throws Exception {
            Integer overdue = createBill(alice, "OLD-1", "5.00");
            Bill bill = billRepository.findById(overdue).orElseThrow();
            bill.setIssueDate(LocalDate.now().minusDays(60));
            bill.setDueDate(LocalDate.now().minusDays(1));
            billRepository.save(bill);
            createBill(alice, "NEW-1", "5.00");
            createBill(bob, "BOB-9", "5.00");
            String aliceToken = login("alice", ALICE_PASSWORD);

            mockMvc.perform(get("/api/client/bills").header("Authorization", "Bearer " + aliceToken))
                    .andExpect(jsonPath("$", hasSize(2)));
            mockMvc.perform(get("/api/client/bills").param("status", "OVERDUE")
                            .header("Authorization", "Bearer " + aliceToken))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].reference").value("OLD-1"))
                    .andExpect(jsonPath("$[0].overdue").value(true));
            mockMvc.perform(get("/api/client/bills").param("reference", "new")
                            .header("Authorization", "Bearer " + aliceToken))
                    .andExpect(jsonPath("$", hasSize(1)));
        }
    }
}
