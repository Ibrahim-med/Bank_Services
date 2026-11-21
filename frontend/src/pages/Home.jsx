import { Fragment, useEffect, useRef, useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import { Button, IconButton, useMediaQuery } from "@mui/material";
import { useTheme } from "@mui/material/styles";

import AccountBalance from "@mui/icons-material/AccountBalance";
import ArrowForward from "@mui/icons-material/ArrowForward";
import ArrowUpward from "@mui/icons-material/ArrowUpward";
import CheckCircleRounded from "@mui/icons-material/CheckCircleRounded";
import CheckRounded from "@mui/icons-material/CheckRounded";
import MenuRounded from "@mui/icons-material/MenuRounded";
import CloseRounded from "@mui/icons-material/CloseRounded";
import AdminPanelSettingsOutlined from "@mui/icons-material/AdminPanelSettingsOutlined";
import PersonOutlineRounded from "@mui/icons-material/PersonOutlineRounded";
import KeyOutlined from "@mui/icons-material/KeyOutlined";
import ManageAccountsOutlined from "@mui/icons-material/ManageAccountsOutlined";
import PasswordOutlined from "@mui/icons-material/PasswordOutlined";
import LockClockOutlined from "@mui/icons-material/LockClockOutlined";
import ShieldOutlined from "@mui/icons-material/ShieldOutlined";

import ThemeToggle from "../components/ThemeToggle";
import Reveal from "../components/motion/Reveal";
import {
    AccountsArt,
    BillsArt,
    Blob,
    HeroIllustration,
    HistoryArt,
    SecurityIllustration,
    TransfersArt,
    VaultIllustration,
    WelcomeIllustration
} from "../components/illustrations/Illustrations";
import useInView from "../hooks/useInView";
import useCountUp from "../hooks/useCountUp";
import useScrolled from "../hooks/useScrolled";
import useActiveSection from "../hooks/useActiveSection";
import usePrefersReducedMotion from "../hooks/usePrefersReducedMotion";

import "../styles/home.css";

// ======================================================
// CONTENT (real BankServices capabilities only)
// ======================================================

const NAV_LINKS = [
    { label: "How it works", href: "#how" },
    { label: "Services", href: "#services" },
    { label: "Roles", href: "#why" },
    { label: "Security", href: "#security" }
];

const SECTION_IDS = NAV_LINKS.map((link) => link.href.slice(1));

const TECHNOLOGIES = [
    { name: "React", logo: "/images/tech/react-original.svg" },
    { name: "Spring Boot", logo: "/images/tech/spring-original.svg" },
    { name: "MySQL", logo: "/images/tech/mysql-original.svg" },
    { name: "JUnit", logo: "/images/tech/junit-plain.svg" },
    { name: "Git", logo: "/images/tech/git-original.svg" }
];

/* Counts of what the platform offers (no financial figures). */
const GLANCE = [
    { value: 2, label: "User roles", hint: "Administrator and client" },
    { value: 2, label: "Account types", hint: "Current and savings" },
    { value: 4, label: "Banking operations", hint: "Deposit, withdrawal, transfer, bill payment" },
    { value: 5, label: "Bill categories", hint: "Electricity, water, internet, telephone, other" }
];

const STEPS = [
    {
        title: "Your bank registers you",
        text: "An administrator creates your client profile and opens your current or savings accounts."
    },
    {
        title: "Activate your online access",
        text: "You receive a single-use activation link by email and choose your own password. No password is ever sent to you."
    },
    {
        title: "Manage your money",
        text: "Make deposits, withdrawals and transfers between your accounts, with balances updated immediately."
    },
    {
        title: "Pay bills, keep receipts",
        text: "Pay electricity, water, internet and telephone bills from your account and download a receipt for every payment."
    }
];

const SERVICES = [
    {
        label: "Accounts",
        title: "Current and savings accounts",
        text: "Follow balances, overdraft limits and account status in one place.",
        Art: AccountsArt
    },
    {
        label: "Operations",
        title: "Deposits, withdrawals and transfers",
        text: "Move money between accounts with checks on balance, ownership and account status.",
        Art: TransfersArt
    },
    {
        label: "Bills",
        title: "Bill payments with receipts",
        text: "Pay unpaid bills from the account of your choice and keep a receipt for each payment.",
        Art: BillsArt
    },
    {
        label: "History",
        title: "Complete transaction history",
        text: "Search and filter every operation by type, account, amount, date and status.",
        Art: HistoryArt
    }
];

const ROLES = [
    {
        title: "Administrator",
        sub: "Runs the bank's day-to-day operations",
        icon: AdminPanelSettingsOutlined,
        blob: 1,
        items: [
            "Register clients and send their activation links",
            "Open, close and reopen current and savings accounts",
            "Issue bills to clients and cancel unpaid ones",
            "Review every account and transaction"
        ]
    },
    {
        title: "Client",
        sub: "Manages their own money online",
        icon: PersonOutlineRounded,
        blob: 3,
        items: [
            "Activate online access with a personal password",
            "Make deposits, withdrawals and transfers",
            "Pay bills and download payment receipts",
            "Follow accounts, balances and history"
        ]
    }
];

const SECURITY_POINTS = [
    {
        icon: KeyOutlined,
        title: "Signed session tokens",
        text: "Every request is authenticated with a signed session token that expires."
    },
    {
        icon: ManageAccountsOutlined,
        title: "Role-based access",
        text: "Administrators and clients only reach what their role allows, and clients only see their own data."
    },
    {
        icon: PasswordOutlined,
        title: "Hashed passwords",
        text: "Passwords are stored as BCrypt hashes. Activation and reset links are single-use and time-limited."
    },
    {
        icon: LockClockOutlined,
        title: "Protected payments",
        text: "Bill payments use the amount stored by the bank and lock the account while the payment runs."
    }
];

// ======================================================
// HOOKS
// ======================================================

/*
 * The hero is sticky so the page "sheet" slides over it. A hero taller
 * than the viewport gets a negative `top`, so it first scrolls fully
 * into view and only then stays put.
 */
function useStickyHero() {
    const ref = useRef(null);

    useEffect(() => {
        const node = ref.current;

        if (!node) return undefined;

        const update = () => {
            node.style.top = `${Math.min(0, window.innerHeight - node.offsetHeight)}px`;
        };

        update();
        window.addEventListener("resize", update);

        const observer = typeof ResizeObserver !== "undefined" ? new ResizeObserver(update) : null;
        observer?.observe(node);

        return () => {
            window.removeEventListener("resize", update);
            observer?.disconnect();
        };
    }, []);

    return ref;
}

// ======================================================
// NAVBAR
// ======================================================

function HomeNavbar({ onSignIn }) {
    const theme = useTheme();
    const isDesktop = useMediaQuery(theme.breakpoints.up("md"));
    const scrolled = useScrolled(24);
    const activeSection = useActiveSection(SECTION_IDS);

    const [open, setOpen] = useState(false);
    const menuOpen = open && !isDesktop;

    const shellRef = useRef(null);
    const toggleRef = useRef(null);
    const firstLinkRef = useRef(null);

    useEffect(() => {
        if (!menuOpen) return undefined;

        firstLinkRef.current?.focus();

        const onKeyDown = (event) => {
            if (event.key === "Escape") {
                setOpen(false);
                toggleRef.current?.focus();
            }
        };

        const onPointerDown = (event) => {
            if (!shellRef.current?.contains(event.target)) setOpen(false);
        };

        document.addEventListener("keydown", onKeyDown);
        document.addEventListener("pointerdown", onPointerDown);

        return () => {
            document.removeEventListener("keydown", onKeyDown);
            document.removeEventListener("pointerdown", onPointerDown);
        };
    }, [menuOpen]);

    const close = () => setOpen(false);
    const floating = scrolled || menuOpen;

    return (
        <header className={`hn ${scrolled ? "hn--floating" : ""} ${menuOpen ? "hn--open" : ""}`.trim()}>
            <div className="ds-container">
                <div className="hn__shell" ref={shellRef}>
                    <a href="#top" className="hn__brand" onClick={close}>
                        <span className="brand-mark" aria-hidden="true">
                            <AccountBalance fontSize="small" />
                        </span>
                        BankServices
                    </a>

                    <nav className="hn__links" aria-label="Page sections">
                        {NAV_LINKS.map((item) => {
                            const isActive = activeSection === item.href.slice(1);

                            return (
                                <a
                                    key={item.href}
                                    href={item.href}
                                    className={`hn__link ${isActive ? "is-active" : ""}`.trim()}
                                    aria-current={isActive ? "location" : undefined}
                                >
                                    {item.label}
                                </a>
                            );
                        })}
                    </nav>

                    <div className="hn__actions">
                        <ThemeToggle tone={floating ? "default" : "onDark"} />

                        <Button
                            onClick={onSignIn}
                            endIcon={<ArrowForward />}
                            className={`ds-btn ds-btn--sm hn__signin ${floating ? "ds-btn--primary" : "ds-btn--light"}`}
                        >
                            Sign in
                        </Button>

                        <IconButton
                            ref={toggleRef}
                            className="hn__toggle icon-swap"
                            onClick={() => setOpen((value) => !value)}
                            aria-label={menuOpen ? "Close menu" : "Open menu"}
                            aria-expanded={menuOpen}
                            aria-controls="home-mobile-menu"
                        >
                            {menuOpen ? <CloseRounded key="close" /> : <MenuRounded key="open" />}
                        </IconButton>
                    </div>

                    <div id="home-mobile-menu" className={`hn__menu ${menuOpen ? "is-open" : ""}`.trim()} inert={!menuOpen}>
                        <div className="hn__menu-inner">
                            <nav className="hn__menu-panel" aria-label="Page sections">
                                {NAV_LINKS.map((item, index) => (
                                    <a
                                        key={item.href}
                                        ref={index === 0 ? firstLinkRef : undefined}
                                        href={item.href}
                                        className={`hn__menu-link ${activeSection === item.href.slice(1) ? "is-active" : ""}`.trim()}
                                        onClick={close}
                                    >
                                        {item.label}
                                        <ArrowForward fontSize="small" aria-hidden="true" />
                                    </a>
                                ))}

                                <Button
                                    onClick={() => {
                                        close();
                                        onSignIn();
                                    }}
                                    endIcon={<ArrowForward />}
                                    className="ds-btn ds-btn--primary"
                                    sx={{ mt: 2 }}
                                    fullWidth
                                >
                                    Sign in
                                </Button>
                            </nav>
                        </div>
                    </div>
                </div>
            </div>
        </header>
    );
}

// ======================================================
// SECTIONS
// ======================================================

function Hero({ onSignIn }) {
    const ref = useStickyHero();

    return (
        <section id="top" className="hero" ref={ref} aria-labelledby="hero-title">
            <div className="ds-container">
                <div className="hero__grid">
                    <div className="hero__intro">
                        <span className="hero__badge ds-label">
                            <span className="hero__badge-dot" aria-hidden="true">
                                <ShieldOutlined sx={{ fontSize: 14 }} />
                            </span>
                            Banking management platform
                        </span>

                        <h1 id="hero-title" className="hero__title ds-display">
                            {["Banking", "made"].map((word, index) => (
                                <Fragment key={word}>
                                    <span className="hero__word" style={{ "--i": index }}>
                                        <span>{word}</span>
                                    </span>{" "}
                                </Fragment>
                            ))}
                            <br />
                            <span className="hero__word" style={{ "--i": 2 }}>
                                <span className="hero__accent">simpler.</span>
                            </span>
                        </h1>

                        <p className="hero__lead ds-lead">
                            BankServices is a centralized platform for managing clients, bank
                            accounts, transfers and bill payments with a clear, organized and
                            secure experience.
                        </p>

                        <div className="hero__ctas">
                            <Button
                                onClick={onSignIn}
                                endIcon={<ArrowForward />}
                                className="ds-btn ds-btn--lg ds-btn--sand"
                            >
                                Access your account
                            </Button>
                            <a href="#services" className="ds-btn ds-btn--lg ds-btn--outline-light">
                                Explore services
                            </a>
                        </div>

                        <ul className="hero__checks">
                            {["Organized management", "Easy access", "Role-based security"].map((label) => (
                                <li key={label}>
                                    <CheckCircleRounded aria-hidden="true" />
                                    {label}
                                </li>
                            ))}
                        </ul>
                    </div>

                    <div className="hero__art">
                        <HeroIllustration className="ill--brand" />
                    </div>
                </div>

                <div id="technology" className="tech-strip" aria-label="Technologies used by BankServices">
                    <span className="tech-strip__label ds-label">Built with</span>
                    <ul className="tech-strip__list">
                        {TECHNOLOGIES.map((tech) => (
                            <li key={tech.name} className="tech-strip__item">
                                <img src={tech.logo} alt="" width="28" height="28" loading="lazy" decoding="async" />
                                {tech.name}
                            </li>
                        ))}
                    </ul>
                </div>
            </div>
        </section>
    );
}

function GlanceValue({ value, start }) {
    // Visual count-up once visible; assistive tech always gets the real value.
    // With reduced motion the real value is shown straight away.
    const reduced = usePrefersReducedMotion();
    const current = useCountUp(start || reduced ? value : 0, 1200);

    return (
        <span className="glance-value">
            <span aria-hidden="true">{Math.round(current)}</span>
            <span className="sr-only">{value}</span>
        </span>
    );
}

function Glance() {
    const [ref, inView] = useInView({ threshold: 0.35 });

    return (
        <section className="plain" aria-labelledby="glance-title">
            <div className="ds-container">
                <Reveal className="section-head section-head--split">
                    <div>
                        <span className="home-eyebrow ds-label">At a glance</span>
                        <h2 id="glance-title" className="ds-h2">One platform for the whole banking day</h2>
                    </div>
                    <p className="ds-lead">
                        Everything administrators and clients need, from opening an account
                        to paying the last bill of the month.
                    </p>
                </Reveal>

                <div ref={ref}>
                    <ul className={`glance-grid reveal reveal--stagger ${inView ? "is-visible" : ""}`.trim()}>
                        {GLANCE.map((item) => (
                            <li key={item.label} className="glance-item">
                                <GlanceValue value={item.value} start={inView} />
                                <span className="glance-label ds-label">{item.label}</span>
                                <p className="glance-hint">{item.hint}</p>
                            </li>
                        ))}
                    </ul>
                </div>
            </div>
        </section>
    );
}

function ConnectorPath() {
    return (
        <svg className="step__connector" viewBox="0 0 54 140" preserveAspectRatio="none" aria-hidden="true">
            <path d="M2 6 C46 18 54 70 34 132" vectorEffect="non-scaling-stroke" />
        </svg>
    );
}

function HowItWorks() {
    return (
        <section id="how" className="slab slab--sand" aria-labelledby="how-title">
            <div className="ds-container">
                <div className="how-grid">
                    <Reveal variant="left" className="how-art">
                        <VaultIllustration />
                    </Reveal>

                    <div>
                        <Reveal>
                            <span className="home-eyebrow ds-label">How it works</span>
                            <h2 id="how-title" className="ds-h2" style={{ marginBottom: "clamp(32px, 4vw, 52px)" }}>
                                From registration to receipts
                            </h2>
                        </Reveal>

                        <Reveal as="ol" stagger className="steps">
                            {STEPS.map((step, index) => (
                                <li key={step.title} className="step">
                                    <span className="step__badge" aria-hidden="true">
                                        <Blob variant={index} />
                                        <span>{String(index + 1).padStart(2, "0")}</span>
                                    </span>
                                    <div>
                                        <h3 className="step__title ds-h3">
                                            <span className="sr-only">Step {index + 1}: </span>
                                            {step.title}
                                        </h3>
                                        <p className="step__text">{step.text}</p>
                                    </div>

                                    {index < STEPS.length - 1 ? (
                                        <ConnectorPath />
                                    ) : (
                                        <svg className="step__plane" viewBox="0 0 64 46" aria-hidden="true">
                                            <path d="M0 22 L64 0 L30 46 L24 30 Z" />
                                        </svg>
                                    )}
                                </li>
                            ))}
                        </Reveal>
                    </div>
                </div>
            </div>
        </section>
    );
}

function Services() {
    return (
        <section id="services" className="slab slab--gradient" aria-labelledby="services-title">
            <div className="ds-container">
                <Reveal className="section-head section-head--split">
                    <div>
                        <span className="home-eyebrow ds-label">Services</span>
                        <h2 id="services-title" className="ds-h2">Everyday banking, beautifully organized</h2>
                    </div>
                    <p className="ds-lead">
                        Accounts, operations, bills and history live together, so every
                        balance and every payment is always one click away.
                    </p>
                </Reveal>

                <Reveal stagger className="service-grid">
                    {SERVICES.map(({ label, title, text, Art }) => (
                        <article key={label} className="service-card">
                            <span className="service-card__label ds-label">{label}</span>
                            <h3 className="service-card__title ds-h3">{title}</h3>
                            <p className="service-card__text">{text}</p>
                            <RouterLink to="/login" className="service-card__link ds-arrow-link">
                                Sign in to start
                                <ArrowForward fontSize="small" aria-hidden="true" />
                                <span className="sr-only"> with {label.toLowerCase()}</span>
                            </RouterLink>
                            <Art className="service-card__art" />
                        </article>
                    ))}
                </Reveal>
            </div>
        </section>
    );
}

function Roles() {
    return (
        <section id="why" className="plain" aria-labelledby="roles-title">
            <div className="ds-container">
                <Reveal className="section-head section-head--center">
                    <span className="home-eyebrow ds-label">Why BankServices</span>
                    <h2 id="roles-title" className="ds-h2">Designed for every role</h2>
                    <p className="ds-lead">
                        Two clear spaces with the right tools for each person, and nothing
                        they are not allowed to see.
                    </p>
                </Reveal>

                <Reveal as="ul" stagger className="roles">
                    {ROLES.map(({ title, sub, icon: Icon, blob, items }) => (
                        <li key={title} className="role">
                            <span className="role__avatar" aria-hidden="true">
                                <Blob variant={blob} />
                                <Icon />
                            </span>
                            <h3 className="role__title ds-h3">{title}</h3>
                            <p className="role__sub">{sub}</p>
                            <ul className="role__list">
                                {items.map((item) => (
                                    <li key={item}>
                                        <CheckRounded aria-hidden="true" />
                                        {item}
                                    </li>
                                ))}
                            </ul>
                        </li>
                    ))}
                </Reveal>
            </div>
        </section>
    );
}

function Security() {
    return (
        <section id="security" className="slab slab--sand" aria-labelledby="security-title">
            <div className="ds-container">
                <div className="security-grid">
                    <Reveal variant="left" className="security-art">
                        <SecurityIllustration />
                    </Reveal>

                    <div>
                        <Reveal>
                            <span className="home-eyebrow ds-label">Security</span>
                            <h2 id="security-title" className="ds-h2">Security built into every request</h2>
                        </Reveal>

                        <Reveal as="ul" stagger className="security-points">
                            {SECURITY_POINTS.map(({ icon: Icon, title, text }) => (
                                <li key={title} className="security-point">
                                    <span className="security-point__icon" aria-hidden="true">
                                        <Icon />
                                    </span>
                                    <h3 className="ds-h3">{title}</h3>
                                    <p>{text}</p>
                                </li>
                            ))}
                        </Reveal>
                    </div>
                </div>
            </div>
        </section>
    );
}

function FinalCta({ onSignIn }) {
    return (
        <section className="slab slab--gradient" aria-labelledby="cta-title">
            <div className="ds-container">
                <div className="cta-grid">
                    <Reveal>
                        <span className="home-eyebrow ds-label">Get started</span>
                        <h2 id="cta-title" className="ds-h2">Ready to manage your banking?</h2>
                        <p className="ds-lead" style={{ marginTop: 22, maxWidth: 520 }}>
                            Sign in to manage accounts, transfers and bills, or activate the
                            online access your bank created for you.
                        </p>
                        <div className="cta-actions">
                            <Button onClick={onSignIn} endIcon={<ArrowForward />} className="ds-btn ds-btn--lg ds-btn--sand">
                                Sign in
                            </Button>
                            <RouterLink to="/activate-account" className="ds-btn ds-btn--lg ds-btn--outline-light">
                                Activate your account
                            </RouterLink>
                        </div>
                    </Reveal>

                    <Reveal variant="scale" className="cta-art">
                        <WelcomeIllustration className="ill--brand" />
                    </Reveal>
                </div>
            </div>
        </section>
    );
}

function Footer() {
    return (
        <footer className="home-footer">
            <div className="ds-container">
                <div className="home-footer__grid">
                    <div>
                        <a href="#top" className="home-footer__brand">
                            <span className="brand-mark" aria-hidden="true">
                                <AccountBalance />
                            </span>
                            BankServices
                        </a>
                        <p className="home-footer__tagline">
                            Banking management for administrators and clients: accounts,
                            operations, bills and receipts in one place.
                        </p>
                    </div>

                    <nav className="home-footer__col" aria-labelledby="footer-explore">
                        <span id="footer-explore" className="home-footer__heading ds-label">Explore</span>
                        <ul className="home-footer__links">
                            {NAV_LINKS.map((item) => (
                                <li key={item.href}><a href={item.href}>{item.label}</a></li>
                            ))}
                        </ul>
                    </nav>

                    <nav className="home-footer__col" aria-labelledby="footer-account">
                        <span id="footer-account" className="home-footer__heading ds-label">Your account</span>
                        <ul className="home-footer__links">
                            <li><RouterLink to="/login">Sign in</RouterLink></li>
                            <li><RouterLink to="/activate-account">Activate account</RouterLink></li>
                            <li><RouterLink to="/forgot-password">Forgot password</RouterLink></li>
                        </ul>
                    </nav>
                </div>

                <div className="home-footer__bottom">
                    <span>© {new Date().getFullYear()} BankServices · Banking Management System</span>
                    <a href="#top" className="home-footer__top ds-label">
                        Back to top
                        <ArrowUpward fontSize="small" aria-hidden="true" />
                    </a>
                </div>
            </div>
        </footer>
    );
}

// ======================================================
// PAGE
// ======================================================

export default function Home() {
    const navigate = useNavigate();
    const goToLogin = () => navigate("/login");

    return (
        <div className="home">
            <HomeNavbar onSignIn={goToLogin} />
            <Hero onSignIn={goToLogin} />

            <div className="home-sheet">
                <main>
                    <Glance />
                    <HowItWorks />
                    <Services />
                    <Roles />
                    <Security />
                    <FinalCta onSignIn={goToLogin} />
                </main>
                <Footer />
            </div>
        </div>
    );
}
