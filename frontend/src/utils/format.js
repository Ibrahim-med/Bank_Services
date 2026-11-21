/* =========================================================
   SHARED FORMATTING HELPERS
   One place for numbers, dates and the labels of backend
   enum values (DEPOT, VALIDEE, COURANT...).
========================================================= */

const LOCALE = "en-US";

const moneyFormatter = new Intl.NumberFormat(LOCALE, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
});

const numberFormatter = new Intl.NumberFormat(LOCALE);

export function formatMoney(value, fallback = "—") {
    if (value === null || value === undefined || value === "") return fallback;

    const number = Number(value);

    return Number.isFinite(number) ? moneyFormatter.format(number) : fallback;
}

export function formatNumber(value, fallback = "—") {
    const number = Number(value);

    return value !== null && value !== undefined && Number.isFinite(number)
        ? numberFormatter.format(number)
        : fallback;
}

function toDate(value) {
    if (!value) return null;

    const date = new Date(value);

    return Number.isNaN(date.getTime()) ? null : date;
}

export function formatDate(value, fallback = "—") {
    const date = toDate(value);

    return date
        ? date.toLocaleDateString(LOCALE, { year: "numeric", month: "short", day: "numeric" })
        : fallback;
}

export function formatDateTime(value, fallback = "—") {
    const date = toDate(value);

    return date
        ? date.toLocaleString(LOCALE, {
            year: "numeric",
            month: "short",
            day: "numeric",
            hour: "2-digit",
            minute: "2-digit"
        })
        : fallback;
}

/* ---------------------------------------------------------
   ENUM LABELS AND TONES
   tone is used by <StatusChip /> to pick the colour.
--------------------------------------------------------- */

const STATUS_MAP = {
    VALIDEE: { label: "Validated", tone: "success" },
    VALIDATED: { label: "Validated", tone: "success" },
    SUCCESS: { label: "Successful", tone: "success" },
    REFUSEE: { label: "Refused", tone: "error" },
    REJECTED: { label: "Rejected", tone: "error" },
    FAILED: { label: "Failed", tone: "error" },
    PENDING: { label: "Pending", tone: "warning" },
    EN_ATTENTE: { label: "Pending", tone: "warning" },
    ACTIVE: { label: "Active", tone: "success" },
    CLOSED: { label: "Closed", tone: "neutral" },
    // Online banking login (Utilisateur.statut)
    PENDING_ACTIVATION: { label: "Pending activation", tone: "warning" },
    DISABLED: { label: "Disabled", tone: "error" },
    // Bills and bill payments
    UNPAID: { label: "Unpaid", tone: "warning" },
    OVERDUE: { label: "Overdue", tone: "error" },
    PAID: { label: "Paid", tone: "success" },
    CANCELLED: { label: "Cancelled", tone: "neutral" },
    COMPLETED: { label: "Completed", tone: "success" }
};

const TRANSACTION_TYPE_MAP = {
    DEPOT: { label: "Deposit", tone: "success" },
    RETRAIT: { label: "Withdrawal", tone: "warning" },
    VIREMENT: { label: "Transfer", tone: "violet" },
    PAIEMENT_FACTURE: { label: "Bill payment", tone: "info" }
};

export const BILL_CATEGORY_MAP = {
    ELECTRICITY: { label: "Electricity", tone: "warning" },
    WATER: { label: "Water", tone: "info" },
    INTERNET: { label: "Internet", tone: "violet" },
    TELEPHONE: { label: "Telephone", tone: "primary" },
    OTHER: { label: "Other services", tone: "neutral" }
};

export const BILL_CATEGORIES = Object.keys(BILL_CATEGORY_MAP);

const ACCOUNT_TYPE_MAP = {
    COURANT: { label: "Current", tone: "primary" },
    EPARGNE: { label: "Savings", tone: "violet" }
};

function describe(map, value) {
    const key = String(value ?? "").toUpperCase();

    return map[key] ?? { label: value ? String(value) : "Unknown", tone: "neutral" };
}

export const describeStatus = (value) => describe(STATUS_MAP, value);
export const describeTransactionType = (value) => describe(TRANSACTION_TYPE_MAP, value);
export const describeAccountType = (value) => describe(ACCOUNT_TYPE_MAP, value);
export const describeBillCategory = (value) => describe(BILL_CATEGORY_MAP, value);

/*
 * Display status of a bill: the backend stores UNPAID / PAID / CANCELLED and
 * flags `overdue` (unpaid and past its due date) without storing it.
 */
export const billDisplayStatus = (bill) =>
    bill?.status === "UNPAID" && bill?.overdue ? "OVERDUE" : bill?.status;

export const getTransactionTypeLabel = (value) => describeTransactionType(value).label;

/* Initials for avatars: "Jane Doe" -> "JD". */
export function initials(...parts) {
    const letters = parts
        .filter(Boolean)
        .map((part) => String(part).trim()[0])
        .filter(Boolean)
        .join("");

    return (letters || "?").slice(0, 2).toUpperCase();
}
