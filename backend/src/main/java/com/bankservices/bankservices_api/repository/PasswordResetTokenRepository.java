
package com.bankservices.bankservices_api.repository;

import com.bankservices.bankservices_api.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHashAndUsedFalse(
            String tokenHash
    );

    void deleteByUtilisateur_IdUtilisateur(Integer idUtilisateur);
}
