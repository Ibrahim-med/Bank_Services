package com.bankservices.bankservices_api.repository;

import com.bankservices.bankservices_api.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository
        extends JpaRepository<Client, Integer> {

    boolean existsByEmailIgnoreCase(String email);
}