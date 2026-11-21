
package com.bankservices.bankservices_api.service;

import com.bankservices.bankservices_api.entity.CompteBancaire;
import com.bankservices.bankservices_api.entity.Utilisateur;
import com.bankservices.bankservices_api.repository.CompteRepository;
import com.bankservices.bankservices_api.repository.UtilisateurRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("compteSecurity")
public class CompteSecurityService {

    private final CompteRepository compteRepository;
    private final UtilisateurRepository utilisateurRepository;

    public CompteSecurityService(
            CompteRepository compteRepository,
            UtilisateurRepository utilisateurRepository) {

        this.compteRepository = compteRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    @Transactional(readOnly = true)
    public boolean isOwner(Integer compteId, String username) {

        CompteBancaire compte = compteRepository
                .findById(compteId)
                .orElse(null);

        if (compte == null || compte.getClient() == null) {
            return false;
        }

        Utilisateur utilisateur = utilisateurRepository
                .findByUsername(username)
                .orElse(null);

        if (utilisateur == null || utilisateur.getClient() == null) {
            return false;
        }

        Integer accountClientId = compte.getClient().getIdClient();
        Integer userClientId = utilisateur.getClient().getIdClient();

        return accountClientId.equals(userClientId);
    }
}
