package com.bankservices.bankservices_api.controller;

import com.bankservices.bankservices_api.config.SecurityConfig;
import com.bankservices.bankservices_api.dto.response.ClientResponse;
import com.bankservices.bankservices_api.exception.ResourceNotFoundException;
import com.bankservices.bankservices_api.service.ClientService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for {@link ClientController}: CRUD endpoints, ADMIN/CLIENT
 * role restrictions, ownership checks, validation and error mapping.
 */
@WebMvcTest(ClientController.class)
@Import(SecurityConfig.class)
class ClientControllerTest extends AbstractControllerTest {

    private static final String VALID_CLIENT = """
            {"nom":"Test","prenom":"Alice","email":"alice@example.test",
             "telephone":"0600000000","adresse":"1 Test Street"}
            """;

    @MockitoBean
    private ClientService clientService;

    private ClientResponse alice() {
        return new ClientResponse(1, "Test", "Alice", "alice@example.test", "0600000000", "1 Test Street");
    }

    // ---------------- authentication ----------------

    @Test
    void anyClientEndpoint_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/clients/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content(VALID_CLIENT))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(clientService);
    }

    // ---------------- create ----------------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createClient_asAdmin_returns201WithCreatedClient() throws Exception {
        when(clientService.createClient(any())).thenReturn(alice());

        mockMvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content(VALID_CLIENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idClient").value(1))
                .andExpect(jsonPath("$.prenom").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.test"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void createClient_asClient_returns403() throws Exception {
        mockMvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content(VALID_CLIENT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("permission")));

        verifyNoInteractions(clientService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createClient_withBlankNamesAndInvalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nom":"","prenom":"","email":"not-an-email"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("nom")))
                .andExpect(jsonPath("$.message", containsString("prenom")))
                .andExpect(jsonPath("$.message", containsString("email")));

        verifyNoInteractions(clientService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createClient_withOverlongPhone_returns400() throws Exception {
        mockMvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nom":"Test","prenom":"Alice","telephone":"012345678901234567890"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("telephone")));
    }

    // ---------------- read ----------------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getAllClients_asAdmin_returnsList() throws Exception {
        when(clientService.getAllClients()).thenReturn(List.of(alice()));

        mockMvc.perform(get("/api/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nom").value("Test"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void getAllClients_asClient_returns403() throws Exception {
        mockMvc.perform(get("/api/clients")).andExpect(status().isForbidden());

        verifyNoInteractions(clientService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getClientById_asAdmin_returnsClientWithoutOwnershipCheck() throws Exception {
        when(clientService.getClientById(1)).thenReturn(alice());

        mockMvc.perform(get("/api/clients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idClient").value(1));

        verifyNoInteractions(clientSecurity);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void getClientById_asOwningClient_returnsOwnProfile() throws Exception {
        when(clientSecurity.isOwner(1, "alice")).thenReturn(true);
        when(clientService.getClientById(1)).thenReturn(alice());

        mockMvc.perform(get("/api/clients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prenom").value("Alice"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void getClientById_asAnotherClient_returns403() throws Exception {
        when(clientSecurity.isOwner(2, "alice")).thenReturn(false);

        mockMvc.perform(get("/api/clients/2")).andExpect(status().isForbidden());

        verifyNoInteractions(clientService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getClientById_whenMissing_returns404WithErrorBody() throws Exception {
        when(clientService.getClientById(99)).thenThrow(new ResourceNotFoundException("Client not found: 99"));

        mockMvc.perform(get("/api/clients/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Client not found: 99"))
                .andExpect(jsonPath("$.path").value("/api/clients/99"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ---------------- update ----------------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateClient_asAdmin_returnsUpdatedClient() throws Exception {
        when(clientService.updateClient(eq(1), any())).thenReturn(alice());

        mockMvc.perform(put("/api/clients/1").contentType(MediaType.APPLICATION_JSON).content(VALID_CLIENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idClient").value(1));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateClient_whenMissing_returns404() throws Exception {
        when(clientService.updateClient(eq(99), any()))
                .thenThrow(new ResourceNotFoundException("Client not found: 99"));

        mockMvc.perform(put("/api/clients/99").contentType(MediaType.APPLICATION_JSON).content(VALID_CLIENT))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void updateClient_asClientEvenForOwnProfile_returns403() throws Exception {
        mockMvc.perform(put("/api/clients/1").contentType(MediaType.APPLICATION_JSON).content(VALID_CLIENT))
                .andExpect(status().isForbidden());

        verifyNoInteractions(clientService);
    }

    // ---------------- delete ----------------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteClient_asAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/clients/1")).andExpect(status().isNoContent());

        verify(clientService).deleteClient(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteClient_withBankAccounts_returns409() throws Exception {
        doThrow(new IllegalStateException("Cannot delete a client who has bank accounts."))
                .when(clientService).deleteClient(1);

        mockMvc.perform(delete("/api/clients/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot delete a client who has bank accounts."));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void deleteClient_asClient_returns403() throws Exception {
        mockMvc.perform(delete("/api/clients/1")).andExpect(status().isForbidden());

        verifyNoInteractions(clientService);
    }
}
