
package com.bankservices.bankservices_api.repository;

import com.bankservices.bankservices_api.entity.TransactionBancaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository
        extends JpaRepository<TransactionBancaire, Integer> {

    // Find all transactions involving a specific account
    List<TransactionBancaire>
    findByCompteSource_IdCompteOrCompteDestination_IdCompteOrderByDateTransactionDesc(
            Integer sourceId,
            Integer destinationId
    );

    // Find all transactions involving accounts owned by a specific client
    List<TransactionBancaire>
    findDistinctByCompteSource_Client_IdClientOrCompteDestination_Client_IdClientOrderByDateTransactionDesc(
            Integer sourceClientId,
            Integer destinationClientId
    );
}
