
package com.bankservices.bankservices_api.controller;

import com.bankservices.bankservices_api.dto.request.ClientRequest;
import com.bankservices.bankservices_api.dto.response.ClientResponse;
import com.bankservices.bankservices_api.service.ClientService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    // ADMIN: Create client
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClientResponse> createClient(
            @Valid @RequestBody ClientRequest request) {

        ClientResponse response = clientService.createClient(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ADMIN: Get all clients
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ClientResponse>> getAllClients() {

        return ResponseEntity.ok(clientService.getAllClients());
    }

    // ADMIN: Get any client by ID
    // CLIENT: Only get their own profile
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CLIENT') and @clientSecurity.isOwner(#p0, authentication.name))")
    public ResponseEntity<ClientResponse> getClientById(
            @PathVariable Integer id) {

        return ResponseEntity.ok(clientService.getClientById(id));
    }

    // ADMIN: Update client
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClientResponse> updateClient(
            @PathVariable Integer id,
            @Valid @RequestBody ClientRequest request) {

        return ResponseEntity.ok(
                clientService.updateClient(id, request));
    }

    // ADMIN: Delete client
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteClient(
            @PathVariable Integer id) {

        clientService.deleteClient(id);

        return ResponseEntity.noContent().build();
    }
}
