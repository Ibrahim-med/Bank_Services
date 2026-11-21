package com.bankservices.bankservices_api.controller;

import com.bankservices.bankservices_api.config.SecurityConfig;
import com.bankservices.bankservices_api.dto.response.TransactionResponse;
import com.bankservices.bankservices_api.enums.StatutTransaction;
import com.bankservices.bankservices_api.enums.TypeTransaction;
import com.bankservices.bankservices_api.exception.InsufficientBalanceException;
import com.bankservices.bankservices_api.exception.ResourceNotFoundException;
import com.bankservices.bankservices_api.service.TransactionService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for {@link TransactionController}: executing operations,
 * validation of amounts, error mapping of financial failures and role rules
 * on the history endpoints.
 */
@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
class TransactionControllerTest extends AbstractControllerTest {

    private static final String VALID_DEPOSIT = """
            {"typeTransaction":"DEPOT","montant":100.00,"description":"Test",
             "idCompteDestination":1}
            """;

    @MockitoBean
    private TransactionService transactionService;

    private TransactionResponse deposit() {
        return new TransactionResponse(7, TypeTransaction.DEPOT, new BigDecimal("100.00"),
                LocalDateTime.of(2026, 3, 1, 10, 30), "Test", StatutTransaction.VALIDEE, null, 1);
    }

    // ---------------- execute ----------------

    @Test
    void executeTransaction_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(VALID_DEPOSIT))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(transactionService);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void executeTransaction_asAuthenticatedUser_returns201WithTransaction() throws Exception {
        when(transactionService.executeTransaction(any())).thenReturn(deposit());

        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(VALID_DEPOSIT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idTransaction").value(7))
                .andExpect(jsonPath("$.typeTransaction").value("DEPOT"))
                .andExpect(jsonPath("$.montant").value(100.00))
                .andExpect(jsonPath("$.statut").value("VALIDEE"))
                .andExpect(jsonPath("$.idCompteDestination").value(1));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void executeTransaction_withZeroAmount_returns400AndServiceNotCalled() throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"typeTransaction":"DEPOT","montant":0,"idCompteDestination":1}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("montant")));

        verifyNoInteractions(transactionService);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void executeTransaction_withNegativeAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"typeTransaction":"RETRAIT","montant":-10,"idCompteSource":1}
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void executeTransaction_withMissingTypeAndAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"idCompteDestination":1}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("typeTransaction")))
                .andExpect(jsonPath("$.message", containsString("montant")));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void executeTransaction_withOverlongDescription_returns400() throws Exception {
        String longText = "x".repeat(256);
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"typeTransaction":"DEPOT","montant":10,"idCompteDestination":1,"description":"%s"}
                        """.formatted(longText)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("description")));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void executeTransaction_withInsufficientBalance_returns400WithMessage() throws Exception {
        when(transactionService.executeTransaction(any()))
                .thenThrow(new InsufficientBalanceException("Insufficient available balance."));

        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"typeTransaction":"RETRAIT","montant":999999,"idCompteSource":1}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient available balance."));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void executeTransaction_onNonExistentAccount_returns404() throws Exception {
        when(transactionService.executeTransaction(any()))
                .thenThrow(new ResourceNotFoundException("Account not found: 404"));

        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(VALID_DEPOSIT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found: 404"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void executeTransaction_onAccountNotOwned_returns403() throws Exception {
        when(transactionService.executeTransaction(any()))
                .thenThrow(new AccessDeniedException("You are not authorized to use this account"));

        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(VALID_DEPOSIT))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void executeTransaction_onClosedAccount_returns409() throws Exception {
        when(transactionService.executeTransaction(any())).thenThrow(new IllegalStateException(
                "This account is closed and cannot be used for transactions."));

        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(VALID_DEPOSIT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("closed")));
    }

    // ---------------- history ----------------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getAllTransactions_asAdmin_returnsList() throws Exception {
        when(transactionService.getAllTransactions()).thenReturn(List.of(deposit()));

        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idTransaction").value(7));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void getAllTransactions_asClient_returns403() throws Exception {
        mockMvc.perform(get("/api/transactions")).andExpect(status().isForbidden());

        verifyNoInteractions(transactionService);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void getTransactionsByClient_asClient_returnsHistory() throws Exception {
        when(transactionService.getTransactionsByClient(1)).thenReturn(List.of(deposit()));

        mockMvc.perform(get("/api/transactions/client/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    /** Documents CURRENT behaviour: this endpoint is CLIENT-only, so ADMIN gets 403. */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getTransactionsByClient_asAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/transactions/client/1")).andExpect(status().isForbidden());

        verifyNoInteractions(transactionService);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void getTransactionById_asAuthenticatedUser_delegatesToServiceAuthorization() throws Exception {
        when(transactionService.getTransactionById(7)).thenReturn(deposit());

        mockMvc.perform(get("/api/transactions/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idTransaction").value(7));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void getTransactionById_whenServiceDeniesAccess_returns403() throws Exception {
        when(transactionService.getTransactionById(8))
                .thenThrow(new AccessDeniedException("You are not authorized to use this account"));

        mockMvc.perform(get("/api/transactions/8")).andExpect(status().isForbidden());
    }
}
