import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";

import Clients from "./Clients";
import ToastProvider from "../context/ToastProvider";
import api from "../services/api";

vi.mock("../services/api", () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }));

const clients = [
    { idClient: 1, nom: "Martin", prenom: "Alice", email: "alice@example.test", username: "amartin", accountStatus: "ACTIVE" },
    { idClient: 2, nom: "Durand", prenom: "Bob", email: "bob@example.test", username: "bdurand", accountStatus: "PENDING_ACTIVATION" },
    { idClient: 3, nom: "Legacy", prenom: "Carl", email: "carl@example.test", username: null, accountStatus: null }
];

const renderPage = () =>
    render(
        <MemoryRouter>
            <ToastProvider>
                <Clients />
            </ToastProvider>
        </MemoryRouter>
    );

describe("Clients page (administrator)", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        localStorage.setItem("role", "ADMIN");
        api.get.mockResolvedValue({ data: clients });
    });

    it("shows the online access status of each client", async () => {
        renderPage();

        expect(await screen.findByText("amartin")).toBeInTheDocument();
        expect(screen.getAllByText("Pending activation").length).toBeGreaterThan(0);
        expect(screen.getByText("No login")).toBeInTheDocument();
    });

    it("offers the activation e-mail only to pending clients and clients without login", async () => {
        renderPage();
        await screen.findByText("amartin");

        expect(screen.queryByRole("button", { name: /send activation email to alice martin/i })).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: /send activation email to bob durand/i })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /send activation email to carl legacy/i })).toBeInTheDocument();
    });

    it("registers a new client through the admin registration endpoint", async () => {
        api.post.mockResolvedValue({ data: { idClient: 4, email: "elise@example.test", username: "edupont", accountStatus: "PENDING_ACTIVATION" } });
        const user = userEvent.setup();
        renderPage();
        await screen.findByText("amartin");

        await user.click(screen.getAllByRole("button", { name: /add client/i })[0]);
        const dialog = await screen.findByRole("dialog");
        await user.type(within(dialog).getByLabelText(/first name/i), "Élise");
        await user.type(within(dialog).getByLabelText(/last name/i), "Dupont");
        await user.type(within(dialog).getByLabelText(/email address/i), "elise@example.test");
        await user.click(within(dialog).getByRole("button", { name: /^add client$/i }));

        expect(api.post).toHaveBeenCalledWith("/admin/clients", expect.objectContaining({
            prenom: "Élise", nom: "Dupont", email: "elise@example.test"
        }));
        expect(await screen.findByText(/activation email sent to elise@example.test \(username: edupont\)/i)).toBeInTheDocument();
    });

    it("requires an email before registering", async () => {
        const user = userEvent.setup();
        renderPage();
        await screen.findByText("amartin");

        await user.click(screen.getAllByRole("button", { name: /add client/i })[0]);
        const dialog = await screen.findByRole("dialog");
        await user.type(within(dialog).getByLabelText(/first name/i), "No");
        await user.type(within(dialog).getByLabelText(/last name/i), "Mail");
        await user.click(within(dialog).getByRole("button", { name: /^add client$/i }));

        expect(within(dialog).getByText(/email address is required/i)).toBeInTheDocument();
        expect(api.post).not.toHaveBeenCalled();
    });

    it("sends the activation e-mail after confirmation", async () => {
        api.post.mockResolvedValue({ data: { email: "bob@example.test", username: "bdurand" } });
        const user = userEvent.setup();
        renderPage();
        await screen.findByText("amartin");

        await user.click(screen.getByRole("button", { name: /send activation email to bob durand/i }));
        await user.click(await screen.findByRole("button", { name: /^send activation email$/i }));

        expect(api.post).toHaveBeenCalledWith("/admin/clients/2/activation-email");
    });
});
