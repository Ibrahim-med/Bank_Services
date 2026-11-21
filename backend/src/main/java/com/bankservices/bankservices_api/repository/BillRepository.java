
package com.bankservices.bankservices_api.repository;

import com.bankservices.bankservices_api.entity.Bill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, Integer> {

    // Locks the bill row for the duration of a payment so that two
    // concurrent payment attempts of the same bill are serialized.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bill b WHERE b.idBill = :id")
    Optional<Bill> findByIdForUpdate(@Param("id") Integer id);

    List<Bill> findByClient_IdClientOrderByDueDateDesc(Integer idClient);

    List<Bill> findAllByOrderByIssueDateDescIdBillDesc();

    boolean existsByReferenceIgnoreCase(String reference);

    boolean existsByClient_IdClient(Integer idClient);
}
