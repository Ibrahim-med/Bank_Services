
package com.bankservices.bankservices_api.enums;

public enum TypeTransaction {
    DEPOT,
    RETRAIT,
    VIREMENT,
    // Bill payment: debit of a source account (no destination account).
    PAIEMENT_FACTURE
}
