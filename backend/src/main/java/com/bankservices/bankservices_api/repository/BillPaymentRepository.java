
package com.bankservices.bankservices_api.repository;

import com.bankservices.bankservices_api.entity.BillPayment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillPaymentRepository extends JpaRepository<BillPayment, Integer> {

    List<BillPayment> findByBill_Client_IdClientOrderByPaymentDateDesc(Integer idClient);

    Optional<BillPayment> findByBill_IdBill(Integer idBill);

    boolean existsByReference(String reference);
}
