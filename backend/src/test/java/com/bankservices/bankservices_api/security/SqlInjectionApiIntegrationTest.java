package com.bankservices.bankservices_api.security;

import com.bankservices.bankservices_api.entity.Bill;
import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.entity.CompteCourant;
import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.enums.BillCategory;
import com.bankservices.bankservices_api.enums.BillStatus;
import com.bankservices.bankservices_api.repository.AccountActivationTokenRepository;
import com.bankservices.bankservices_api.repository.BillPaymentRepository;
import com.bankservices.bankservices_api.repository.BillRepository;
import com.bankservices.bankservices_api.repository.ClientRepository;
import com.bankservices.bankservices_api.repository.CompteRepository;
import com.bankservices.bankservices_api.repository.PasswordResetTokenRepository;
import com.bankservices.bankservices_api.repository.TransactionRepository;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;
import com.bankservices.bankservices_api.service.JwtService;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SQL injection attempts through the real HTTP API (full Spring context, real JWT,
 * isolated in-memory H2 database from the test profile - never production data).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SqlInjectionApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ClientRepository clientRepository;
    @Autowired private CompteRepository compteRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private BillRepository billRepository;
    @Autowired private BillPaymentRepository billPaymentRepository;
    @Autowired private AccountActivationTokenRepository activationTokenRepository;
    @Autowired private PasswordResetTokenRepository resetTokenRepository;

    @MockitoBean private JavaMailSender mailSender;

    private Client alice;
    private Client bob;
    private Integer aliceAccount;
    private Integer aliceSecondAccount;
    private Integer bobAccount;
    private String adminToken;
    private String aliceToken;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        alice = createClient("Alice", "alice.sqli@example.test");
        bob = createClient("Bob", "bob.sqli@example.test");
        createUser("admin", "AdminTestPass1", "ADMIN", null);
        createUser("alice", "AliceTestPass1", "CLIENT", alice);
        createUser("bob", "BobTestPass1", "CLIENT", bob);
        aliceAccount = createAccount(alice, "SQLI-ALICE-1", "500.00");
        aliceSecondAccount = createAccount(alice, "SQLI-ALICE-2", "0.00");
        bobAccount = createAccount(bob, "SQLI-BOB-1", "300.00");
        createBill(alice, "ALICE-ELEC-1", BillCategory.ELECTRICITY);
        createBill(alice, "ALICE-WATER-2", BillCategory.WATER);
        createBill(bob, "BOB-SECRET-9", BillCategory.INTERNET);

        adminToken = jwtService.generateToken("admin");
        aliceToken = jwtService.generateToken("alice");
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

    private Integer createAccount(Client owner, String numero, String balance) {
        CompteCourant compte = new CompteCourant();
        compte.setNumero(numero);
        compte.setSolde(new BigDecimal(balance));
        compte.setDecouvertAutorise(BigDecimal.ZERO);
        compte.setClient(owner);
        return compteRepository.save(compte).getIdCompte();
    }

    private void createBill(Client owner, String reference, BillCategory category) {
        Bill bill = new Bill();
        bill.setReference(reference);
        bill.setProvider("Provider");
        bill.setCategory(category);
        bill.setAmount(new BigDecimal("12.00"));
        bill.setIssueDate(LocalDate.now());
        bill.setDueDate(LocalDate.now().plusDays(10));
        bill.setStatus(BillStatus.UNPAID);
        bill.setClient(owner);
        billRepository.save(bill);
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("username", username, "password", password)));
    }

    /** Builds a JSON object with properly escaped string values. */
    private static String json(String... keyValues) {
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) builder.append(',');
            builder.append('"').append(keyValues[i]).append("\":\"")
                    .append(keyValues[i + 1].replace("\\", "\\\\").replace("\"", "\\\"")
                            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
                            .replace("\u0000", "\\u0000"))
                    .append('"');
        }
        return builder.append('}').toString();
    }

    // ============================================================ AUTHENTICATION

    @Nested
    @DisplayName("Login")
    class Login {

        @ParameterizedTest
        @ValueSource(strings = {"' OR '1'='1", "' OR 1=1 --", "admin'--", "admin' #", "\" OR \"\"=\"",
                "alice' AND '1'='1", "' UNION SELECT 1,2,3 --"})
        void injectionInUsername_neverAuthenticates(String payload) throws Exception {
            login(payload, "anything123")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid username or password"))
                    .andExpect(jsonPath("$.token").doesNotExist());
        }

        @ParameterizedTest
        @ValueSource(strings = {"' OR '1'='1", "' OR 1=1 --", "x' OR 'x'='x"})
        void injectionInPassword_neverAuthenticates(String payload) throws Exception {
            login("alice", payload).andExpect(status().isBadRequest());
        }

        @Test
        void legitimateLogin_stillWorks() throws Exception {
            login("alice", "AliceTestPass1")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").exists())
                    .andExpect(jsonPath("$.idClient").value(alice.getIdClient()));
        }

        @Test
        void oversizedCredentials_areRejectedByValidation() throws Exception {
            login("u".repeat(500), "p".repeat(500))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("too long")));
        }
    }

    // ============================================================ CLIENT DATA

    @Nested
    @DisplayName("Client registration and updates")
    class ClientData {

        @Test
        void specialCharacters_areStoredAsOrdinaryData() throws Exception {
            String body = json(
                    "nom", "O'Brien'); DROP TABLE client; --",
                    "prenom", "Zoë-Ann \"Jo\"",
                    "email", "o'brien+bank@example.com",
                    "telephone", "+213 (0)5 50-12-34",
                    "adresse", "12 rue de l'Église\nApt. 3 % _ ;");

            String response = mockMvc.perform(post("/api/admin/clients")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Integer id = JsonPath.read(response, "$.idClient");
            Client stored = clientRepository.findById(id).orElseThrow();
            assertThat(stored.getNom()).isEqualTo("O'Brien'); DROP TABLE client; --");
            assertThat(stored.getPrenom()).isEqualTo("Zoë-Ann \"Jo\"");
            assertThat(stored.getEmail()).isEqualTo("o'brien+bank@example.com");
            assertThat(stored.getAdresse()).isEqualTo("12 rue de l'Église\nApt. 3 % _ ;");
            assertThat(clientRepository.count()).isEqualTo(3);
        }

        @Test
        void duplicateEmailCheck_isNotFooledByInjection() throws Exception {
            mockMvc.perform(post("/api/admin/clients")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("nom", "X", "prenom", "Y", "email", "alice.sqli@example.test' OR '1'='1")))
                    .andExpect(status().isBadRequest());   // not a valid e-mail address

            mockMvc.perform(post("/api/admin/clients")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("nom", "X", "prenom", "Y", "email", "ALICE.SQLI@example.test")))
                    .andExpect(status().isConflict());     // real duplicate still detected
        }

        @Test
        void controlCharactersAndBadPhones_areRejected() throws Exception {
            mockMvc.perform(put("/api/clients/" + alice.getIdClient())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("nom", "Test\u0000", "prenom", "Alice", "email", "alice.sqli@example.test")))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(put("/api/clients/" + alice.getIdClient())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("nom", "Test", "prenom", "Alice", "email", "alice.sqli@example.test",
                                    "telephone", "0550' OR 1=1")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Phone")));

            assertThat(clientRepository.findById(alice.getIdClient()).orElseThrow().getNom()).isEqualTo("Test");
        }

        @Test
        void injectionInPathVariable_isABadRequest_andLeaksNothing() throws Exception {
            String body = mockMvc.perform(get("/api/clients/1 OR 1=1").header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).doesNotContain("Exception").doesNotContain("java.").doesNotContain("SELECT");
        }
    }

    // ============================================================ SEARCH / FILTERS

    @Nested
    @DisplayName("Bill search and filters")
    class Search {

        /** Query parameters are passed raw: the server receives exactly the payload. */
        private ResultActions bills(String... nameValues) throws Exception {
            var request = get("/api/client/bills").header("Authorization", "Bearer " + aliceToken);
            for (int i = 0; i < nameValues.length; i += 2) {
                request = request.param(nameValues[i], nameValues[i + 1]);
            }
            return mockMvc.perform(request);
        }

        @Test
        void normalFilters_stillWork() throws Exception {
            bills().andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
            bills("reference", "elec").andExpect(jsonPath("$", hasSize(1)));
            bills("category", "WATER").andExpect(jsonPath("$", hasSize(1)));
            bills("status", "UNPAID").andExpect(jsonPath("$", hasSize(2)));
        }

        @ParameterizedTest
        @ValueSource(strings = {"' OR '1'='1", "%", "%' OR '%'='", "' UNION SELECT * FROM bill --", "_", "BOB-SECRET-9"})
        void injectionInReference_matchesNothing_andNeverExposesOtherClientsBills(String payload) throws Exception {
            bills("reference", payload)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void injectionInStatus_isRejectedByTheAllowList() throws Exception {
            bills("status", "PAID' OR '1'='1")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Unknown bill status")));
        }

        @Test
        void injectionInCategory_isRejectedByTheEnum() throws Exception {
            bills("category", "WATER' OR '1'='1")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid value for parameter 'category'."));
        }

        @Test
        void oversizedSearchTerm_isRejected() throws Exception {
            bills("reference", "A".repeat(41))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    // ============================================================ BANKING OPERATIONS

    @Nested
    @DisplayName("Banking operations and ownership")
    class Banking {

        private ResultActions transaction(String json) throws Exception {
            return mockMvc.perform(post("/api/transactions")
                    .header("Authorization", "Bearer " + aliceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json));
        }

        @Test
        void transferWithSqlLookingDescription_isStoredLiterally_andBalancesAreCorrect() throws Exception {
            transaction("""
                    {"typeTransaction":"VIREMENT","montant":25.50,
                     "description":"Rent'); UPDATE compte_bancaire SET solde=999999; --",
                     "idCompteSource":%d,"idCompteDestination":%d}
                    """.formatted(aliceAccount, aliceSecondAccount))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.description").value("Rent'); UPDATE compte_bancaire SET solde=999999; --"));

            assertThat(compteRepository.findById(aliceAccount).orElseThrow().getSolde()).isEqualByComparingTo("474.50");
            assertThat(compteRepository.findById(aliceSecondAccount).orElseThrow().getSolde()).isEqualByComparingTo("25.50");
            assertThat(compteRepository.findById(bobAccount).orElseThrow().getSolde()).isEqualByComparingTo("300.00");
        }

        @Test
        void amountBeyondDatabasePrecision_isAValidationError_notADatabaseError() throws Exception {
            transaction("""
                    {"typeTransaction":"DEPOT","montant":10.005,"idCompteSource":null,"idCompteDestination":%d}
                    """.formatted(aliceAccount))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("montant")));

            assertThat(compteRepository.findById(aliceAccount).orElseThrow().getSolde()).isEqualByComparingTo("500.00");
        }

        @Test
        void accountIdAsInjectionString_isRejectedAsMalformed() throws Exception {
            transaction("""
                    {"typeTransaction":"DEPOT","montant":10,"idCompteSource":null,"idCompteDestination":"1 OR 1=1"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Malformed request body."));
        }

        @Test
        void ownershipChecks_areUnchanged() throws Exception {
            // Alice cannot move Bob's money.
            transaction("""
                    {"typeTransaction":"RETRAIT","montant":10,"idCompteSource":%d,"idCompteDestination":null}
                    """.formatted(bobAccount))
                    .andExpect(status().isForbidden());
            assertThat(compteRepository.findById(bobAccount).orElseThrow().getSolde()).isEqualByComparingTo("300.00");

            // Alice cannot read Bob's profile or accounts.
            mockMvc.perform(get("/api/clients/" + bob.getIdClient()).header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/comptes/client/" + bob.getIdClient()).header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        void accountCreation_rejectsInjectionInTheAccountNumber() throws Exception {
            mockMvc.perform(post("/api/comptes")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"numero":"ACC9' OR '1'='1","typeCompte":"COURANT","idClient":%d}
                                    """.formatted(alice.getIdClient())))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(post("/api/comptes")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"numero":"ACC-2026-0099","typeCompte":"COURANT","idClient":%d}
                                    """.formatted(alice.getIdClient())))
                    .andExpect(status().isCreated());
        }
    }
}
