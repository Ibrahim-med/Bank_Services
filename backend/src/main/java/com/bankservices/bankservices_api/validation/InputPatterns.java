package com.bankservices.bankservices_api.validation;

/**
 * Shared validation patterns, based on the business data, not on "dangerous"
 * characters: apostrophes, hyphens, accents, quotes, '+' in e-mail addresses etc.
 * are all accepted. SQL injection is prevented by parameter binding everywhere;
 * these rules only reject data that is invalid for the business.
 */
public final class InputPatterns {

    private InputPatterns() {
    }

    /** Free text on one line: any character except control characters (newlines, NUL, escape...). */
    public static final String SINGLE_LINE_TEXT = "^[^\\p{Cntrl}]*$";

    /** Free text that may span lines (addresses): no control characters except tab, CR and LF. */
    public static final String MULTI_LINE_TEXT = "^[^\\p{Cntrl}]*(?:[\\t\\r\\n][^\\p{Cntrl}]*)*$";

    /** Phone numbers: digits, spaces and + ( ) . - / (international and local formats). */
    public static final String PHONE = "^[0-9+().\\-/ ]*$";

    /** Bank account number: letters, digits and hyphens, starting with a letter or digit. */
    public static final String ACCOUNT_NUMBER = "^[A-Za-z0-9][A-Za-z0-9-]{0,29}$";

    /** BCrypt limit. */
    public static final int PASSWORD_MAX_BYTES = 72;
}
