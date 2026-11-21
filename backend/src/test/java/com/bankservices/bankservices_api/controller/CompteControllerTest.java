package com.bankservices.bankservices_api.controller;

import com.bankservices.bankservices_api.config.SecurityConfig;
import com.bankservices.bankservices_api.dto.response.CompteResponse;
import com.bankservices.bankservices_api.enums.StatutCompte;
import com.bankservices.bankservices_api.enums.TypeCompte;
import com.bankservices.bankservices_api.exception.ResourceNotFoundException;
import com.bankservices.bankservices_api.service.CompteService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for {@link CompteController}: account creation, retrieval,
 * close/reopen, role restrictions, account ownership and validation.
 */
@WebMvcTest(CompteController.class)
@Import(SecurityConfig.class)
class CompteControllerTest extends AbstractControllerTest {

    private static final String VALID_ACCOUNT = """
            {"numero":"TEST-ACC-1","typeCompte":"COURANT","decouvertAutorise":500.00,
             "tauxInteret":0,"idClient":1}
            """;

    @MockitoBean
    private CompteService compteService;

    private CompteResponse account(StatutCompte statut) {
        return new CompteResponse(1, "TEST-ACC-1", new BigDecimal("150.75"), TypeCompte.COURANT,
                new BigDecimal("500.00"), BigDecimal.ZERO, LocalDate.of(2026, 1, 15), 1, statut);
    }

    @Test
    void accountEndpoints_withoutAuthentication_return401() throws Exception {
        mockMvc.perform(get("/api/comptes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/comptes/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/comptes/1/close")).andExpect(status().isUnauthorized());

        verifyNoInteractions(compteService);
    }

    // ---------------- create ----------------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createCompte_asAdmin_returns201WithAccountDetails() throws Exception {
        when(compteService.createCompte(any())).thenReturn(account(StatutCompte.ACTIVE));

        mockMvc.perform(post("/api/comptes").contentType(MediaType.APPLICATION_JSON).content(VALID_ACCOUNT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idCompte").value(1))
                .andExpect(jsonPath("$.typeCompte").value("COURANT"))
                .andExpect(jsonPath("$.solde").value(150.75))
                .andExpect(jsonPath("$.statut").value("ACTIVE"))
                .andExpect(jsonPath("$.dateCreation").value("2026-01-15"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void createCompte_asClient_returns403() throws Exception {
        mockMvc.perform(post("/api/comptes").contentType(MediaType.APPLICATION_JSON).content(VALID_ACCOUNT))
                .andExpect(status().isForbidden());

        verifyNoInteractions(compteService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createCompte_withMissingRequiredFields_returns400() throws Exception {
        mockMvc.perform(post("/api/comptes").contentType(MediaType.APPLICATION_JSON).content("""
                        {"numero":"","decouvertAutorise":-1}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("numero")))
                .andExpect(jsonPath("$.message", containsString("typeCompte")))
                .andExpect(jsonPath("$.message", containsString("idClient")))
                .andExpect(jsonPath("$.message", containsString("decouvertAutorise")));

        verifyNoInteractions(compteService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createCompte_withUnknownAccountType_returns400() throws Exception {
        mockMvc.perform(post("/api/comptes").contentType(MediaType.APPLICATION_JSON).content("""
                        {"numero":"X","typeCompte":"CHEQUE","idClient":1}
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(compteService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createCompte_forMissingClient_returns404() throws Exception {
        when(compteService.createCompte(any())).thenThrow(new ResourceNotFoundException("Client not found: 1"));

        mockMvc.perform(post("/api/comptes").contentType(MediaType.APPLICATION_JSON).content(VALID_ACCOUNT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Client not found: 1"));
    }

    // ---------------- read ----------------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getAllComptes_asAdmin_returnsList() throws Exception {
        when(compteService.getAllComptes()).thenReturn(List.of(account(StatutCompte.ACTIVE)));

        mockMvc.perform(get("/api/comptes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void getAllComptes_asClient_returns403() throws Exception {
        mockMvc.perform(get("/api/comptes")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void getCompteById_asOwner_returnsAccount() throws Exception {
        when(compteSecurity.isOwner(1, "alice")).thenReturn(true);
        when(compteService.getCompteById(1)).thenReturn(account(StatutCompte.ACTIVE));

        mockMvc.perform(get("/api/comptes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("TEST-ACC-1"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void getCompteById_asNonOwner_returns403() throws Exception {
        when(compteSecurity.isOwner(2, "alice")).thenReturn(false);

        mockMvc.perform(get("/api/comptes/2")).andExpect(status().isForbidden());

        verifyNoInteractions(compteService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getCompteById_whenMissing_returns404() throws Exception {
        when(compteService.getCompteById(404)).thenThrow(new ResourceNotFoundException("Account not found: 404"));

        mockMvc.perform(get("/api/comptes/404")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void getComptesByClient_forOwnClientId_returnsAccounts() throws Exception {
        when(clientSecurity.isOwner(1, "alice")).thenReturn(true);
        when(compteService.getComptesByClient(1)).thenReturn(List.of(account(StatutCompte.ACTIVE)));

        mockMvc.perform(get("/api/comptes/client/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idClient").value(1));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void getComptesByClient_forAnotherClientId_returns403() throws Exception {
        when(clientSecurity.isOwner(2, "alice")).thenReturn(false);

        mockMvc.perform(get("/api/comptes/client/2")).andExpect(status().isForbidden());

        verifyNoInteractions(compteService);
    }

    // ---------------- close / reopen ----------------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void closeCompte_asAdmin_returnsClosedAccount() throws Exception {
        when(compteService.closeCompte(1)).thenReturn(account(StatutCompte.CLOSED));

        mockMvc.perform(patch("/api/comptes/1/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("CLOSED"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void closeCompte_withNonZeroBalance_returns409() throws Exception {
        when(compteService.closeCompte(1)).thenThrow(
                new IllegalStateException("Account cannot be closed because its balance is not zero"));

        mockMvc.perform(patch("/api/comptes/1/close"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("balance is not zero")));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void closeCompte_asClient_returns403() throws Exception {
        mockMvc.perform(patch("/api/comptes/1/close")).andExpect(status().isForbidden());

        verifyNoInteractions(compteService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void reopenCompte_asAdmin_returnsActiveAccount() throws Exception {
        when(compteService.reopenCompte(1)).thenReturn(account(StatutCompte.ACTIVE));

        mockMvc.perform(patch("/api/comptes/1/reopen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACTIVE"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void reopenCompte_whenAlreadyActive_returns409() throws Exception {
        when(compteService.reopenCompte(1)).thenThrow(new IllegalStateException("This account is already active"));

        mockMvc.perform(patch("/api/comptes/1/reopen")).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void reopenCompte_asClient_returns403() throws Exception {
        mockMvc.perform(patch("/api/comptes/1/reopen")).andExpect(status().isForbidden());
    }
}
