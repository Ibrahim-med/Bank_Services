import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { Alert, Button } from "@mui/material";

import PeopleAltOutlined from "@mui/icons-material/PeopleAltOutlined";
import AccountBalanceWalletOutlined from "@mui/icons-material/AccountBalanceWalletOutlined";
import SwapHorizOutlined from "@mui/icons-material/SwapHorizOutlined";
import AccountBalanceOutlined from "@mui/icons-material/AccountBalanceOutlined";
import InfoOutlined from "@mui/icons-material/InfoOutlined";
import ReceiptLongOutlined from "@mui/icons-material/ReceiptLongOutlined";
import InsightsOutlined from "@mui/icons-material/InsightsOutlined";
import SouthWestRounded from "@mui/icons-material/SouthWestRounded";
import NorthEastRounded from "@mui/icons-material/NorthEastRounded";
import ArrowForward from "@mui/icons-material/ArrowForward";

import api from "../services/api";
import PageHeader from "../components/PageHeader";
import { HeroIllustration } from "../components/illustrations/Illustrations";
import StatCard from "../components/StatCard";
import RefreshButton from "../components/RefreshButton";
import SectionCard from "../components/SectionCard";
import DataTable from "../components/DataTable";
import EmptyState from "../components/EmptyState";
import LoadingState from "../components/LoadingState";
import StatusChip from "../components/StatusChip";
import {
    describeTransactionType,
    formatDateTime,
    formatMoney
} from "../utils/format";

/* ========================================
   DATA HELPERS (unchanged field mapping)
======================================== */

const getTransactionId = (transaction) =>
    transaction.idTransaction ??
    transaction.id_transaction ??
    transaction.id;

const getTransactionType = (transaction) =>
    transaction.typeTransaction ??
    transaction.type_transaction ??
    transaction.type ??
    "UNKNOWN";

const getTransactionStatus = (transaction) =>
    transaction.statut ??
    transaction.status ??
    "UNKNOWN";

const getTransactionDate = (transaction) =>
    transaction.dateTransaction ??
    transaction.date_transaction ??
    transaction.date;

const getTransactionAmount = (transaction) =>
    Number(transaction.montant ?? transaction.amount ?? 0);

const isValidated = (transaction) =>
    ["VALIDEE", "VALIDATED", "SUCCESS"].includes(
        String(getTransactionStatus(transaction)).toUpperCase()
    );

const TYPE_ICONS = {
    DEPOT: <SouthWestRounded />,
    RETRAIT: <NorthEastRounded />,
    VIREMENT: <SwapHorizOutlined />,
    PAIEMENT_FACTURE: <ReceiptLongOutlined />
};

/* Validated volume per transaction type, computed from loaded data. */
function buildBreakdown(transactions) {
    const types = ["DEPOT", "RETRAIT", "VIREMENT", "PAIEMENT_FACTURE"];

    const rows = types.map((type) => {
        const matching = transactions.filter(
            (transaction) =>
                String(getTransactionType(transaction)).toUpperCase() === type &&
                isValidated(transaction)
        );

        return {
            type,
            count: matching.length,
            amount: matching.reduce(
                (sum, transaction) => sum + (Number.isFinite(getTransactionAmount(transaction))
                    ? getTransactionAmount(transaction)
                    : 0),
                0
            )
        };
    });

    const total = rows.reduce((sum, row) => sum + row.amount, 0);
    const validatedCount = transactions.filter(isValidated).length;

    return { rows, total, validatedCount, otherCount: transactions.length - validatedCount };
}

/* ========================================
   DASHBOARD COMPONENT
======================================== */

export default function Dashboard() {
    const role = localStorage.getItem("role");
    const idClient = localStorage.getItem("idClient");
    const username = localStorage.getItem("username") || "";

    const isAdmin = role === "ADMIN";

    const [stats, setStats] = useState({
        clients: null,
        accounts: null,
        transactions: null,
        liquidity: null
    });

    const [recentTransactions, setRecentTransactions] = useState([]);
    const [breakdown, setBreakdown] = useState(() => buildBreakdown([]));

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [refreshKey, setRefreshKey] = useState(0);

    /* ========================================
       LOAD DASHBOARD DATA
    ======================================== */

    useEffect(() => {
        let active = true;

        async function loadDashboard() {
            setLoading(true);
            setError("");

            try {
                let clients = [];
                let accounts = [];
                let transactions = [];

                if (isAdmin) {
                    const [
                        clientsResponse,
                        accountsResponse,
                        transactionsResponse
                    ] = await Promise.all([
                        api.get("/clients"),
                        api.get("/comptes"),
                        api.get("/transactions")
                    ]);

                    clients = Array.isArray(clientsResponse.data)
                        ? clientsResponse.data : [];

                    accounts = Array.isArray(accountsResponse.data)
                        ? accountsResponse.data : [];

                    transactions = Array.isArray(transactionsResponse.data)
                        ? transactionsResponse.data : [];

                } else {
                    if (!idClient) {
                        throw new Error(
                            "Your client profile ID is missing. Please sign out and sign in again."
                        );
                    }

                    const [
                        accountsResponse,
                        transactionsResponse
                    ] = await Promise.all([
                        api.get(
                            `/comptes/client/${encodeURIComponent(idClient)}`
                        ),
                        api.get(
                            `/transactions/client/${encodeURIComponent(idClient)}`
                        )
                    ]);

                    accounts = Array.isArray(accountsResponse.data)
                        ? accountsResponse.data : [];

                    transactions = Array.isArray(transactionsResponse.data)
                        ? transactionsResponse.data : [];
                }

                const balancesAvailable = accounts.every(
                    (account) =>
                        account.solde !== null &&
                        account.solde !== undefined &&
                        Number.isFinite(Number(account.solde))
                );

                const liquidity = balancesAvailable
                    ? accounts.reduce(
                        (total, account) =>
                            total + Number(account.solde || 0),
                        0
                    )
                    : null;

                const sortedTransactions = [...transactions].sort(
                    (a, b) =>
                        new Date(getTransactionDate(b) || 0).getTime() -
                        new Date(getTransactionDate(a) || 0).getTime()
                );

                if (active) {
                    setStats({
                        clients: isAdmin ? clients.length : null,
                        accounts: accounts.length,
                        transactions: transactions.length,
                        liquidity
                    });

                    setRecentTransactions(sortedTransactions.slice(0, 5));
                    setBreakdown(buildBreakdown(transactions));
                }

            } catch (err) {
                if (active) {
                    setError(
                        err.response?.data?.message ||
                        err.message ||
                        "Unable to load dashboard data."
                    );
                }

            } finally {
                if (active) {
                    setLoading(false);
                }
            }
        }

        loadDashboard();

        return () => {
            active = false;
        };

    }, [isAdmin, idClient, refreshKey]);

    const refresh = () => setRefreshKey((previous) => previous + 1);
    // Numbers count up (StatCard); skeletons only before the first data.
    const showValue = (value) => (error ? "—" : value ?? 0);
    const firstLoad = loading && stats.accounts === null;

    /* ========================================
       STATISTICS
    ======================================== */

    const cards = isAdmin
        ? [
            {
                label: "Total clients",
                value: showValue(stats.clients),
                icon: <PeopleAltOutlined />,
                tone: "primary",
                hint: "Registered bank clients"
            },
            {
                label: "Bank accounts",
                value: showValue(stats.accounts),
                icon: <AccountBalanceWalletOutlined />,
                tone: "success",
                hint: "Accounts across all clients"
            },
            {
                label: "Transactions",
                value: showValue(stats.transactions),
                icon: <SwapHorizOutlined />,
                tone: "violet",
                hint: "Recorded operations"
            }
        ]
        : [
            {
                label: "My bank accounts",
                value: showValue(stats.accounts),
                icon: <AccountBalanceWalletOutlined />,
                tone: "primary",
                hint: "Your registered accounts"
            },
            {
                label: "My transactions",
                value: showValue(stats.transactions),
                icon: <SwapHorizOutlined />,
                tone: "violet",
                hint: "Your banking activity"
            }
        ];

    /* ========================================
       RECENT TRANSACTIONS TABLE
    ======================================== */

    const columns = [
        {
            key: "type",
            header: "Type",
            mobile: "primary",
            render: (transaction) => {
                const type = getTransactionType(transaction);
                const { label, tone } = describeTransactionType(type);

                return (
                    <div className="identity">
                        <span className={`identity__avatar tone-${tone}`} aria-hidden="true">
                            {TYPE_ICONS[String(type).toUpperCase()] ?? <SwapHorizOutlined />}
                        </span>
                        <div className="identity__text">
                            <strong>{label}</strong>
                            <span>{transaction.description || "No description"}</span>
                        </div>
                    </div>
                );
            }
        },
        {
            key: "amount",
            header: "Amount",
            align: "right",
            mobile: "aside",
            render: (transaction) => (
                <span className="cell-amount">
                    {formatMoney(getTransactionAmount(transaction))}
                </span>
            )
        },
        {
            key: "date",
            header: "Date",
            nowrap: true,
            render: (transaction) => (
                <span className="cell-muted">
                    {formatDateTime(getTransactionDate(transaction))}
                </span>
            )
        },
        {
            key: "status",
            header: "Status",
            render: (transaction) => (
                <StatusChip value={getTransactionStatus(transaction)} />
            )
        }
    ];

    /* ========================================
       RENDER
    ======================================== */

    return (
        <div className="page">
            <PageHeader
                variant="hero"
                eyebrow={isAdmin ? "Administrator workspace" : "Your banking space"}
                art={<HeroIllustration className="ill--brand" />}
                title={username ? `Welcome back, ${username}` : "Dashboard"}
                description={
                    isAdmin
                        ? "Here is an overview of the banking system."
                        : "Here is an overview of your accounts and recent activity."
                }
                actions={<RefreshButton onClick={refresh} loading={loading} />}
            />

            {error && (
                <Alert
                    severity="error"
                    action={
                        <Button color="inherit" size="small" onClick={refresh}>
                            Retry
                        </Button>
                    }
                >
                    {error}
                </Alert>
            )}

            {/* STATISTICS */}
            <div className="stat-grid">
                <StatCard
                    variant="featured"
                    label={isAdmin ? "Managed liquidity" : "My total balance"}
                    value={error || stats.liquidity === null ? "Unavailable" : stats.liquidity}
                    format={formatMoney}
                    hint="Combined account balances"
                    icon={<AccountBalanceOutlined />}
                    loading={firstLoad}
                />

                {cards.map((card) => (
                    <StatCard key={card.label} {...card} loading={firstLoad} />
                ))}
            </div>

            <div className="two-column">
                {/* RECENT TRANSACTIONS */}
                <SectionCard
                    title="Recent transactions"
                    subtitle={
                        isAdmin
                            ? "Latest activity across the banking system"
                            : "Your latest banking activity"
                    }
                    icon={<ReceiptLongOutlined />}
                    actions={
                        <Button
                            component={RouterLink}
                            to="/app/transactions"
                            size="small"
                            endIcon={<ArrowForward fontSize="small" />}
                        >
                            View all
                        </Button>
                    }
                    flush
                >
                    <DataTable
                        ariaLabel="Recent transactions"
                        columns={columns}
                        rows={error ? [] : recentTransactions}
                        getRowId={getTransactionId}
                        loading={loading}
                        loadingLabel="Loading recent transactions…"
                        paginate={false}
                        minWidth={560}
                        empty={
                            <EmptyState
                                icon={<SwapHorizOutlined />}
                                title="No transactions yet"
                                description="Recent deposits, withdrawals and transfers will appear here."
                            />
                        }
                    />
                </SectionCard>

                {/* ACTIVITY BREAKDOWN */}
                <SectionCard
                    title="Activity breakdown"
                    subtitle="Validated volume by transaction type"
                    icon={<InsightsOutlined />}
                >
                    {firstLoad ? (
                        <LoadingState compact label="Calculating…" />
                    ) : error || breakdown.validatedCount === 0 ? (
                        <EmptyState
                            compact
                            icon={<InsightsOutlined />}
                            title="No validated activity"
                            description="The breakdown appears once validated transactions exist."
                        />
                    ) : (
                        <div className="breakdown">
                            {breakdown.rows.map((row) => {
                                const { label, tone } = describeTransactionType(row.type);
                                const share = breakdown.total > 0
                                    ? Math.round((row.amount / breakdown.total) * 100)
                                    : 0;

                                return (
                                    <div key={row.type} className={`breakdown__row tone-${tone}`}>
                                        <div className="breakdown__meta">
                                            <span className="breakdown__label">
                                                <span className="breakdown__swatch" aria-hidden="true" />
                                                {label}s
                                            </span>
                                            <span className="breakdown__value">
                                                {formatMoney(row.amount)}
                                                <small>
                                                    {row.count} · {share}%
                                                </small>
                                            </span>
                                        </div>

                                        <div
                                            className="breakdown__track"
                                            role="progressbar"
                                            aria-label={`${label} share of validated volume`}
                                            aria-valuemin={0}
                                            aria-valuemax={100}
                                            aria-valuenow={share}
                                        >
                                            <div className="breakdown__fill" style={{ width: `${share}%` }} />
                                        </div>
                                    </div>
                                );
                            })}

                            <div className="breakdown__footer">
                                <span>{breakdown.validatedCount} validated</span>
                                <span>{breakdown.otherCount} refused or pending</span>
                            </div>
                        </div>
                    )}
                </SectionCard>
            </div>

            <p className="info-banner">
                <InfoOutlined fontSize="small" aria-hidden="true" />
                <span>
                    You are signed in as {isAdmin ? "an " : "a "}
                    <strong>{isAdmin ? "administrator" : "client"}</strong>.
                    Your dashboard shows information according to your access permissions.
                </span>
            </p>
        </div>
    );
}
