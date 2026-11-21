
package com.bankservices.bankservices_api.controller;

import com.bankservices.bankservices_api.dto.request.PayBillRequest;
import com.bankservices.bankservices_api.dto.response.BillPaymentResponse;
import com.bankservices.bankservices_api.dto.response.BillResponse;
import com.bankservices.bankservices_api.enums.BillCategory;
import com.bankservices.bankservices_api.service.BillPaymentService;
import com.bankservices.bankservices_api.service.BillService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CLIENT: own bills, bill payment, payment history and receipts.
 * The client is always taken from the authenticated user, never from the
 * request. ("/api/client/**" is also CLIENT-only in SecurityConfig.)
 */
@RestController
@RequestMapping("/api/client")
@PreAuthorize("hasRole('CLIENT')")
public class ClientBillController {

    private final BillService billService;
    private final BillPaymentService billPaymentService;

    public ClientBillController(
            BillService billService,
            BillPaymentService billPaymentService) {

        this.billService = billService;
        this.billPaymentService = billPaymentService;
    }

    // All bills, with optional filters:
    // ?status=UNPAID|PAID|CANCELLED|OVERDUE&category=WATER&reference=EDF
    @GetMapping("/bills")
    public ResponseEntity<List<BillResponse>> getBills(
            Authentication authentication,
            // Filters are matched in Java against an enum allow-list / plain text: they never reach SQL.
            @RequestParam(required = false) @Size(max = 20) String status,
            @RequestParam(required = false) BillCategory category,
            @RequestParam(required = false) @Size(max = 40) String reference) {

        return ResponseEntity.ok(billService.getClientBills(
                authentication.getName(), status, category, reference));
    }

    @GetMapping("/bills/unpaid")
    public ResponseEntity<List<BillResponse>> getUnpaidBills(Authentication authentication) {
        return ResponseEntity.ok(billService.getClientBills(
                authentication.getName(), "UNPAID", null, null));
    }

    @GetMapping("/bills/paid")
    public ResponseEntity<List<BillResponse>> getPaidBills(Authentication authentication) {
        return ResponseEntity.ok(billService.getClientBills(
                authentication.getName(), "PAID", null, null));
    }

    @GetMapping("/bills/{id}")
    public ResponseEntity<BillResponse> getBill(
            Authentication authentication,
            @PathVariable Integer id) {

        return ResponseEntity.ok(billService.getClientBill(authentication.getName(), id));
    }

    // Pay a bill. Body: {"idCompte": 12}. The amount always comes from the bill.
    @PostMapping("/bills/{id}/pay")
    public ResponseEntity<BillPaymentResponse> payBill(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody PayBillRequest request) {

        return ResponseEntity.ok(billPaymentService.payBill(
                authentication.getName(), id, request.getIdCompte()));
    }

    @GetMapping("/bill-payments")
    public ResponseEntity<List<BillPaymentResponse>> getPayments(Authentication authentication) {
        return ResponseEntity.ok(billPaymentService.getPayments(authentication.getName()));
    }

    @GetMapping("/bill-payments/{id}/receipt")
    public ResponseEntity<BillPaymentResponse> getReceipt(
            Authentication authentication,
            @PathVariable Integer id) {

        return ResponseEntity.ok(billPaymentService.getReceipt(authentication.getName(), id));
    }
}
