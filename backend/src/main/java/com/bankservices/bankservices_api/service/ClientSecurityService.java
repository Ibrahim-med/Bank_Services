
package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service("clientSecurity")
public class ClientSecurityService {

    // DEBUG only, and never the username: ownership checks run on every request.
    private static final Logger logger = LoggerFactory.getLogger(ClientSecurityService.class);

    private final UtilisateurRepository utilisateurRepository;

    public ClientSecurityService(
            UtilisateurRepository utilisateurRepository) {
        this.utilisateurRepository = utilisateurRepository;
    }

    public boolean isOwner(Integer clientId, String username) {

        Utilisateur utilisateur = utilisateurRepository
                .findByUsername(username)
                .orElse(null);

        if (utilisateur == null || utilisateur.getClient() == null) {
            logger.debug("Ownership check for client {}: no linked client profile", clientId);
            return false;
        }

        Integer ownerClientId = utilisateur.getClient().getIdClient();

        boolean result = ownerClientId.equals(clientId);

        logger.debug("Ownership check for client {}: {}", clientId, result ? "granted" : "denied");

        return result;
    }
}
