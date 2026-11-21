/* =========================================================
   FORM VALIDATION (mirrors the backend Bean Validation rules;
   the backend remains the authority).
========================================================= */

export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const REFERENCE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._/-]{2,39}$/;
const AMOUNT_PATTERN = /^\d{1,13}(\.\d{1,2})?$/;

/* Administrator: issue a bill (BillRequest). */
export function validateBill(form) {
    const errors = {};

    if (!form.idClient) errors.idClient = "Select the client to bill.";

    if (!form.reference.trim()) errors.reference = "Reference is required.";
    else if (!REFERENCE_PATTERN.test(form.reference.trim()))
        errors.reference = "3-40 characters: letters, digits, '.', '_', '/', '-'.";

    if (!form.provider.trim()) errors.provider = "Provider is required.";
    else if (form.provider.trim().length > 100) errors.provider = "100 characters maximum.";

    const amount = String(form.amount).trim();
    if (!amount) errors.amount = "Amount is required.";
    else if (!AMOUNT_PATTERN.test(amount) || Number(amount) <= 0)
        errors.amount = "Enter an amount greater than zero, with at most 2 decimals.";

    if (!form.issueDate) errors.issueDate = "Issue date is required.";
    if (!form.dueDate) errors.dueDate = "Due date is required.";
    else if (form.issueDate && form.dueDate < form.issueDate)
        errors.dueDate = "The due date cannot be before the issue date.";

    return errors;
}

/*
 * Passwords are hashed with BCrypt, which only handles 72 bytes (UTF-8):
 * the backend refuses longer passwords, so the forms check the same limit.
 * Accented or non-Latin characters count as 2-4 bytes.
 */
export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_BYTES = 72;

export const passwordBytes = (password) => new TextEncoder().encode(password).length;

export const isPasswordLengthOk = (password) =>
    password.length >= PASSWORD_MIN_LENGTH && passwordBytes(password) <= PASSWORD_MAX_BYTES;

/* Account activation / password reset: 8 characters to 72 bytes, confirmed. */
export function validateNewPassword(password, confirmPassword) {
    if (password.length < PASSWORD_MIN_LENGTH) return "Your password must contain at least 8 characters.";
    if (passwordBytes(password) > PASSWORD_MAX_BYTES) return "Your password is too long (maximum 72 characters).";
    if (password !== confirmPassword) return "The passwords do not match.";
    return "";
}
