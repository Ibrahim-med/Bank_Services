import { useState } from "react";
import { Link as RouterLink, useNavigate, useSearchParams } from "react-router-dom";
import {
    Alert,
    Button,
    CircularProgress,
    InputAdornment,
    TextField
} from "@mui/material";

import HowToRegOutlined from "@mui/icons-material/HowToRegOutlined";
import EmailOutlined from "@mui/icons-material/EmailOutlined";
import CheckCircle from "@mui/icons-material/CheckCircle";
import RadioButtonUnchecked from "@mui/icons-material/RadioButtonUnchecked";
import ArrowBack from "@mui/icons-material/ArrowBack";

import AuthLayout from "../components/AuthLayout";
import PasswordField from "../components/PasswordField";
import api from "../services/api";
import { EMAIL_PATTERN, isPasswordLengthOk, validateNewPassword } from "../utils/validation";

/* Form to request a new activation link (expired / invalid / used link). */
function ResendActivation() {
    const [email, setEmail] = useState("");
    const [emailError, setEmailError] = useState("");
    const [loading, setLoading] = useState(false);
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");

    async function submit(event) {
        event.preventDefault();
        setError("");

        if (!EMAIL_PATTERN.test(email.trim())) {
            setEmailError("Enter a valid email address.");
            return;
        }

        setLoading(true);

        try {
            const { data } = await api.post("/auth/resend-activation", { email: email.trim() });
            setMessage(data?.message || "If a pending account exists for this email, a new activation link will be sent.");
        } catch (err) {
            setError(err.response?.data?.message || "Unable to send a new link. Please try again.");
        } finally {
            setLoading(false);
        }
    }

    return (
        <form className="auth-form" onSubmit={submit} noValidate aria-label="Request a new activation link">
            {error && <Alert severity="error">{error}</Alert>}
            {message && <Alert severity="success">{message}</Alert>}

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
                required
                disabled={loading || Boolean(message)}
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
                variant="outlined"
                disabled={loading || Boolean(message)}
                startIcon={loading ? <CircularProgress size={18} color="inherit" /> : null}
            >
                {loading ? "Sending…" : message ? "Link requested" : "Send me a new activation link"}
            </Button>
        </form>
    );
}

/*
 * Account activation (link e-mailed when an administrator registers a client):
 * the client chooses and confirms a password, then signs in.
 */
export default function ActivateAccount() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const token = searchParams.get("token") || "";

    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);
    const [showConfirm, setShowConfirm] = useState(false);

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");
    const [linkProblem, setLinkProblem] = useState(!token);
    const [success, setSuccess] = useState(false);
    const [submitted, setSubmitted] = useState(false);

    const lengthOk = isPasswordLengthOk(password);
    const matches = password.length > 0 && password === confirmPassword;

    async function handleSubmit(event) {
        event.preventDefault();
        setError("");
        setSubmitted(true);

        const problem = validateNewPassword(password, confirmPassword);

        if (problem) {
            setError(problem);
            return;
        }

        setLoading(true);

        try {
            await api.post("/auth/activate", { token, password, confirmPassword });
            setSuccess(true);
        } catch (err) {
            if (err.response) {
                const message = err.response.data?.message || "This activation link is invalid or expired.";
                setError(message);
                // Invalid, expired or already used link: offer a new one.
                if (/link/i.test(message)) setLinkProblem(true);
            } else {
                setError("Unable to connect to the server. Please try again.");
            }
        } finally {
            setLoading(false);
        }
    }

    return (
        <AuthLayout
            headline="Welcome to"
            headlineAccent="BankServices."
            description="Activate your online banking access by choosing your own password. We never send passwords by email."
            icon={<HowToRegOutlined />}
            title="Activate your account"
            subtitle={
                success
                    ? "Your account is active. Sign in with the username from your email."
                    : linkProblem
                        ? "This activation link cannot be used. Request a new one below."
                        : "Choose a password for your account, then confirm it."
            }
        >
            {error && !success && (
                <Alert severity="error" onClose={() => setError("")} sx={{ mb: 2.5 }}>
                    {error}
                </Alert>
            )}

            {success ? (
                <>
                    <Alert severity="success" sx={{ mb: 2.5 }}>
                        Your account has been activated.
                    </Alert>

                    <Button fullWidth size="large" variant="contained" onClick={() => navigate("/login", { replace: true })}>
                        Go to sign in
                    </Button>
                </>
            ) : linkProblem ? (
                <>
                    {!token && (
                        <Alert severity="warning" sx={{ mb: 2.5 }}>
                            This activation link is missing its token.
                        </Alert>
                    )}
                    <ResendActivation />
                </>
            ) : (
                <form className="auth-form" onSubmit={handleSubmit} noValidate>
                    <PasswordField
                        label="Password"
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
                        helperText={confirmPassword.length > 0 && !matches ? "The passwords do not match." : ""}
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
                        {loading ? "Activating…" : "Activate my account"}
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
