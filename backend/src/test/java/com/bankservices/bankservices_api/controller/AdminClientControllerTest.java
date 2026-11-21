package com.bankservices.bankservices_api.controller;

import com.bankservices.bankservices_api.config.SecurityConfig;
import com.bankservices.bankservices_api.dto.response.ClientResponse;
import com.bankservices.bankservices_api.enums.StatutUtilisateur;
import com.bankservices.bankservices_api.service.AccountActivationService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MockMvc tests for {@link AdminClientController}: ADMIN-only registration. */
@WebMvcTest(AdminClientController.class)
@Import(SecurityConfig.class)
class AdminClientControllerTest extends AbstractControllerTest {

    private static final String VALID = """
            {"nom":"Martin","prenom":"Alice","email":"alice@example.test",
             "telephone":"0600000000","adresse":"1 Test Street"}
            """;

    @MockitoBean
    private AccountActivationService accountActivationService;

    private static ClientResponse pendingAlice() {
        ClientResponse response = new ClientResponse(1, "Martin", "Alice", "alice@example.test", "0600000000", "1 Test Street");
        response.setUsername("amartin");
        response.setAccountStatus(StatutUtilisateur.PENDING_ACTIVATION);
        return response;
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void register_asAdmin_returns201_withUsernameAndPendingStatus_andNoPassword() throws Exception {
        when(accountActivationService.registerClient(any())).thenReturn(pendingAlice());

        mockMvc.perform(post("/api/admin/clients").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idClient").value(1))
                .andExpect(jsonPath("$.username").value("amartin"))
                .andExpect(jsonPath("$.accountStatus").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void register_asClient_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/clients").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());

        verifyNoInteractions(accountActivationService);
    }

    @Test
    void register_anonymous_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/clients").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accountActivationService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void register_withoutEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/clients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Martin\",\"prenom\":\"Alice\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("email")));

        verifyNoInteractions(accountActivationService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/clients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Martin\",\"prenom\":\"Alice\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountActivationService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void register_duplicateEmail_returns409() throws Exception {
        when(accountActivationService.registerClient(any()))
                .thenThrow(new IllegalStateException("A client with this email address already exists."));

        mockMvc.perform(post("/api/admin/clients").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void resendActivation_asAdmin_returns200() throws Exception {
        when(accountActivationService.sendActivationForClient(1)).thenReturn(pendingAlice());

        mockMvc.perform(post("/api/admin/clients/1/activation-email"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("PENDING_ACTIVATION"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void resendActivation_asClient_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/clients/1/activation-email"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(accountActivationService);
    }
}
