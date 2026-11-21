
package com.bankservices.bankservices_api.controller;

import com.bankservices.bankservices_api.dto.request.ClientRegistrationRequest;
import com.bankservices.bankservices_api.dto.response.ClientResponse;
import com.bankservices.bankservices_api.service.AccountActivationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * ADMIN: register clients with an online banking login and manage
 * their activation e-mails. ("/api/admin/**" is also ADMIN-only in SecurityConfig.)
 */
@RestController
@RequestMapping("/api/admin/clients")
@PreAuthorize("hasRole('ADMIN')")
public class AdminClientController {

    private final AccountActivationService accountActivationService;

    public AdminClientController(AccountActivationService accountActivationService) {
        this.accountActivationService = accountActivationService;
    }

    // Create client + pending login, and e-mail the activation link
    @PostMapping
    public ResponseEntity<ClientResponse> registerClient(
            @Valid @RequestBody ClientRegistrationRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(accountActivationService.registerClient(request));
    }

    // (Re)send the activation e-mail (creates the pending login for a
    // client registered before online banking existed)
    @PostMapping("/{id}/activation-email")
    public ResponseEntity<ClientResponse> sendActivationEmail(
            @PathVariable Integer id) {

        return ResponseEntity.ok(accountActivationService.sendActivationForClient(id));
    }
}
