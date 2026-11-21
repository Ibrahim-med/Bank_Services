import { describe, expect, it } from "vitest";

import {
    billDisplayStatus,
    describeBillCategory,
    describeStatus,
    describeTransactionType
} from "./format";
import { isPasswordLengthOk, passwordBytes, validateBill, validateNewPassword } from "./validation";
import { buildReceiptText, receiptFileName } from "./receipt";

describe("format helpers for the new backend enums", () => {
    it("labels bill payments in the transaction history", () => {
        expect(describeTransactionType("PAIEMENT_FACTURE").label).toBe("Bill payment");
    });

    it("labels bill categories and login statuses", () => {
        expect(describeBillCategory("OTHER").label).toBe("Other services");
        expect(describeStatus("PENDING_ACTIVATION").label).toBe("Pending activation");
        expect(describeStatus("OVERDUE").tone).toBe("error");
    });

    it("shows an unpaid bill past its due date as overdue", () => {
        expect(billDisplayStatus({ status: "UNPAID", overdue: true })).toBe("OVERDUE");
        expect(billDisplayStatus({ status: "UNPAID", overdue: false })).toBe("UNPAID");
        expect(billDisplayStatus({ status: "PAID", overdue: true })).toBe("PAID");
    });
});

describe("validateBill", () => {
    const valid = {
        idClient: "1",
        reference: "ELEC-2026-11",
        provider: "City Power",
        category: "ELECTRICITY",
        amount: "120.50",
        issueDate: "2026-11-01",
        dueDate: "2026-11-30"
    };

    it("accepts a valid bill", () => {
        expect(validateBill(valid)).toEqual({});
    });

    it("rejects zero, negative and over-precise amounts", () => {
        expect(validateBill({ ...valid, amount: "0" }).amount).toBeTruthy();
        expect(validateBill({ ...valid, amount: "-5" }).amount).toBeTruthy();
        expect(validateBill({ ...valid, amount: "10.005" }).amount).toBeTruthy();
    });

    it("rejects a due date before the issue date and a bad reference", () => {
        expect(validateBill({ ...valid, dueDate: "2026-10-01" }).dueDate).toBeTruthy();
        expect(validateBill({ ...valid, reference: "a b" }).reference).toBeTruthy();
        expect(validateBill({ ...valid, idClient: "" }).idClient).toBeTruthy();
    });
});

describe("validateNewPassword", () => {
    it("enforces length and confirmation", () => {
        expect(validateNewPassword("short", "short")).toMatch(/at least 8/);
        expect(validateNewPassword("LongEnough1", "Different1")).toMatch(/do not match/);
        expect(validateNewPassword("LongEnough1", "LongEnough1")).toBe("");
    });

    it("applies the backend's 72-byte BCrypt limit, counting UTF-8 bytes", () => {
        expect(validateNewPassword("a".repeat(72), "a".repeat(72))).toBe("");
        expect(validateNewPassword("a".repeat(73), "a".repeat(73))).toMatch(/too long/);
        // 37 accented characters = 74 bytes
        expect(validateNewPassword("é".repeat(37), "é".repeat(37))).toMatch(/too long/);
        expect(isPasswordLengthOk("é".repeat(36))).toBe(true);
        expect(passwordBytes("é")).toBe(2);
    });
});

describe("receipt", () => {
    const receipt = {
        idBillPayment: 7,
        paymentReference: "PAY-20261119-ABCDEFGH",
        status: "COMPLETED",
        amount: 120.5,
        paymentDate: "2026-11-19T10:00:00",
        billReference: "ELEC-2026-11",
        provider: "City Power",
        category: "ELECTRICITY",
        billIssueDate: "2026-11-01",
        billDueDate: "2026-11-30",
        accountNumberMasked: "****1234",
        accountType: "COURANT",
        idTransaction: 900,
        clientName: "Alice Martin"
    };

    it("contains every receipt field with the masked account", () => {
        const text = buildReceiptText(receipt);

        for (const expected of [
            "PAY-20261119-ABCDEFGH", "ELEC-2026-11", "City Power", "Electricity",
            "120.50", "****1234", "Completed", "Alice Martin"
        ]) {
            expect(text).toContain(expected);
        }
    });

    it("builds a safe file name", () => {
        expect(receiptFileName(receipt)).toBe("receipt-PAY-20261119-ABCDEFGH.txt");
    });
});
