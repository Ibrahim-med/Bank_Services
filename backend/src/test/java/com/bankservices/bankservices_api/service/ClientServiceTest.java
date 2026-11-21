package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.entity.CompteCourant;
import static org.mockito.ArgumentMatchers.any;
import com.bankservices.bankservices_api.dto.request.ClientRequest;
import java.util.List;
import com.bankservices.bankservices_api.exception.ResourceNotFoundException;
import com.bankservices.bankservices_api.dto.response.ClientResponse;
import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.repository.AccountActivationTokenRepository;
import com.bankservices.bankservices_api.repository.BillRepository;
import com.bankservices.bankservices_api.repository.ClientRepository;
import com.bankservices.bankservices_api.repository.PasswordResetTokenRepository;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    // Collaborators added with the activation and bill features
    // (unstubbed: no login linked, no bills).
    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private BillRepository billRepository;

    @Mock
    private AccountActivationTokenRepository activationTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @InjectMocks
    private ClientService clientService;

    @Test
    void getClientById_ShouldReturnClient() {

        Client client = new Client();
        client.setIdClient(1);
        client.setNom("Chalig");
        client.setPrenom("Amine");

        when(clientRepository.findById(1))
                .thenReturn(Optional.of(client));

        ClientResponse response = clientService.getClientById(1);

        assertNotNull(response);
        assertEquals("Chalig", response.getNom());
        assertEquals("Amine", response.getPrenom());

        verify(clientRepository, times(1)).findById(1);
    }
    @Test
    void getClientById_ShouldThrowException_WhenClientDoesNotExist() {

        when(clientRepository.findById(99))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> clientService.getClientById(99)
        );

        verify(clientRepository, times(1)).findById(99);
    }
    @Test
    void getAllClients_ShouldReturnClientList() {

        Client client1 = new Client();
        client1.setIdClient(1);
        client1.setNom("Martin");
        client1.setPrenom("Lucas");

        Client client2 = new Client();
        client2.setIdClient(2);
        client2.setNom("Smith");
        client2.setPrenom("John");

        when(clientRepository.findAll())
                .thenReturn(List.of(client1, client2));

        List<ClientResponse> responses = clientService.getAllClients();

        assertEquals(2, responses.size());
        assertEquals("Martin", responses.get(0).getNom());
        assertEquals("Smith", responses.get(1).getNom());

        verify(clientRepository, times(1)).findAll();
    }@Test
    void createClient_ShouldSaveAndReturnClient() {

        ClientRequest request = new ClientRequest();
        request.setNom("Martin");
        request.setPrenom("Lucas");
        request.setEmail("lucas@example.com");
        request.setTelephone("0550123456");
        request.setAdresse("Algiers");

        when(clientRepository.save(any(Client.class)))
                .thenAnswer(invocation -> {
                    Client client = invocation.getArgument(0);
                    client.setIdClient(1);
                    return client;
                });

        ClientResponse response = clientService.createClient(request);

        assertNotNull(response);
        assertEquals(1, response.getIdClient());
        assertEquals("Martin", response.getNom());
        assertEquals("Lucas", response.getPrenom());
        assertEquals("lucas@example.com", response.getEmail());

        verify(clientRepository, times(1)).save(any(Client.class));
    }@Test
    void updateClient_ShouldUpdateAndReturnClient() {

        Client client = new Client();
        client.setIdClient(1);
        client.setNom("Martin");
        client.setPrenom("Lucas");
        client.setEmail("old@example.com");

        ClientRequest request = new ClientRequest();
        request.setNom("Smith");
        request.setPrenom("John");
        request.setEmail("john@example.com");
        request.setTelephone("0550123456");
        request.setAdresse("Algiers");

        when(clientRepository.findById(1))
                .thenReturn(Optional.of(client));

        when(clientRepository.save(any(Client.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ClientResponse response = clientService.updateClient(1, request);

        assertNotNull(response);
        assertEquals("Smith", response.getNom());
        assertEquals("John", response.getPrenom());
        assertEquals("john@example.com", response.getEmail());

        verify(clientRepository, times(1)).findById(1);
        verify(clientRepository, times(1)).save(any(Client.class));
    }@Test
    void updateClient_ShouldThrowException_WhenClientDoesNotExist() {

        ClientRequest request = new ClientRequest();
        request.setNom("Smith");
        request.setPrenom("John");

        when(clientRepository.findById(99))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> clientService.updateClient(99, request)
        );

        verify(clientRepository, times(1)).findById(99);
        verify(clientRepository, never()).save(any(Client.class));
    }@Test
    void deleteClient_ShouldDeleteClient_WhenClientHasNoAccounts() {

        Client client = new Client();
        client.setIdClient(1);
        client.setNom("Martin");
        client.setPrenom("Lucas");

        when(clientRepository.findById(1))
                .thenReturn(Optional.of(client));

        clientService.deleteClient(1);

        verify(clientRepository, times(1)).findById(1);
        verify(clientRepository, times(1)).delete(client);
    }@Test
    void deleteClient_ShouldThrowException_WhenClientDoesNotExist() {

        when(clientRepository.findById(99))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> clientService.deleteClient(99)
        );

        verify(clientRepository, never()).delete(any(Client.class));
    }@Test
    void deleteClient_ShouldThrowException_WhenClientHasAccounts() {
        Client client = new Client();
        client.setIdClient(1);

        client.getComptes().add(new CompteCourant());

        when(clientRepository.findById(1))
                .thenReturn(Optional.of(client));

        assertThrows(IllegalStateException.class,
                () -> clientService.deleteClient(1));

        verify(clientRepository, never()).delete(any(Client.class));
    }
}