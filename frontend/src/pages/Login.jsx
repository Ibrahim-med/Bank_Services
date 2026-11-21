import { useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router-dom";

import {
    Alert,
    Button,
    CircularProgress,
    IconButton,
    InputAdornment,
    TextField
} from "@mui/material";

import PersonOutline from "@mui/icons-material/PersonOutlined";
import LockOutlined from "@mui/icons-material/LockOutlined";
import Visibility from "@mui/icons-material/Visibility";
import VisibilityOff from "@mui/icons-material/VisibilityOff";
import ArrowForward from "@mui/icons-material/ArrowForward";
import VerifiedUserOutlined from "@mui/icons-material/VerifiedUserOutlined";
import ArrowBack from "@mui/icons-material/ArrowBack";

import AuthLayout from "../components/AuthLayout";
import api from "../services/api";

function Login() {
    const navigate = useNavigate();

    const [formData, setFormData] = useState({
        username: "",
        password: ""
    });

    const [fieldErrors, setFieldErrors] = useState({});
    const [showPassword, setShowPassword] = useState(false);
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);

    const handleChange = (event) => {
        const { name, value } = event.target;

        setFormData((previous) => ({
            ...previous,
            [name]: value
        }));

        if (fieldErrors[name]) {
            setFieldErrors((previous) => ({ ...previous, [name]: "" }));
        }

        if (error) setError("");
    };

    const handleSubmit = async (event) => {
        event.preventDefault();
        setError("");

        const nextErrors = {
            username: formData.username.trim() ? "" : "Enter your username.",
            password: formData.password ? "" : "Enter your password."
        };

        if (nextErrors.username || nextErrors.password) {
            setFieldErrors(nextErrors);
            return;
        }

        setLoading(true);

        try {
            const response = await api.post("/auth/login", {
                username: formData.username.trim(),
                password: formData.password
            });

            const data = response.data;

            if (!data.token) {
                setError("Authentication failed. No token was received.");
                return;
            }

            localStorage.setItem("token", data.token);
            localStorage.setItem("username", data.username);
            localStorage.setItem("role", data.role);

            if (data.idClient !== null && data.idClient !== undefined) {
                localStorage.setItem("idClient", data.idClient);
            } else {
                localStorage.removeItem("idClient");
            }

            navigate("/app/dashboard", { replace: true });

        } catch (err) {
            console.error("Login error:", err);

            if (err.response) {
                const status = err.response.status;

                if (status === 401) {
                    setError("Incorrect username or password. Please try again.");
                } else if (status === 403) {
                    // Pending activation or disabled account: show the server's explanation.
                    setError(
                        err.response.data?.message ||
                        "Access denied. You do not have permission to log in."
                    );
                } else if (status >= 500) {
                    setError("Our server is temporarily unavailable. Please try again later.");
                } else {
                    setError(
                        err.response.data?.message ||
                        `Unable to log in. Server error ${status}.`
                    );
                }
            } else if (err.request) {
                setError("Unable to reach the server. Please check your connection.");
            } else {
                setError("Something went wrong. Please try again.");
            }

        } finally {
            setLoading(false);
        }
    };

    return (
        <AuthLayout
            headline="Banking made"
            headlineAccent="simpler."
            description="Manage accounts, monitor transactions and access your banking services from one secure place."
            title="Welcome back"
            subtitle="Sign in to access your BankServices account."
        >
            {error && (
                <Alert severity="error" onClose={() => setError("")} sx={{ mb: 2.5 }}>
                    {error}
                </Alert>
            )}

            <form className="auth-form" onSubmit={handleSubmit} noValidate>
                <TextField
                    fullWidth
                    label="Username"
                    name="username"
                    value={formData.username}
                    onChange={handleChange}
                    placeholder="e.g. alexander.vance"
                    autoComplete="username"
                    autoFocus
                    disabled={loading}
                    required
                    error={Boolean(fieldErrors.username)}
                    helperText={fieldErrors.username}
                    slotProps={{
                        input: {
                            startAdornment: (
                                <InputAdornment position="start">
                                    <PersonOutline fontSize="small" sx={{ color: "text.disabled" }} />
                                </InputAdornment>
                            )
                        }
                    }}
                />

                <TextField
                    fullWidth
                    label="Password"
                    name="password"
                    type={showPassword ? "text" : "password"}
                    value={formData.password}
                    onChange={handleChange}
                    placeholder="Enter your password"
                    autoComplete="current-password"
                    disabled={loading}
                    required
                    error={Boolean(fieldErrors.password)}
                    helperText={fieldErrors.password}
                    slotProps={{
                        input: {
                            startAdornment: (
                                <InputAdornment position="start">
                                    <LockOutlined fontSize="small" sx={{ color: "text.disabled" }} />
                                </InputAdornment>
                            ),
                            endAdornment: (
                                <InputAdornment position="end">
                                    <IconButton
                                        className="icon-swap"
                                        edge="end"
                                        size="small"
                                        onClick={() => setShowPassword((value) => !value)}
                                        aria-label={showPassword ? "Hide password" : "Show password"}
                                    >
                                        {showPassword ? (
                                            <VisibilityOff fontSize="small" />
                                        ) : (
                                            <Visibility fontSize="small" />
                                        )}
                                    </IconButton>
                                </InputAdornment>
                            )
                        }
                    }}
                />

                <div className="auth-form__row" style={{ justifyContent: "flex-end", marginTop: -6 }}>
                    <RouterLink to="/forgot-password" className="text-link">
                        Forgot password?
                    </RouterLink>
                </div>

                <Button
                    type="submit"
                    fullWidth
                    size="large"
                    variant="contained"
                    disabled={loading}
                    endIcon={loading ? null : <ArrowForward />}
                    startIcon={loading ? <CircularProgress size={18} color="inherit" /> : null}
                >
                    {loading ? "Signing in…" : "Sign in"}
                </Button>
            </form>

            <p className="auth-note">
                <VerifiedUserOutlined fontSize="small" aria-hidden="true" />
                Your session is protected and ends when you sign out.
            </p>

            <RouterLink to="/" className="auth-back">
                <ArrowBack fontSize="small" aria-hidden="true" />
                Back to home
            </RouterLink>
        </AuthLayout>
    );
}

export default Login;
