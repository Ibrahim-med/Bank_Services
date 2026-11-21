package com.bankservices.bankservices_api.support;

import com.bankservices.bankservices_api.entity.Client;
import com.bankservices.bankservices_api.entity.CompteBancaire;
import com.bankservices.bankservices_api.entity.CompteCourant;
import com.bankservices.bankservices_api.entity.CompteEpargne;
import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.enums.StatutCompte;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared builders for fake (non-sensitive) test data and helpers
 * for placing an authenticated user in the SecurityContext.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    // ------------------------------------------------------------
    // Entities
    // ------------------------------------------------------------

    public static Client client(Integer id, String prenom, String nom, String email) {
        Client client = new Client();
        client.setIdClient(id);
        client.setPrenom(prenom);
        client.setNom(nom);
        client.setEmail(email);
        client.setTelephone("0600000000");
        client.setAdresse("1 Test Street");
        client.setComptes(new ArrayList<>());
        return client;
    }

    public static CompteCourant compteCourant(Integer id, Client owner,
                                              String solde, String decouvert) {
        CompteCourant compte = new CompteCourant();
        fill(compte, id, owner, solde);
        compte.setDecouvertAutorise(new BigDecimal(decouvert));
        return compte;
    }

    public static CompteEpargne compteEpargne(Integer id, Client owner,
                                              String solde, String taux) {
        CompteEpargne compte = new CompteEpargne();
        fill(compte, id, owner, solde);
        compte.setTauxInteret(new BigDecimal(taux));
        return compte;
    }

    private static void fill(CompteBancaire compte, Integer id,
                             Client owner, String solde) {
        compte.setIdCompte(id);
        compte.setNumero("TEST-ACC-" + id);
        compte.setSolde(new BigDecimal(solde));
        compte.setStatut(StatutCompte.ACTIVE);
        compte.setDateCreation(LocalDate.of(2026, 1, 15));
        compte.setClient(owner);
        if (owner != null) {
            owner.getComptes().add(compte);
        }
    }

    public static Utilisateur utilisateur(Integer id, String username,
                                          String role, Client client) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setIdUtilisateur(id);
        utilisateur.setUsername(username);
        utilisateur.setPassword("$2a$10$encodedPasswordHashPlaceholder");
        utilisateur.setRole(role);
        utilisateur.setClient(client);
        return utilisateur;
    }

    // ------------------------------------------------------------
    // Security context helpers (for service-level unit tests)
    // ------------------------------------------------------------

    public static void authenticateAs(String username, String role) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    public static void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }
}
