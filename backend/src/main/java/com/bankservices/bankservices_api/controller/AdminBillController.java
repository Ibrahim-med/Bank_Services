
package com.bankservices.bankservices_api.controller;

import com.bankservices.bankservices_api.dto.request.BillRequest;
import com.bankservices.bankservices_api.dto.response.BillResponse;
import com.bankservices.bankservices_api.service.BillService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** ADMIN: issue, list and cancel client bills. */
@RestController
@RequestMapping("/api/admin/bills")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBillController {

    private final BillService billService;

    public AdminBillController(BillService billService) {
        this.billService = billService;
    }

    @PostMapping
    public ResponseEntity<BillResponse> createBill(
            @Valid @RequestBody BillRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(billService.createBill(request));
    }

    @GetMapping
    public ResponseEntity<List<BillResponse>> getAllBills() {
        return ResponseEntity.ok(billService.getAllBills());
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<BillResponse> cancelBill(@PathVariable Integer id) {
        return ResponseEntity.ok(billService.cancelBill(id));
    }
}
