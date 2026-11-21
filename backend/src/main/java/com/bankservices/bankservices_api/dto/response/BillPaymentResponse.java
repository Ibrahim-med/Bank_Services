
package com.bankservices.bankservices_api.dto.response;

import com.bankservices.bankservices_api.enums.BillCategory;
import com.bankservices.bankservices_api.enums.BillPaymentStatus;
import com.bankservices.bankservices_api.enums.TypeCompte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Bill payment confirmation / receipt.
 * The account number is masked (only the last 4 characters are shown).
 */
public record BillPaymentResponse(
        Integer idBillPayment,
        String paymentReference,
        BillPaymentStatus status,
        BigDecimal amount,
        LocalDateTime paymentDate,
        Integer idBill,
        String billReference,
        String provider,
        BillCategory category,
        LocalDate billIssueDate,
        LocalDate billDueDate,
        Integer idCompte,
        String accountNumberMasked,
        TypeCompte accountType,
        Integer idTransaction,
        String clientName
) {
}
