/* =========================================================
   HTTP 429 (Too Many Requests) messages.
   Display only: the limits themselves are enforced by the
   backend. Retry-After is read from the response headers
   (exposed by the API's CORS configuration).
========================================================= */

export const RATE_LIMIT_MESSAGE =
    "You have made too many requests. Please wait before trying again.";

/* Retry-After is either a number of seconds or an HTTP date. */
export function parseRetryAfter(value, now = Date.now()) {
    if (value === null || value === undefined || value === "") return null;

    const text = String(value).trim();

    if (/^\d+$/.test(text)) {
        return Number(text);
    }

    const date = Date.parse(text);

    if (Number.isNaN(date)) return null;

    return Math.max(0, Math.ceil((date - now) / 1000));
}

export function formatWait(seconds) {
    if (!Number.isFinite(seconds) || seconds <= 0) return null;

    if (seconds < 60) {
        return `${seconds} second${seconds === 1 ? "" : "s"}`;
    }

    const minutes = Math.ceil(seconds / 60);
    return `${minutes} minute${minutes === 1 ? "" : "s"}`;
}

export function rateLimitMessage(retryAfterSeconds) {
    const wait = formatWait(retryAfterSeconds);

    return wait
        ? `You have made too many requests. Please wait ${wait} before trying again.`
        : RATE_LIMIT_MESSAGE;
}
