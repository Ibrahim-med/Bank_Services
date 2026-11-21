import { describe, expect, it } from "vitest";

import { formatWait, parseRetryAfter, RATE_LIMIT_MESSAGE, rateLimitMessage } from "./rateLimit";
import api from "../services/api";

describe("Retry-After parsing", () => {
    it("reads delay-seconds", () => {
        expect(parseRetryAfter("12")).toBe(12);
        expect(parseRetryAfter(" 300 ")).toBe(300);
    });

    it("reads an HTTP date", () => {
        const now = Date.parse("Fri, 20 Nov 2026 10:00:00 GMT");
        expect(parseRetryAfter("Fri, 20 Nov 2026 10:00:30 GMT", now)).toBe(30);
    });

    it("ignores missing or invalid values", () => {
        expect(parseRetryAfter(undefined)).toBeNull();
        expect(parseRetryAfter("")).toBeNull();
        expect(parseRetryAfter("soon")).toBeNull();
    });
});

describe("rate-limit message", () => {
    it("formats seconds and minutes", () => {
        expect(formatWait(1)).toBe("1 second");
        expect(formatWait(12)).toBe("12 seconds");
        expect(formatWait(60)).toBe("1 minute");
        expect(formatWait(300)).toBe("5 minutes");
        expect(formatWait(0)).toBeNull();
    });

    it("includes the waiting time when known", () => {
        expect(rateLimitMessage(12)).toBe(
            "You have made too many requests. Please wait 12 seconds before trying again."
        );
        expect(rateLimitMessage(null)).toBe(RATE_LIMIT_MESSAGE);
    });
});

describe("API client handling of HTTP 429", () => {
    const rejected = api.interceptors.response.handlers.find(Boolean).rejected;

    const tooMany = (headers, data) => ({
        config: { url: "/client/bills/1/pay" },
        response: { status: 429, headers, data }
    });

    it("replaces the message with one that includes Retry-After", async () => {
        const error = tooMany({ "retry-after": "20" }, { status: 429, message: "server text", path: "/api/x" });

        await expect(rejected(error)).rejects.toBe(error);

        expect(error.rateLimited).toBe(true);
        expect(error.retryAfterSeconds).toBe(20);
        expect(error.response.data.message).toBe(
            "You have made too many requests. Please wait 20 seconds before trying again."
        );
        expect(error.response.data.path).toBe("/api/x");
    });

    it("falls back to the generic message without Retry-After", async () => {
        const error = tooMany({}, undefined);

        await expect(rejected(error)).rejects.toBe(error);

        expect(error.response.data.message).toBe(RATE_LIMIT_MESSAGE);
    });

    it("does not sign the user out on 429", async () => {
        localStorage.setItem("token", "abc");

        await expect(rejected(tooMany({ "retry-after": "5" }, {}))).rejects.toBeTruthy();

        expect(localStorage.getItem("token")).toBe("abc");
    });
});
