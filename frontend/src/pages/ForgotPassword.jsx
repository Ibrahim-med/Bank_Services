import { useState } from "react";
import { Link as RouterLink } from "react-router-dom";

import {
    Alert,
    Button,
    CircularProgress,
    InputAdornment,
    TextField
} from "@mui/material";

import EmailOutlined from "@mui/icons-material/EmailOutlined";
import ArrowBack from "@mui/icons-material/ArrowBack";
import MarkEmailReadOutlined from "@mui/icons-material/MarkEmailReadOutlined";

import AuthLayout from "../components/AuthLayout";
import api from "../services/api";

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function ForgotPassword() {
    const [email, setEmail] = useState("");
    const [emailError, setEmailError] = useState("");
    const [loading, setLoading] = useState(false);
    const [success, setSuccess] = useState("");
    const [error, setError] = useState("");

    const handleSubmit = async (event) => {
        event.preventDefault();
        setError("");
        setSuccess("");

        if (!email.trim()) {
            setEmailError("Please enter your email address.");
            return;
        }

        if (!EMAIL_PATTERN.test(email.trim())) {
            setEmailError("Please enter a valid email address.");
            return;
        }

        setLoading(true);

        try {
            const response = await api.post("/auth/forgot-password", {
                email: email.trim()
            });

            setSuccess(
                response.data?.message ||
                "If an account exists for this email, a password reset link will be sent."
            );
        } catch (err) {
            console.error("Forgot password error:", err);

            if (err.response) {
                setError(
                    err.response.data?.message ||
                    "Unable to process your request. Please try again."
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
            headline="Account"
            headlineAccent="recovery."
            description="Regain access to your banking account through a secure password recovery process."
            icon={<MarkEmailReadOutlined />}
            title="Forgot your password?"
            subtitle="Enter the email address linked to your account. If it exists, we'll send you a link to reset your password."
        >
            {error && (
                <Alert severity="error" onClose={() => setError("")} sx={{ mb: 2.5 }}>
                    {error}
                </Alert>
            )}

            {success && (
                <Alert severity="success" sx={{ mb: 2.5 }}>
                    {success}
                </Alert>
            )}

            <form className="auth-form" onSubmit={handleSubmit} noValidate>
                <TextField
                    fullWidth
                    type="email"
                    label="Email address"
                    placeholder="name@example.com"
                    value={email}
                    onChange={(event) => {
                        setEmail(event.target.value);
                        if (emailError) setEmailError("");
                    }}
                    autoComplete="email"
                    autoFocus
                    required
                    disabled={loading || Boolean(success)}
                    error={Boolean(emailError)}
                    helperText={emailError}
                    slotProps={{
                        input: {
                            startAdornment: (
                                <InputAdornment position="start">
                                    <EmailOutlined fontSize="small" sx={{ color: "text.disabled" }} />
                                </InputAdornment>
                            )
                        }
                    }}
                />

                <Button
                    type="submit"
                    fullWidth
                    size="large"
                    variant="contained"
                    disabled={loading || Boolean(success)}
                    startIcon={loading ? <CircularProgress size={18} color="inherit" /> : null}
                >
                    {loading ? "Sending…" : success ? "Reset link sent" : "Send reset link"}
                </Button>
            </form>

            <RouterLink to="/login" className="auth-back">
                <ArrowBack fontSize="small" aria-hidden="true" />
                Back to sign in
            </RouterLink>
        </AuthLayout>
    );
}
