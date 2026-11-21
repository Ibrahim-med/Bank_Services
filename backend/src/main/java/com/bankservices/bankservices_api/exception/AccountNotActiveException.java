
package com.bankservices.bankservices_api.exception;

/**
 * Thrown at login when the credentials are correct but the user is
 * not ACTIVE (pending activation or disabled). Mapped to HTTP 403.
 */
public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(String message) {
        super(message);
    }
}
