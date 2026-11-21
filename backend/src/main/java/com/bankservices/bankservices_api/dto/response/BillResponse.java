
package com.bankservices.bankservices_api.dto.response;

import com.bankservices.bankservices_api.enums.BillCategory;
import com.bankservices.bankservices_api.enums.BillStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Bill as exposed by the API.
 * overdue = UNPAID and the due date is before today (derived, not stored).
 * idBillPayment / paymentReference are set once the bill is paid.
 */
public record BillResponse(
        Integer idBill,
        String reference,
        String provider,
        BillCategory category,
        BigDecimal amount,
        LocalDate issueDate,
        LocalDate dueDate,
        BillStatus status,
        boolean overdue,
        Integer idClient,
        String clientName,
        LocalDateTime paidAt,
        Integer idBillPayment,
        String paymentReference
) {
}
