package com.bankservices.bankservices_api.repository;

import com.bankservices.bankservices_api.entity.CompteBancaire;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface CompteRepository
        extends JpaRepository<CompteBancaire, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CompteBancaire c WHERE c.idCompte = :id")
    Optional<CompteBancaire> findByIdForUpdate(
            @Param("id") Integer id);
}