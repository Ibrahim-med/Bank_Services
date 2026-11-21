
package com.bankservices.bankservices_api.enums;

/**
 * Lifecycle of an online banking login (Utilisateur).
 * Only ACTIVE users may authenticate.
 */
public enum StatutUtilisateur {
    PENDING_ACTIVATION,
    ACTIVE,
    DISABLED
}
