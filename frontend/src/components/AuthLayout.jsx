import { useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import AccountBalance from "@mui/icons-material/AccountBalance";
import VerifiedUserOutlined from "@mui/icons-material/VerifiedUserOutlined";
import LockOutlined from "@mui/icons-material/LockOutlined";
import HistoryOutlined from "@mui/icons-material/HistoryOutlined";

import ThemeToggle from "./ThemeToggle";
import { SecurityIllustration } from "./illustrations/Illustrations";

const HIGHLIGHTS = [
    { icon: VerifiedUserOutlined, text: "Role-based access for administrators and clients" },
    { icon: LockOutlined, text: "Token-based sessions that end when you sign out" },
    { icon: HistoryOutlined, text: "Complete history of accounts and transactions" }
];

/* Set once the card entrance has played in this browser session. */
let cardHasEntered = false;

/*
 * Shared frame for Login, Forgot Password and Reset Password:
 * brand panel on the left (hidden on small screens) and the
 * form panel on the right.
 */
export default function AuthLayout({
    headline,
    headlineAccent,
    description,
    icon,
    title,
    subtitle,
    children
}) {
    // Only the first authentication page shown plays the card entrance.
    const [animateCard] = useState(() => {
        const first = !cardHasEntered;
        cardHasEntered = true;
        return first;
    });

    return (
        <div className="auth-page">
            <div className={animateCard ? "auth-card auth-card--enter" : "auth-card"}>
                <aside className="auth-brand">
                    <RouterLink to="/" className="auth-brand__logo">
                        <span className="brand-mark" aria-hidden="true">
                            <AccountBalance fontSize="small" />
                        </span>
                        <span>BankServices</span>
                    </RouterLink>

                    <div key={headline} className="auth-brand__body">
                        <h2 className="auth-brand__headline">
                            {headline}
                            {headlineAccent && <span>{headlineAccent}</span>}
                        </h2>

                        {description && <p className="auth-brand__description">{description}</p>}

                        <ul className="auth-brand__list">
                            {HIGHLIGHTS.map((item) => (
                                <li key={item.text}>
                                    <item.icon fontSize="small" aria-hidden="true" />
                                    <span>{item.text}</span>
                                </li>
                            ))}
                        </ul>
                    </div>

                    <p className="auth-brand__footnote">Secure banking management platform</p>

                    <div className="auth-brand__art" aria-hidden="true">
                        <SecurityIllustration className="ill--brand" />
                    </div>
                </aside>

                <div className="auth-panel">
                    <div className="auth-panel__top">
                        <RouterLink to="/" className="auth-panel__mobile-logo">
                            <span className="brand-mark brand-mark--sm" aria-hidden="true">
                                <AccountBalance sx={{ fontSize: 16 }} />
                            </span>
                            BankServices
                        </RouterLink>

                        <ThemeToggle />
                    </div>

                    <div className="auth-panel__content">
                        {icon && (
                            <span className="auth-panel__icon" aria-hidden="true">
                                {icon}
                            </span>
                        )}

                        <h1 className="auth-panel__title">{title}</h1>
                        {subtitle && <p className="auth-panel__subtitle">{subtitle}</p>}

                        {children}
                    </div>
                </div>
            </div>

            <footer className="auth-footer">
                © {new Date().getFullYear()} BankServices · Banking Management System
            </footer>
        </div>
    );
}
