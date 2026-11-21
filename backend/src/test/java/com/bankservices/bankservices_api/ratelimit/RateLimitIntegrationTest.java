package com.bankservices.bankservices_api.ratelimit;

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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Rate limiting through the real Spring Security chain (real JWT, H2 database).
 * Small limits keep the tests fast; every test uses its own client IPs / users,
 * because buckets live for the whole application context.
 */
@SpringBootTest(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.login.capacity=3",
        "app.rate-limit.login.window=1m",
        "app.rate-limit.activation.capacity=2",
        "app.rate-limit.activation.window=15m",
        "app.rate-limit.resend-activation.capacity=2",
        "app.rate-limit.password-reset.capacity=2",
        "app.rate-limit.bill-payment.capacity=2",
        "app.rate-limit.transfer.capacity=2",
        "app.rate-limit.general.capacity=4",
        "app.rate-limit.trusted-proxies=10.0.0.0/8"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    private static final AtomicInteger IP_COUNTER = new AtomicInteger(1);

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
    private Integer aliceAccount;
    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        alice = createClient("Alice", "alice.rl@example.test");
        Client bob = createClient("Bob", "bob.rl@example.test");
        createUser("alice", "AliceTestPass1", "CLIENT", alice);
        createUser("bob", "BobTestPass1", "CLIENT", bob);
        aliceAccount = createAccount(alice, "RL-ALICE-1", "500.00");

        // Tokens are issued directly so that setting up never consumes login quota.
        aliceToken = jwtService.generateToken("alice");
        bobToken = jwtService.generateToken("bob");
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

    // ------------------------------------------------------------ helpers

    private static String freshIp() {
        int n = IP_COUNTER.getAndIncrement();
        return "198.51." + (100 + n / 250) + "." + (n % 250 + 1);
    }

    private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder builder, String ip) {
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
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

    private ResultActions login(String ip, String username, String password) throws Exception {
        return mockMvc.perform(from(post("/api/auth/login"), ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    private ResultActions deposit(String token, String ip, String amount) throws Exception {
        return mockMvc.perform(from(post("/api/transactions"), ip)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"typeTransaction":"DEPOT","montant":%s,"description":"rl",
                         "idCompteSource":null,"idCompteDestination":%d}
                        """.formatted(amount, aliceAccount)));
    }

    private ResultActions getBills(String token, String ip) throws Exception {
        return mockMvc.perform(from(get("/api/client/bills"), ip).header("Authorization", "Bearer " + token));
    }

    private BigDecimal balance() {
        return compteRepository.findById(aliceAccount).orElseThrow().getSolde();
    }

    // ============================================================ LOGIN

    @Nested
    @DisplayName("Login limit (per client IP)")
    class Login {

        @Test
        void belowTheLimit_requestsReachTheController_withRateLimitHeaders() throws Exception {
            String ip = freshIp();

            login(ip, "alice", "AliceTestPass1")
                    .andExpect(status().isOk())
                    .andExpect(header().string("RateLimit-Limit", "3"))
                    .andExpect(header().string("RateLimit-Remaining", "2"))
                    .andExpect(header().exists("RateLimit-Reset"))
                    .andExpect(header().doesNotExist("Retry-After"));
        }

        @Test
        void aboveTheLimit_returns429_withConsistentJsonAndRetryAfter() throws Exception {
            String ip = freshIp();
            for (int i = 0; i < 3; i++) {
                login(ip, "alice", "wrong-password").andExpect(status().isBadRequest());
            }

            MvcResult result = login(ip, "alice", "AliceTestPass1")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(429))
                    .andExpect(jsonPath("$.error").value("Too Many Requests"))
                    .andExpect(jsonPath("$.message").value(
                            "You have made too many requests. Please wait before trying again."))
                    .andExpect(jsonPath("$.path").value("/api/auth/login"))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(header().string("RateLimit-Remaining", "0"))
                    .andReturn();

            long retryAfter = Long.parseLong(result.getResponse().getHeader("Retry-After"));
            // 3 per minute, greedy refill: the next attempt is possible within 20 s.
            assertThat(retryAfter).isBetween(1L, 20L);

            // Nothing internal leaks: no key, IP, username or class names in the body.
            String body = result.getResponse().getContentAsString();
            assertThat(body).doesNotContain(ip).doesNotContain("alice").doesNotContain("bucket").doesNotContain("Exception");
        }

        @Test
        void changingTheUsername_doesNotBypassTheLimit() throws Exception {
            String ip = freshIp();
            for (int i = 0; i < 3; i++) {
                login(ip, "user" + i, "whatever123").andExpect(status().isBadRequest());
            }

            login(ip, "someone-else", "whatever123").andExpect(status().isTooManyRequests());
            login(ip, "bob", "BobTestPass1").andExpect(status().isTooManyRequests());
        }

        @Test
        void anotherClientIp_hasItsOwnLimit() throws Exception {
            String blocked = freshIp();
            for (int i = 0; i < 3; i++) {
                login(blocked, "alice", "wrong").andExpect(status().isBadRequest());
            }
            login(blocked, "alice", "AliceTestPass1").andExpect(status().isTooManyRequests());

            login(freshIp(), "alice", "AliceTestPass1").andExpect(status().isOk());
        }

        @Test
        void spoofedForwardedFor_fromAnUntrustedClient_isIgnored() throws Exception {
            String ip = freshIp();
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(from(post("/api/auth/login"), ip)
                                .header("X-Forwarded-For", "203.0.113." + (i + 1))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                        .andExpect(status().isBadRequest());
            }

            mockMvc.perform(from(post("/api/auth/login"), ip)
                            .header("X-Forwarded-For", "203.0.113.200")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        void behindATrustedProxy_eachRealClientHasItsOwnLimit_andPrependedValuesAreIgnored() throws Exception {
            String proxy = "10.1.2.3";
            String realClient = freshIp();

            // The attacker prepends a different fake address on every request.
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(from(post("/api/auth/login"), proxy)
                                .header("X-Forwarded-For", "6.6.6." + i + ", " + realClient)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                        .andExpect(status().isBadRequest());
            }
            mockMvc.perform(from(post("/api/auth/login"), proxy)
                            .header("X-Forwarded-For", "6.6.6.99, " + realClient)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                    .andExpect(status().isTooManyRequests());

            // Another client behind the same proxy is not affected.
            mockMvc.perform(from(post("/api/auth/login"), proxy)
                            .header("X-Forwarded-For", freshIp())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"alice\",\"password\":\"AliceTestPass1\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void concurrentAttempts_cannotExceedTheLimit() throws Exception {
            String ip = freshIp();
            int attempts = 12;
            ExecutorService pool = Executors.newFixedThreadPool(attempts);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> statuses = new ArrayList<>();

            for (int i = 0; i < attempts; i++) {
                Callable<Integer> task = () -> {
                    start.await();
                    return login(ip, "alice", "wrong").andReturn().getResponse().getStatus();
                };
                statuses.add(pool.submit(task));
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

            int allowed = 0;
            int limited = 0;
            for (Future<Integer> status : statuses) {
                if (status.get() == 429) limited++; else allowed++;
            }
            assertThat(allowed).isEqualTo(3);
            assertThat(limited).isEqualTo(attempts - 3);
        }
    }

    // ============================================================ OTHER PUBLIC ENDPOINTS

    @Nested
    @DisplayName("Activation and password reset limits")
    class PublicEndpoints {

        @Test
        void activation_isLimitedPerIp() throws Exception {
            String ip = freshIp();
            String body = "{\"token\":\"forged\",\"password\":\"Password123\",\"confirmPassword\":\"Password123\"}";
            for (int i = 0; i < 2; i++) {
                mockMvc.perform(from(post("/api/auth/activate"), ip).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest());
            }
            mockMvc.perform(from(post("/api/auth/activate"), ip).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"));
        }

        @Test
        void resendActivation_isLimitedPerIp_whateverTheEmail() throws Exception {
            String ip = freshIp();
            for (int i = 0; i < 2; i++) {
                mockMvc.perform(from(post("/api/auth/resend-activation"), ip).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"user" + i + "@example.test\"}"))
                        .andExpect(status().isOk());
            }
            mockMvc.perform(from(post("/api/auth/resend-activation"), ip).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"other@example.test\"}"))
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        void forgotAndResetPassword_shareOneLimit() throws Exception {
            String ip = freshIp();
            mockMvc.perform(from(post("/api/auth/forgot-password"), ip).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"alice.rl@example.test\"}"))
                    .andExpect(status().isOk());
            mockMvc.perform(from(post("/api/auth/reset-password"), ip).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"forged\",\"newPassword\":\"Password123\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(from(post("/api/auth/forgot-password"), ip).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"alice.rl@example.test\"}"))
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        void exhaustedLogin_doesNotAffectActivation() throws Exception {
            String ip = freshIp();
            for (int i = 0; i < 3; i++) {
                login(ip, "alice", "wrong").andExpect(status().isBadRequest());
            }
            login(ip, "alice", "wrong").andExpect(status().isTooManyRequests());

            mockMvc.perform(from(post("/api/auth/activate"), ip).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"forged\",\"password\":\"Password123\",\"confirmPassword\":\"Password123\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ============================================================ AUTHENTICATED USERS

    @Nested
    @DisplayName("Per-user limits for authenticated requests")
    class PerUser {

        @Test
        void differentUsers_haveIndependentLimits_evenFromTheSameIp() throws Exception {
            String sharedIp = freshIp();
            for (int i = 0; i < 4; i++) {
                getBills(aliceToken, sharedIp).andExpect(status().isOk());
            }
            getBills(aliceToken, sharedIp).andExpect(status().isTooManyRequests());

            getBills(bobToken, sharedIp)
                    .andExpect(status().isOk())
                    .andExpect(header().string("RateLimit-Remaining", "3"));
        }

        @Test
        void changingIp_doesNotResetAnAuthenticatedUsersLimit() throws Exception {
            for (int i = 0; i < 4; i++) {
                getBills(aliceToken, freshIp()).andExpect(status().isOk());
            }
            getBills(aliceToken, freshIp()).andExpect(status().isTooManyRequests());
        }

        @Test
        void transferLimit_isIndependentFromTheGeneralLimit() throws Exception {
            String ip = freshIp();
            for (int i = 0; i < 4; i++) {
                getBills(aliceToken, ip).andExpect(status().isOk());
            }
            getBills(aliceToken, ip).andExpect(status().isTooManyRequests());

            deposit(aliceToken, ip, "10.00").andExpect(status().isCreated());
        }

        @Test
        void transfers_areLimited_andRejectedRequestsChangeNothing() throws Exception {
            String ip = freshIp();
            deposit(aliceToken, ip, "10.00").andExpect(status().isCreated());
            deposit(aliceToken, ip, "10.00").andExpect(status().isCreated());

            deposit(aliceToken, ip, "10.00")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.status").value(429));

            assertThat(balance()).isEqualByComparingTo("520.00");
            assertThat(transactionRepository.count()).isEqualTo(2);
        }

        @Test
        void billPayments_areLimited_whileExistingPaymentRulesStillApply() throws Exception {
            String ip = freshIp();
            Integer billId = createBill("RL-BILL-1", "30.00");

            // Existing protection unchanged: another client's attempt is refused by authorization.
            mockMvc.perform(from(post("/api/client/bills/" + billId + "/pay"), freshIp())
                            .header("Authorization", "Bearer " + bobToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"idCompte\":" + aliceAccount + "}"))
                    .andExpect(status().isForbidden());

            // Normal payment works and debits once.
            mockMvc.perform(from(post("/api/client/bills/" + billId + "/pay"), ip)
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"idCompte\":" + aliceAccount + "}"))
                    .andExpect(status().isOk());
            assertThat(balance()).isEqualByComparingTo("470.00");

            // Second attempt: refused by the business rule (already paid), still counted.
            mockMvc.perform(from(post("/api/client/bills/" + billId + "/pay"), ip)
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"idCompte\":" + aliceAccount + "}"))
                    .andExpect(status().isConflict());

            // Third attempt: rate limited before reaching the service.
            mockMvc.perform(from(post("/api/client/bills/" + billId + "/pay"), ip)
                            .header("Authorization", "Bearer " + aliceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"idCompte\":" + aliceAccount + "}"))
                    .andExpect(status().isTooManyRequests());

            assertThat(balance()).isEqualByComparingTo("470.00");
            assertThat(billRepository.findById(billId).orElseThrow().getStatus()).isEqualTo(BillStatus.PAID);
            assertThat(billPaymentRepository.count()).isEqualTo(1);
        }

        private Integer createBill(String reference, String amount) {
            Bill bill = new Bill();
            bill.setReference(reference);
            bill.setProvider("City Power");
            bill.setCategory(BillCategory.ELECTRICITY);
            bill.setAmount(new BigDecimal(amount));
            bill.setIssueDate(LocalDate.now());
            bill.setDueDate(LocalDate.now().plusDays(10));
            bill.setStatus(BillStatus.UNPAID);
            bill.setClient(alice);
            return billRepository.save(bill).getIdBill();
        }
    }

    // ============================================================ EXISTING SECURITY

    @Nested
    @DisplayName("Existing authentication and authorization are unchanged")
    class ExistingSecurity {

        @Test
        void unauthenticatedRequest_still401_andIsCountedPerIp() throws Exception {
            String ip = freshIp();
            for (int i = 0; i < 4; i++) {
                mockMvc.perform(from(get("/api/clients"), ip)).andExpect(status().isUnauthorized());
            }
            mockMvc.perform(from(get("/api/clients"), ip)).andExpect(status().isTooManyRequests());
        }

        @Test
        void clientOnAdminEndpoint_still403() throws Exception {
            mockMvc.perform(from(get("/api/admin/bills"), freshIp()).header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isForbidden())
                    .andExpect(header().exists("RateLimit-Limit"));
        }

        @Test
        void validLogin_stillReturnsAToken() throws Exception {
            login(freshIp(), "alice", "AliceTestPass1")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").exists());
        }

        @Test
        void corsPreflight_isNotRateLimited() throws Exception {
            String ip = freshIp();
            for (int i = 0; i < 10; i++) {
                mockMvc.perform(from(options("/api/auth/login"), ip)
                                .header("Origin", "http://localhost:5173")
                                .header("Access-Control-Request-Method", "POST"))
                        .andExpect(status().isOk())
                        .andExpect(header().doesNotExist("RateLimit-Limit"));
            }
        }

        @Test
        void rateLimitHeaders_areExposedToTheBrowser() throws Exception {
            mockMvc.perform(from(post("/api/auth/login"), freshIp())
                            .header("Origin", "http://localhost:5173")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"alice\",\"password\":\"AliceTestPass1\"}"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Expose-Headers", not(org.hamcrest.Matchers.emptyOrNullString())))
                    .andExpect(header().string("Access-Control-Expose-Headers",
                            org.hamcrest.Matchers.containsString("Retry-After")));
        }
    }
}
