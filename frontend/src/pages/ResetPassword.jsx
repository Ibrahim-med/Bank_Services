import { useState } from "react";
import { Link as RouterLink, useNavigate, useSearchParams } from "react-router-dom";

import {
    Alert,
    Button,
    CircularProgress
} from "@mui/material";

import LockResetOutlined from "@mui/icons-material/LockResetOutlined";
import CheckCircle from "@mui/icons-material/CheckCircle";
import RadioButtonUnchecked from "@mui/icons-material/RadioButtonUnchecked";
import ArrowBack from "@mui/icons-material/ArrowBack";

import AuthLayout from "../components/AuthLayout";
import PasswordField from "../components/PasswordField";
import api from "../services/api";
import { isPasswordLengthOk, validateNewPassword } from "../utils/validation";

export default function ResetPassword() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();

    const token = searchParams.get("token") || "";

    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);
    const [showConfirm, setShowConfirm] = useState(false);

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState(false);
    const [submitted, setSubmitted] = useState(false);

    const lengthOk = isPasswordLengthOk(password);
    const matches = password.length > 0 && password === confirmPassword;

    const handleSubmit = async (event) => {
        event.preventDefault();
        setError("");
        setSubmitted(true);

        if (!token) {
            setError("The reset link is missing its token.");
            return;
        }

        // Same rules as the backend (8 characters minimum, 72 bytes maximum for BCrypt).
        const problem = validateNewPassword(password, confirmPassword);

        if (problem) {
            setError(problem);
            return;
        }

        setLoading(true);

        try {
            await api.post("/auth/reset-password", {
                token: token,
                newPassword: password
            });

            setSuccess(true);

        } catch (err) {
            console.error("Reset password error:", err);

            if (err.response) {
                setError(
                    err.response.data?.message ||
                    "The reset link is invalid or expired. Request a new one."
                );
            } else {
                setError("Unable to connect to the server. Please try again.");
            }
        } finally {
            setLoading(false);
        }
    };

    return (
        <AuthLayout
            headline="Create a"
            headlineAccent="new password."
            description="Choose a new password to securely access your banking account."
            icon={<LockResetOutlined />}
            title="Reset password"
            subtitle={success ? "You can now sign in with your new password." : "Enter and confirm your new password below."}
        >
            {error && !success && (
                <Alert severity="error" onClose={() => setError("")} sx={{ mb: 2.5 }}>
                    {error}
                </Alert>
            )}

            {success ? (
                <>
                    <Alert severity="success" sx={{ mb: 2.5 }}>
                        Your password has been reset successfully.
                    </Alert>

                    <Button
                        fullWidth
                        size="large"
                        variant="contained"
                        onClick={() => navigate("/login", { replace: true })}
                    >
                        Go to sign in
                    </Button>
                </>
            ) : !token ? (
                <>
                    <Alert severity="warning" sx={{ mb: 2.5 }}>
                        This reset link is missing its token. Please request a new link.
                    </Alert>

                    <Button
                        fullWidth
                        size="large"
                        variant="contained"
                        onClick={() => navigate("/forgot-password")}
                    >
                        Request a new reset link
                    </Button>
                </>
            ) : (
                <form className="auth-form" onSubmit={handleSubmit} noValidate>
                    <PasswordField
                        label="New password"
                        value={password}
                        onChange={setPassword}
                        visible={showPassword}
                        onToggle={() => setShowPassword((value) => !value)}
                        disabled={loading}
                        autoFocus
                        error={submitted && !lengthOk}
                    />

                    <PasswordField
                        label="Confirm password"
                        value={confirmPassword}
                        onChange={setConfirmPassword}
                        visible={showConfirm}
                        onToggle={() => setShowConfirm((value) => !value)}
                        disabled={loading}
                        error={confirmPassword.length > 0 && !matches}
                        helperText={
                            confirmPassword.length > 0 && !matches
                                ? "The passwords do not match."
                                : ""
                        }
                    />

                    <ul className="password-checks" aria-label="Password requirements">
                        <li className={lengthOk ? "is-met" : ""}>
                            {lengthOk ? <CheckCircle /> : <RadioButtonUnchecked />}
                            Between 8 and 72 characters
                        </li>
                        <li className={matches ? "is-met" : ""}>
                            {matches ? <CheckCircle /> : <RadioButtonUnchecked />}
                            Both passwords match
                        </li>
                    </ul>

                    <Button
                        type="submit"
                        fullWidth
                        size="large"
                        variant="contained"
                        disabled={loading}
                        startIcon={loading ? <CircularProgress size={18} color="inherit" /> : null}
                    >
                        {loading ? "Updating…" : "Reset password"}
                    </Button>
                </form>
            )}

            <RouterLink to="/login" className="auth-back">
                <ArrowBack fontSize="small" aria-hidden="true" />
                Back to sign in
            </RouterLink>
        </AuthLayout>
    );
}
