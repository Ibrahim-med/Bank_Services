package com.bankservices.bankservices_api.controller;

import com.bankservices.bankservices_api.config.SecurityConfig;
import com.bankservices.bankservices_api.dto.response.BillPaymentResponse;
import com.bankservices.bankservices_api.dto.response.BillResponse;
import com.bankservices.bankservices_api.enums.BillCategory;
import com.bankservices.bankservices_api.enums.BillPaymentStatus;
import com.bankservices.bankservices_api.enums.BillStatus;
import com.bankservices.bankservices_api.enums.TypeCompte;
import com.bankservices.bankservices_api.exception.InsufficientBalanceException;
import com.bankservices.bankservices_api.service.BillPaymentService;
import com.bankservices.bankservices_api.service.BillService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MockMvc tests for {@link ClientBillController} and {@link AdminBillController}. */
@WebMvcTest({ClientBillController.class, AdminBillController.class})
@Import(SecurityConfig.class)
class BillControllersTest extends AbstractControllerTest {

    @MockitoBean private BillService billService;
    @MockitoBean private BillPaymentService billPaymentService;

    private static BillResponse unpaidBill() {
        return new BillResponse(100, "ELEC-1", "City Power", BillCategory.ELECTRICITY,
                new BigDecimal("120.50"), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                BillStatus.UNPAID, false, 1, "Alice Martin", null, null, null);
    }

    private static BillPaymentResponse receipt() {
        return new BillPaymentResponse(700, "PAY-20261119-ABCDEFGH", BillPaymentStatus.COMPLETED,
                new BigDecimal("120.50"), LocalDateTime.of(2026, 11, 19, 10, 0), 100, "ELEC-1",
                "City Power", BillCategory.ELECTRICITY, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                11, "****1234", TypeCompte.COURANT, 900, "Alice Martin");
    }

    // ---------------- client endpoints ----------------

    @Test
    void clientEndpoints_withoutAuthentication_return401() throws Exception {
        mockMvc.perform(get("/api/client/bills")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/client/bills/100/pay").contentType(MediaType.APPLICATION_JSON)
                .content("{\"idCompte\":11}")).andExpect(status().isUnauthorized());

        verifyNoInteractions(billService, billPaymentService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void clientEndpoints_asAdmin_return403() throws Exception {
        mockMvc.perform(get("/api/client/bills")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/client/bills/100/pay").contentType(MediaType.APPLICATION_JSON)
                .content("{\"idCompte\":11}")).andExpect(status().isForbidden());

        verifyNoInteractions(billService, billPaymentService);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void listBills_usesTheAuthenticatedUsername_andFilters() throws Exception {
        when(billService.getClientBills("alice", "UNPAID", BillCategory.ELECTRICITY, "ELEC"))
                .thenReturn(List.of(unpaidBill()));

        mockMvc.perform(get("/api/client/bills")
                        .param("status", "UNPAID").param("category", "ELECTRICITY").param("reference", "ELEC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reference").value("ELEC-1"))
                .andExpect(jsonPath("$[0].amount").value(120.50))
                .andExpect(jsonPath("$[0].status").value("UNPAID"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void unpaidAndPaidShortcuts() throws Exception {
        when(billService.getClientBills(eqStr("alice"), eqStr("UNPAID"), isNull(), isNull()))
                .thenReturn(List.of(unpaidBill()));
        when(billService.getClientBills(eqStr("alice"), eqStr("PAID"), isNull(), isNull()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/client/bills/unpaid")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/client/bills/paid")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private static String eqStr(String value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void billOfAnotherClient_returns403() throws Exception {
        when(billService.getClientBill("alice", 200))
                .thenThrow(new AccessDeniedException("You are not authorized to view this bill"));

        mockMvc.perform(get("/api/client/bills/200")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void pay_returnsReceipt_andIgnoresAnyAmountSentByTheClient() throws Exception {
        when(billPaymentService.payBill("alice", 100, 11)).thenReturn(receipt());

        mockMvc.perform(post("/api/client/bills/100/pay").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCompte\":11,\"amount\":0.01,\"montant\":0.01}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentReference").value("PAY-20261119-ABCDEFGH"))
                .andExpect(jsonPath("$.amount").value(120.50))
                .andExpect(jsonPath("$.accountNumberMasked").value("****1234"));

        // The service only ever receives the username, bill id and account id.
        verify(billPaymentService).payBill("alice", 100, 11);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void pay_withoutAccount_returns400() throws Exception {
        mockMvc.perform(post("/api/client/bills/100/pay").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("idCompte")));

        verifyNoInteractions(billPaymentService);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void pay_insufficientFunds_returns400() throws Exception {
        when(billPaymentService.payBill(anyString(), anyInt(), anyInt()))
                .thenThrow(new InsufficientBalanceException("Insufficient available balance."));

        mockMvc.perform(post("/api/client/bills/100/pay").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCompte\":11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient available balance."));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void pay_alreadyPaid_returns409() throws Exception {
        when(billPaymentService.payBill(anyString(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("This bill has already been paid."));

        mockMvc.perform(post("/api/client/bills/100/pay").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCompte\":11}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void paymentHistoryAndReceipt() throws Exception {
        when(billPaymentService.getPayments("alice")).thenReturn(List.of(receipt()));
        when(billPaymentService.getReceipt("alice", 700)).thenReturn(receipt());

        mockMvc.perform(get("/api/client/bill-payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/client/bill-payments/700/receipt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billReference").value("ELEC-1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // ---------------- admin endpoints ----------------

    private static final String VALID_BILL = """
            {"reference":"ELEC-1","provider":"City Power","category":"ELECTRICITY",
             "amount":120.50,"issueDate":"2026-11-01","dueDate":"2026-11-30","idClient":1}
            """;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCreatesBill_returns201() throws Exception {
        when(billService.createBill(any())).thenReturn(unpaidBill());

        mockMvc.perform(post("/api/admin/bills").contentType(MediaType.APPLICATION_JSON).content(VALID_BILL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idBill").value(100));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCreatesBill_negativeAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/bills").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BILL.replace("120.50", "-5")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(billService);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CLIENT")
    void adminEndpoints_asClient_return403() throws Exception {
        mockMvc.perform(post("/api/admin/bills").contentType(MediaType.APPLICATION_JSON).content(VALID_BILL))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/bills")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/bills/1/cancel")).andExpect(status().isForbidden());

        verifyNoInteractions(billService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminListsAndCancels() throws Exception {
        when(billService.getAllBills()).thenReturn(List.of(unpaidBill()));
        when(billService.cancelBill(100)).thenReturn(unpaidBill());

        mockMvc.perform(get("/api/admin/bills")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(patch("/api/admin/bills/100/cancel")).andExpect(status().isOk());
    }
}
