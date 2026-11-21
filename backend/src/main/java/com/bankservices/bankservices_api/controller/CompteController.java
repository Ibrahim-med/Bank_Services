
package com.bankservices.bankservices_api.controller;

import com.bankservices.bankservices_api.dto.request.CompteRequest;
import com.bankservices.bankservices_api.dto.response.CompteResponse;
import com.bankservices.bankservices_api.service.CompteService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comptes")
public class CompteController {

    private final CompteService compteService;

    public CompteController(CompteService compteService) {
        this.compteService = compteService;
    }

    // Create a bank account - ADMIN only
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CompteResponse> createCompte(
            @Valid @RequestBody CompteRequest request) {

        CompteResponse response = compteService.createCompte(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Get all accounts - ADMIN only
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CompteResponse>> getAllComptes() {

        return ResponseEntity.ok(compteService.getAllComptes());
    }

    // Get account by ID - ADMIN or account owner
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CLIENT') and @compteSecurity.isOwner(#p0, authentication.name))")
    public ResponseEntity<CompteResponse> getCompteById(
            @PathVariable Integer id) {

        return ResponseEntity.ok(compteService.getCompteById(id));
    }

    // Get accounts belonging to a client - ADMIN or that client
    @GetMapping("/client/{idClient}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CLIENT') and @clientSecurity.isOwner(#p0, authentication.name))")
    public ResponseEntity<List<CompteResponse>> getComptesByClient(
            @PathVariable Integer idClient) {

        return ResponseEntity.ok(compteService.getComptesByClient(idClient));
    }

    // Close a bank account - ADMIN only
    @PatchMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CompteResponse> closeCompte(
            @PathVariable Integer id) {

        CompteResponse response = compteService.closeCompte(id);

        return ResponseEntity.ok(response);
    }

    // Reopen a closed bank account - ADMIN only
    @PatchMapping("/{id}/reopen")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CompteResponse> reopenCompte(
            @PathVariable Integer id) {

        CompteResponse response = compteService.reopenCompte(id);

        return ResponseEntity.ok(response);
    }
}
