
package com.bankservices.bankservices_api.controller;

import com.bankservices.bankservices_api.dto.request.TransactionRequest;
import com.bankservices.bankservices_api.dto.response.TransactionResponse;
import com.bankservices.bankservices_api.service.TransactionService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(
            TransactionService transactionService) {

        this.transactionService = transactionService;
    }

    // Deposit, withdrawal or transfer
    @PostMapping
    public ResponseEntity<TransactionResponse> executeTransaction(
            @Valid @RequestBody TransactionRequest request) {

        TransactionResponse response =
                transactionService.executeTransaction(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // Get all transactions - ADMIN only
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TransactionResponse>>
    getAllTransactions() {

        return ResponseEntity.ok(
                transactionService.getAllTransactions());
    }

    // Get transaction history for the authenticated client
    @GetMapping("/client/{idClient}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<TransactionResponse>>
    getTransactionsByClient(
            @PathVariable Integer idClient) {

        return ResponseEntity.ok(
                transactionService.getTransactionsByClient(idClient));
    }

    // Get transaction by ID - ADMIN or transaction owner
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse>
    getTransactionById(@PathVariable Integer id) {

        return ResponseEntity.ok(
                transactionService.getTransactionById(id));
    }
}
