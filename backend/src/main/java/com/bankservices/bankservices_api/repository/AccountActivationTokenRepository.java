
package com.bankservices.bankservices_api.repository;

import com.bankservices.bankservices_api.entity.AccountActivationToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface AccountActivationTokenRepository
        extends JpaRepository<AccountActivationToken, Long> {

    // Row lock: two concurrent activations with the same link cannot both succeed.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM AccountActivationToken t WHERE t.tokenHash = :tokenHash")
    Optional<AccountActivationToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash);

    void deleteByUtilisateur_IdUtilisateur(Integer idUtilisateur);
}
