
package com.bankservices.bankservices_api.enums;

/**
 * Stored bill status. "Overdue" is not stored: it is derived from
 * an UNPAID bill whose due date has passed (see BillResponse.overdue).
 */
public enum BillStatus {
    UNPAID,
    PAID,
    CANCELLED
}
