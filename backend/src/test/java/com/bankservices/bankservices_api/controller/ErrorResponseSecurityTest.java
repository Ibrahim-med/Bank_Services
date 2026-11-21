package com.bankservices.bankservices_api.controller;

import com.bankservices.bankservices_api.config.SecurityConfig;
import com.bankservices.bankservices_api.service.ClientService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Error responses never expose SQL, JDBC URLs, credentials, class names or
 * stack traces, and keep the standard JSON format. Logs keep only the error
 * type and SQLState (no SQL text or submitted values at ERROR level).
 */
@WebMvcTest(ClientController.class)
@Import(SecurityConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class ErrorResponseSecurityTest extends AbstractControllerTest {

    private static final String SECRET_DETAILS =
            "jdbc:mysql://localhost:3306/BankServices user=root password=S3cretDbPass "
                    + "SELECT * FROM client WHERE email = 'victim@example.com'";

    @MockitoBean
    private ClientService clientService;

    private static void assertNoInternals(String body) {
        assertThat(body)
                .doesNotContain("jdbc")
                .doesNotContain("S3cretDbPass")
                .doesNotContain("root")
                .doesNotContain("SELECT")
                .doesNotContain("victim@example.com")
                .doesNotContain("Exception")
                .doesNotContain("org.springframework")
                .doesNotContain("java.")
                .doesNotContain("at com.");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void databaseFailure_returnsGeneric500_andLogsNoSqlOrCredentials(CapturedOutput output) throws Exception {
        SQLException cause = new SQLException(SECRET_DETAILS, "08S01", 0);
        when(clientService.getAllClients()).thenThrow(new DataAccessResourceFailureException(SECRET_DETAILS, cause));

        MvcResult result = mockMvc.perform(get("/api/clients"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("A server error occurred. Please try again later."))
                .andExpect(jsonPath("$.path").value("/api/clients"))
                .andReturn();

        assertNoInternals(result.getResponse().getContentAsString());

        // Server-side log: error type + SQLState, never the SQL text or the password.
        assertThat(output).contains("Database error on GET /api/clients")
                .contains("SQLState 08S01")
                .doesNotContain("S3cretDbPass")
                .doesNotContain("victim@example.com");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sqlGrammarError_isNotExposed() throws Exception {
        when(clientService.getClientById(anyInt()))
                .thenThrow(new InvalidDataAccessResourceUsageException(SECRET_DETAILS));

        MvcResult result = mockMvc.perform(get("/api/clients/1"))
                .andExpect(status().isInternalServerError())
                .andReturn();

        assertNoInternals(result.getResponse().getContentAsString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void integrityViolation_keepsItsGenericConflictMessage() throws Exception {
        when(clientService.createClient(any())).thenThrow(new DataIntegrityViolationException(
                "Duplicate entry 'victim@example.com' for key 'client.email' " + SECRET_DETAILS));

        MvcResult result = mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Test\",\"prenom\":\"Alice\",\"email\":\"alice@example.test\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The operation conflicts with existing database data."))
                .andReturn();

        assertNoInternals(result.getResponse().getContentAsString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void lockTimeout_returnsRetryableConflict() throws Exception {
        when(clientService.getAllClients()).thenThrow(new CannotAcquireLockException(SECRET_DETAILS));

        MvcResult result = mockMvc.perform(get("/api/clients"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "The operation could not be completed because the data is being updated. Please try again."))
                .andReturn();

        assertNoInternals(result.getResponse().getContentAsString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unexpectedError_returnsGeneric500() throws Exception {
        when(clientService.getAllClients()).thenThrow(new RuntimeException(SECRET_DETAILS));

        MvcResult result = mockMvc.perform(get("/api/clients"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("A server error occurred. Please try again later."))
                .andReturn();

        assertNoInternals(result.getResponse().getContentAsString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void injectionInPathVariable_isRejectedAsBadRequest() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/clients/1 OR 1=1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'."))
                .andReturn();

        assertNoInternals(result.getResponse().getContentAsString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void malformedJson_isRejected_withoutParserDetails() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\": \"Test\", \"prenom\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body."))
                .andReturn();

        assertNoInternals(result.getResponse().getContentAsString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void wrongHttpMethod_keeps405() throws Exception {
        mockMvc.perform(patch("/api/clients"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }
}
