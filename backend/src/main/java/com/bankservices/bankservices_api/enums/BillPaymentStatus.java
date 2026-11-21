
package com.bankservices.bankservices_api.enums;

/**
 * A bill payment row is written only when the whole payment succeeds
 * (failed attempts are rolled back), so COMPLETED is the only status.
 */
public enum BillPaymentStatus {
    COMPLETED
}
