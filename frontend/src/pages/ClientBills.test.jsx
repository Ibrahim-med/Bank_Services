import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";

import ClientBills from "./ClientBills";
import ToastProvider from "../context/ToastProvider";
import api from "../services/api";

vi.mock("../services/api", () => ({ default: { get: vi.fn(), post: vi.fn() } }));

const unpaid = {
    idBill: 100, reference: "ELEC-1", provider: "City Power", category: "ELECTRICITY",
    amount: 120.5, issueDate: "2026-11-01", dueDate: "2026-11-30", status: "UNPAID",
    overdue: false, idClient: 1, clientName: "Alice Martin", paidAt: null, idBillPayment: null, paymentReference: null
};
const overdue = { ...unpaid, idBill: 101, reference: "WAT-2", provider: "City Water", category: "WATER", amount: 40, overdue: true };
const paid = { ...unpaid, idBill: 102, reference: "NET-3", provider: "FiberNet", category: "INTERNET", status: "PAID", idBillPayment: 7, paymentReference: "PAY-1" };

const account = { idCompte: 11, numero: "FR76-0001-1234", solde: 500, decouvertAutorise: 0, typeCompte: "COURANT", statut: "ACTIVE" };

const receipt = {
    idBillPayment: 8, paymentReference: "PAY-20261119-NEWPAYMT", status: "COMPLETED", amount: 120.5,
    paymentDate: "2026-11-19T10:00:00", idBill: 100, billReference: "ELEC-1", provider: "City Power",
    category: "ELECTRICITY", billIssueDate: "2026-11-01", billDueDate: "2026-11-30", idCompte: 11,
    accountNumberMasked: "****1234", accountType: "COURANT", idTransaction: 900, clientName: "Alice Martin"
};

function mockLoad(accounts = [account]) {
    api.get.mockImplementation((url) => {
        if (url === "/client/bills") return Promise.resolve({ data: [unpaid, overdue, paid] });
        if (url.startsWith("/comptes/client/")) return Promise.resolve({ data: accounts });
        return Promise.reject(new Error("unexpected " + url));
    });
}

const renderPage = () =>
    render(
        <MemoryRouter>
            <ToastProvider>
                <ClientBills />
            </ToastProvider>
        </MemoryRouter>
    );

describe("ClientBills page", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        localStorage.setItem("role", "CLIENT");
        localStorage.setItem("idClient", "1");
    });

    it("lists the client's bills from the API with their status", async () => {
        mockLoad();
        renderPage();

        expect(await screen.findByText("ELEC-1")).toBeInTheDocument();
        expect(screen.getByText("WAT-2")).toBeInTheDocument();
        expect(screen.getByText("NET-3")).toBeInTheDocument();
        expect(screen.getAllByText("Overdue").length).toBeGreaterThan(0);
        expect(api.get).toHaveBeenCalledWith("/client/bills");
        expect(api.get).toHaveBeenCalledWith("/comptes/client/1");
    });

    it("searches by bill reference", async () => {
        mockLoad();
        const user = userEvent.setup();
        renderPage();
        await screen.findByText("ELEC-1");

        await user.type(screen.getByLabelText(/search bills by reference/i), "wat");

        expect(screen.getByText("WAT-2")).toBeInTheDocument();
        expect(screen.queryByText("ELEC-1")).not.toBeInTheDocument();
    });

    it("pays with the selected account, sends no amount, and shows the receipt", async () => {
        mockLoad();
        api.post.mockResolvedValue({ data: receipt });
        const user = userEvent.setup();
        renderPage();

        const row = (await screen.findByText("ELEC-1")).closest("tr");
        await user.click(within(row).getByRole("button", { name: "Pay" }));

        const dialog = await screen.findByRole("dialog", { name: /pay bill/i });
        // Single active account is preselected; balance after payment is previewed.
        expect(within(dialog).getByText("379.50")).toBeInTheDocument();

        await user.click(within(dialog).getByRole("button", { name: /confirm payment/i }));

        expect(api.post).toHaveBeenCalledTimes(1);
        expect(api.post).toHaveBeenCalledWith("/client/bills/100/pay", { idCompte: 11 });

        const receiptDialog = await screen.findByRole("dialog", { name: /payment receipt/i });
        expect(within(receiptDialog).getByText(/payment confirmed/i)).toBeInTheDocument();
        expect(within(receiptDialog).getAllByText("PAY-20261119-NEWPAYMT").length).toBeGreaterThan(0);
        expect(within(receiptDialog).getByText(/\*\*\*\*1234/)).toBeInTheDocument();
    });

    it("shows the server error and no receipt when the payment is refused", async () => {
        mockLoad();
        api.post.mockRejectedValue({ response: { status: 409, data: { message: "This bill has already been paid." } } });
        const user = userEvent.setup();
        renderPage();

        const row = (await screen.findByText("ELEC-1")).closest("tr");
        await user.click(within(row).getByRole("button", { name: "Pay" }));
        const dialog = await screen.findByRole("dialog", { name: /pay bill/i });
        await user.click(within(dialog).getByRole("button", { name: /confirm payment/i }));

        expect(await within(dialog).findByText("This bill has already been paid.")).toBeInTheDocument();
        expect(screen.queryByRole("dialog", { name: /payment receipt/i })).not.toBeInTheDocument();
    });

    it("blocks the payment when the selected account clearly lacks funds", async () => {
        mockLoad([{ ...account, solde: 10 }]);
        const user = userEvent.setup();
        renderPage();

        const row = (await screen.findByText("ELEC-1")).closest("tr");
        await user.click(within(row).getByRole("button", { name: "Pay" }));
        const dialog = await screen.findByRole("dialog", { name: /pay bill/i });

        expect(within(dialog).getByText(/not have enough available funds/i)).toBeInTheDocument();
        expect(within(dialog).getByRole("button", { name: /confirm payment/i })).toBeDisabled();
        expect(api.post).not.toHaveBeenCalled();
    });
});
