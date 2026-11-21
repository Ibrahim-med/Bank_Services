import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";

import ActivateAccount from "./ActivateAccount";
import api from "../services/api";

vi.mock("../services/api", () => ({ default: { get: vi.fn(), post: vi.fn() } }));

const renderAt = (url) =>
    render(
        <MemoryRouter initialEntries={[url]}>
            <ActivateAccount />
        </MemoryRouter>
    );

const fillPasswords = async (user, password, confirm) => {
    await user.type(screen.getByLabelText(/^Password/, { selector: "input" }), password);
    await user.type(screen.getByLabelText(/^Confirm password/, { selector: "input" }), confirm);
};

describe("ActivateAccount page", () => {
    beforeEach(() => vi.clearAllMocks());

    it("sends the token with the password and its confirmation", async () => {
        api.post.mockResolvedValue({ data: { message: "ok" } });
        const user = userEvent.setup();
        renderAt("/activate-account?token=abc123");

        await fillPasswords(user, "ElisePass123", "ElisePass123");
        await user.click(screen.getByRole("button", { name: /activate my account/i }));

        expect(api.post).toHaveBeenCalledWith("/auth/activate", {
            token: "abc123",
            password: "ElisePass123",
            confirmPassword: "ElisePass123"
        });
        expect(await screen.findByText(/your account has been activated/i)).toBeInTheDocument();
    });

    it("does not call the API when the passwords differ", async () => {
        const user = userEvent.setup();
        renderAt("/activate-account?token=abc123");

        await fillPasswords(user, "ElisePass123", "Different123");
        await user.click(screen.getByRole("button", { name: /activate my account/i }));

        expect(api.post).not.toHaveBeenCalled();
        expect(screen.getAllByText(/do not match/i).length).toBeGreaterThan(0);
    });

    it("offers a new link when the server says the link was already used", async () => {
        api.post.mockRejectedValueOnce({
            response: { status: 400, data: { message: "This activation link has already been used." } }
        });
        const user = userEvent.setup();
        renderAt("/activate-account?token=used");

        await fillPasswords(user, "ElisePass123", "ElisePass123");
        await user.click(screen.getByRole("button", { name: /activate my account/i }));

        expect(await screen.findByText(/already been used/i)).toBeInTheDocument();

        api.post.mockResolvedValueOnce({ data: { message: "If a pending account exists for this email, a new activation link will be sent." } });
        await user.type(screen.getByLabelText(/email address/i), "elise@example.test");
        await user.click(screen.getByRole("button", { name: /send me a new activation link/i }));

        expect(api.post).toHaveBeenLastCalledWith("/auth/resend-activation", { email: "elise@example.test" });
        expect(await screen.findByText(/new activation link will be sent/i)).toBeInTheDocument();
    });

    it("shows the resend form when the token is missing", () => {
        renderAt("/activate-account");

        expect(screen.getByText(/missing its token/i)).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /send me a new activation link/i })).toBeInTheDocument();
    });
});
