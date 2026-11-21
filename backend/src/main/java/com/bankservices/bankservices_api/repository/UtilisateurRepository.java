
package com.bankservices.bankservices_api.repository;

import com.bankservices.bankservices_api.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UtilisateurRepository
        extends JpaRepository<Utilisateur, Integer> {

    Optional<Utilisateur> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByClient_IdClient(Integer idClient);

    Optional<Utilisateur> findByClient_EmailIgnoreCase(String email);

    Optional<Utilisateur> findByClient_IdClient(Integer idClient);

    List<Utilisateur> findByClientIsNotNull();
}
