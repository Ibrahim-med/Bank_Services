import { useEffect, useMemo, useState } from "react";

import {
    Alert,
    Button,
    CircularProgress,
    InputAdornment,
    MenuItem,
    TextField,
    ToggleButton,
    ToggleButtonGroup
} from "@mui/material";

import SwapHorizIcon from "@mui/icons-material/SwapHoriz";
import ReceiptLongOutlined from "@mui/icons-material/ReceiptLongOutlined";
import SouthWestRounded from "@mui/icons-material/SouthWestRounded";
import NorthEastRounded from "@mui/icons-material/NorthEastRounded";
import ArrowForwardRounded from "@mui/icons-material/ArrowForwardRounded";
import AddCardOutlined from "@mui/icons-material/AddCardOutlined";
import PaymentsOutlined from "@mui/icons-material/PaymentsOutlined";
import FilterListOffOutlined from "@mui/icons-material/FilterListOffOutlined";
import HistoryOutlined from "@mui/icons-material/HistoryOutlined";

import api from "../services/api";
import PageHeader from "../components/PageHeader";
import StatCard from "../components/StatCard";
import RefreshButton from "../components/RefreshButton";
import SectionCard from "../components/SectionCard";
import DataTable from "../components/DataTable";
import EmptyState from "../components/EmptyState";
import LoadingState from "../components/LoadingState";
import SearchField from "../components/SearchField";
import StatusChip from "../components/StatusChip";
import { useToast } from "../context/toastContext";
import {
    describeTransactionType,
    formatDateTime,
    formatMoney,
    getTransactionTypeLabel
} from "../utils/format";

// --------------------------------------------------
// CONSTANTS AND DATA HELPERS (field mapping unchanged)
// --------------------------------------------------

const blank = {
    typeTransaction: "DEPOT",
    montant: "",
    description: "",
    sourceId: "",
    destinationId: ""
};

const DESCRIPTION_LIMIT = 255;

const TYPE_ICONS = {
    DEPOT: <SouthWestRounded />,
    RETRAIT: <NorthEastRounded />,
    VIREMENT: <SwapHorizIcon />,
    PAIEMENT_FACTURE: <ReceiptLongOutlined />
};

const TYPE_HELP = {
    DEPOT: "Money will be added to the selected account.",
    RETRAIT: "Money will be withdrawn from the selected account.",
    VIREMENT: "Money will be transferred between two accounts."
};

const accountId = (account) =>
    account?.idCompte ??
    account?.id_compte ??
    account?.compteId ??
    account?.id;

const getAccountNumber = (account) =>
    account?.numero ??
    account?.numeroCompte ??
    account?.numero_compte ??
    account?.accountNumber ??
    "";

const getTransactionId = (transaction) =>
    transaction.idTransaction ?? transaction.id_transaction;

function getArray(data, possibleKeys = []) {
    if (Array.isArray(data)) return data;

    if (!data || typeof data !== "object") return [];

    const keys = [...possibleKeys, "content", "items", "result", "data"];

    for (const key of keys) {
        if (Array.isArray(data[key])) return data[key];
    }

    for (const key of keys) {
        if (data[key] && typeof data[key] === "object") {
            const result = getArray(data[key], possibleKeys);
            if (result.length > 0) return result;
        }
    }

    return [];
}

function getTransactionAccountNumber(transaction, type, accounts) {
    const isSource = type === "source";

    const nestedAccount = isSource
        ? transaction?.compteSource ??
        transaction?.source ??
        transaction?.sourceCompte
        : transaction?.compteDestination ??
        transaction?.destination ??
        transaction?.destinationCompte;

    const directNumber = getAccountNumber(nestedAccount);

    if (directNumber) return directNumber;

    const flatNumber = isSource
        ? transaction?.numeroCompteSource ??
        transaction?.numero_compte_source
        : transaction?.numeroCompteDestination ??
        transaction?.numero_compte_destination;

    if (flatNumber) return flatNumber;

    const transactionAccountId = isSource
        ? transaction?.idCompteSource ??
        transaction?.id_compte_source ??
        transaction?.compteSourceId ??
        transaction?.compte_source_id ??
        nestedAccount?.idCompte ??
        nestedAccount?.id_compte ??
        nestedAccount?.id
        : transaction?.idCompteDestination ??
        transaction?.id_compte_destination ??
        transaction?.compteDestinationId ??
        transaction?.compte_destination_id ??
        nestedAccount?.idCompte ??
        nestedAccount?.id_compte ??
        nestedAccount?.id;

    if (transactionAccountId == null) return "—";

    const matchingAccount = accounts.find(
        (account) =>
            String(accountId(account)) === String(transactionAccountId)
    );

    return getAccountNumber(matchingAccount) || "—";
}

function accountOptionLabel(account) {
    return `${getAccountNumber(account) || "Account"} — #${accountId(account)}`;
}

// --------------------------------------------------
// MAIN COMPONENT
// --------------------------------------------------

export default function Transactions() {
    const notify = useToast();

    const role = localStorage.getItem("role");
    const idClient = localStorage.getItem("idClient");
    const isAdmin = role === "ADMIN";

    const [accounts, setAccounts] = useState([]);
    const [transactions, setTransactions] = useState([]);
    const [form, setForm] = useState(blank);
    const [fieldErrors, setFieldErrors] = useState({});

    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");
    const [formError, setFormError] = useState("");
    const [refreshKey, setRefreshKey] = useState(0);

    const [search, setSearch] = useState("");
    const [typeFilter, setTypeFilter] = useState("ALL");
    const [statusFilter, setStatusFilter] = useState("ALL");

    const activeAccounts = accounts.filter(
        (account) =>
            String(account.statut ?? account.status ?? "ACTIVE").toUpperCase() === "ACTIVE"
    );

    // --------------------------------------------------
    // LOAD DATA
    // --------------------------------------------------

    useEffect(() => {
        let active = true;

        async function load() {
            setLoading(true);
            setError("");

            try {
                let accountsResponse;
                let transactionsResponse;

                if (isAdmin) {
                    [accountsResponse, transactionsResponse] = await Promise.all([
                        api.get("/comptes"),
                        api.get("/transactions")
                    ]);
                } else {
                    if (!idClient) {
                        throw new Error(
                            "Client ID is missing. Please sign in again."
                        );
                    }

                    [accountsResponse, transactionsResponse] = await Promise.all([
                        api.get(`/comptes/client/${encodeURIComponent(idClient)}`),
                        api.get(`/transactions/client/${encodeURIComponent(idClient)}`)
                    ]);
                }

                if (active) {
                    setAccounts(
                        getArray(accountsResponse.data, ["comptes", "accounts"])
                    );

                    setTransactions(
                        getArray(transactionsResponse.data, ["transactions"])
                    );
                }
            } catch (e) {
                if (active) {
                    setError(
                        e.response?.data?.message ||
                        e.message ||
                        "Could not load transaction data."
                    );
                }
            } finally {
                if (active) setLoading(false);
            }
        }

        load();

        return () => {
            active = false;
        };
    }, [isAdmin, idClient, refreshKey]);

    const reload = () => setRefreshKey((value) => value + 1);

    // Skeletons only before the first data arrives (no replay on refresh).
    const firstLoad = loading && transactions.length === 0 && accounts.length === 0;

    // --------------------------------------------------
    // FORM HANDLING
    // --------------------------------------------------

    function handleTypeChange(type) {
        setForm({
            ...blank,
            typeTransaction: type
        });

        setFieldErrors({});
        setFormError("");
    }

    function handleChange(field, value) {
        setForm((previous) => ({
            ...previous,
            [field]: value
        }));

        if (fieldErrors[field]) {
            setFieldErrors((previous) => ({ ...previous, [field]: "" }));
        }
    }

    // --------------------------------------------------
    // SUBMIT TRANSACTION
    // --------------------------------------------------

    async function submit(e) {
        e.preventDefault();

        setFormError("");

        const amount = Number(form.montant);
        const errors = {};

        if (!Number.isFinite(amount) || amount <= 0) {
            errors.montant = "Please enter an amount greater than zero.";
        }

        if (form.typeTransaction !== "DEPOT" && !form.sourceId) {
            errors.sourceId = "Please select a source account.";
        }

        if (form.typeTransaction !== "RETRAIT" && !form.destinationId) {
            errors.destinationId = "Please select a destination account.";
        }

        if (
            form.typeTransaction === "VIREMENT" &&
            form.sourceId &&
            String(form.sourceId) === String(form.destinationId)
        ) {
            errors.destinationId = "Source and destination accounts must be different.";
        }

        if (Object.keys(errors).length) {
            setFieldErrors(errors);
            return;
        }

        setSaving(true);

        const payload = {
            typeTransaction: form.typeTransaction,
            montant: amount,
            description: form.description.trim(),
            idCompteSource:
                form.typeTransaction === "DEPOT"
                    ? null
                    : Number(form.sourceId),
            idCompteDestination:
                form.typeTransaction === "RETRAIT"
                    ? null
                    : Number(form.destinationId)
        };

        try {
            const { data } = await api.post("/transactions", payload);

            notify(data?.message || "Transaction completed successfully.");

            setForm({ ...blank, typeTransaction: form.typeTransaction });
            setFieldErrors({});
            reload();
        } catch (e) {
            setFormError(
                e.response?.data?.message ||
                "Transaction failed. Please verify the account and amount."
            );
        } finally {
            setSaving(false);
        }
    }

    // --------------------------------------------------
    // SUMMARY
    // --------------------------------------------------

    const summary = useMemo(() => {
        const validated = transactions.filter(
            (transaction) => transaction.statut === "VALIDEE"
        );

        const calculateTotal = (type) =>
            validated
                .filter((transaction) => transaction.typeTransaction === type)
                .reduce(
                    (sum, transaction) =>
                        sum + Number(transaction.montant || 0),
                    0
                );

        return {
            total: transactions.length,
            deposits: calculateTotal("DEPOT"),
            withdrawals: calculateTotal("RETRAIT"),
            transfers: calculateTotal("VIREMENT")
        };
    }, [transactions]);

    // --------------------------------------------------
    // FILTERS
    // --------------------------------------------------

    const filteredTransactions = useMemo(() => {
        const searchValue = search.trim().toLowerCase();

        return transactions.filter((transaction) => {
            const sourceNumber = getTransactionAccountNumber(transaction, "source", accounts);
            const destinationNumber = getTransactionAccountNumber(transaction, "destination", accounts);

            const matchesSearch = [
                getTransactionId(transaction) ?? "",
                transaction.description,
                getTransactionTypeLabel(transaction.typeTransaction),
                sourceNumber,
                destinationNumber
            ].some((value) =>
                String(value ?? "").toLowerCase().includes(searchValue)
            );

            const matchesType =
                typeFilter === "ALL" ||
                transaction.typeTransaction === typeFilter;

            const matchesStatus =
                statusFilter === "ALL" ||
                transaction.statut === statusFilter;

            return matchesSearch && matchesType && matchesStatus;
        });
    }, [transactions, accounts, search, typeFilter, statusFilter]);

    const filtersActive = Boolean(search) || typeFilter !== "ALL" || statusFilter !== "ALL";

    const clearFilters = () => {
        setSearch("");
        setTypeFilter("ALL");
        setStatusFilter("ALL");
    };

    // --------------------------------------------------
    // TABLE COLUMNS
    // --------------------------------------------------

    const columns = [
        {
            key: "type",
            header: "Transaction",
            mobile: "primary",
            render: (transaction) => {
                const { label, tone } = describeTransactionType(transaction.typeTransaction);

                return (
                    <div className="identity">
                        <span className={`identity__avatar tone-${tone}`} aria-hidden="true">
                            {TYPE_ICONS[transaction.typeTransaction] ?? <SwapHorizIcon />}
                        </span>
                        <div className="identity__text">
                            <strong>{label}</strong>
                            <span>#{getTransactionId(transaction) ?? "—"}</span>
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
                <span className="cell-amount">{formatMoney(transaction.montant)}</span>
            )
        },
        {
            key: "source",
            header: "From",
            nowrap: true,
            render: (transaction) => (
                <span className="cell-strong">
                    {getTransactionAccountNumber(transaction, "source", accounts)}
                </span>
            )
        },
        {
            key: "destination",
            header: "To",
            nowrap: true,
            render: (transaction) => (
                <span className="cell-strong">
                    {getTransactionAccountNumber(transaction, "destination", accounts)}
                </span>
            )
        },
        {
            key: "date",
            header: "Date",
            nowrap: true,
            render: (transaction) => (
                <span className="cell-muted">{formatDateTime(transaction.dateTransaction)}</span>
            )
        },
        {
            key: "status",
            header: "Status",
            render: (transaction) => <StatusChip value={transaction.statut} />
        },
        {
            key: "description",
            header: "Description",
            render: (transaction) => (
                <span className="cell-truncate cell-muted" title={transaction.description || ""}>
                    {transaction.description || "—"}
                </span>
            )
        }
    ];

    // --------------------------------------------------
    // FORM PREVIEW
    // --------------------------------------------------

    const findAccount = (id) =>
        activeAccounts.find((account) => String(accountId(account)) === String(id));

    const sourceAccount = findAccount(form.sourceId);
    const destinationAccount = findAccount(form.destinationId);
    const previewAmount = Number(form.montant);

    const accountMenuItems = (list) =>
        list.map((account) => (
            <MenuItem key={accountId(account)} value={accountId(account)}>
                <span style={{ display: "flex", justifyContent: "space-between", gap: 16, width: "100%" }}>
                    <span>{accountOptionLabel(account)}</span>
                    {account.solde !== undefined && account.solde !== null && (
                        <span className="cell-muted">{formatMoney(account.solde)}</span>
                    )}
                </span>
            </MenuItem>
        ));

    // --------------------------------------------------
    // USER INTERFACE
    // --------------------------------------------------

    return (
        <div className="page">
            <PageHeader
                title="Transactions"
                description="Make deposits, withdrawals and transfers, and review your banking history."
                actions={
                    <RefreshButton onClick={reload} loading={loading} disabled={saving} />
                }
            />

            {error && (
                <Alert severity="error" onClose={() => setError("")}>
                    {error}
                </Alert>
            )}

            {/* SUMMARY */}
            <div className="stat-grid">
                <StatCard
                    label="Total transactions"
                    value={summary.total}
                    hint="All recorded operations"
                    icon={<ReceiptLongOutlined />}
                    tone="primary"
                    loading={firstLoad}
                />
                <StatCard
                    label="Deposits"
                    value={summary.deposits}
                    format={formatMoney}
                    hint="Validated deposits"
                    icon={<SouthWestRounded />}
                    tone="success"
                    loading={firstLoad}
                />
                <StatCard
                    label="Withdrawals"
                    value={summary.withdrawals}
                    format={formatMoney}
                    hint="Validated withdrawals"
                    icon={<NorthEastRounded />}
                    tone="warning"
                    loading={firstLoad}
                />
                <StatCard
                    label="Transfers"
                    value={summary.transfers}
                    format={formatMoney}
                    hint="Validated transfers"
                    icon={<SwapHorizIcon />}
                    tone="violet"
                    loading={firstLoad}
                />
            </div>

            {/* NEW TRANSACTION */}
            <SectionCard
                title="New transaction"
                subtitle="Choose an operation, then fill in the details."
                icon={<AddCardOutlined />}
            >
                {firstLoad ? (
                    <LoadingState compact label="Loading account information…" />
                ) : activeAccounts.length === 0 ? (
                    <Alert severity="info">
                        No active bank accounts are available for transactions.
                    </Alert>
                ) : (
                    <form onSubmit={submit} noValidate>
                        {formError && (
                            <Alert severity="error" onClose={() => setFormError("")} sx={{ mb: 2 }}>
                                {formError}
                            </Alert>
                        )}

                        <ToggleButtonGroup
                            exclusive
                            fullWidth
                            value={form.typeTransaction}
                            onChange={(_event, value) => value && handleTypeChange(value)}
                            aria-label="Transaction type"
                            disabled={saving}
                            sx={{ mb: 2.5, maxWidth: 520 }}
                        >
                            {["DEPOT", "RETRAIT", "VIREMENT"].map((type) => (
                                <ToggleButton
                                    key={type}
                                    value={type}
                                    sx={{
                                        gap: 1,
                                        px: { xs: 1, sm: 2 },
                                        fontSize: { xs: "0.8125rem", sm: "0.875rem" },
                                        "& svg": { display: { xs: "none", sm: "inline-block" } }
                                    }}
                                >
                                    {TYPE_ICONS[type]}
                                    {getTransactionTypeLabel(type)}
                                </ToggleButton>
                            ))}
                        </ToggleButtonGroup>

                        <div key={form.typeTransaction} className="form-grid swap-in">
                            {form.typeTransaction !== "DEPOT" && (
                                <TextField
                                    select
                                    fullWidth
                                    required
                                    label={
                                        form.typeTransaction === "VIREMENT"
                                            ? "Source account"
                                            : "Account to debit"
                                    }
                                    value={form.sourceId}
                                    onChange={(e) => handleChange("sourceId", e.target.value)}
                                    disabled={saving}
                                    error={Boolean(fieldErrors.sourceId)}
                                    helperText={fieldErrors.sourceId}
                                >
                                    <MenuItem value="" disabled>Select account</MenuItem>
                                    {accountMenuItems(activeAccounts)}
                                </TextField>
                            )}

                            {form.typeTransaction !== "RETRAIT" && (
                                <TextField
                                    select
                                    fullWidth
                                    required
                                    label={
                                        form.typeTransaction === "VIREMENT"
                                            ? "Destination account"
                                            : "Account to credit"
                                    }
                                    value={form.destinationId}
                                    onChange={(e) => handleChange("destinationId", e.target.value)}
                                    disabled={saving}
                                    error={Boolean(fieldErrors.destinationId)}
                                    helperText={fieldErrors.destinationId}
                                >
                                    <MenuItem value="" disabled>Select account</MenuItem>
                                    {accountMenuItems(
                                        activeAccounts.filter(
                                            (account) =>
                                                form.typeTransaction !== "VIREMENT" ||
                                                String(accountId(account)) !== String(form.sourceId)
                                        )
                                    )}
                                </TextField>
                            )}

                            <TextField
                                fullWidth
                                required
                                type="number"
                                label="Amount"
                                placeholder="0.00"
                                value={form.montant}
                                onChange={(e) => handleChange("montant", e.target.value)}
                                disabled={saving}
                                error={Boolean(fieldErrors.montant)}
                                helperText={fieldErrors.montant}
                                slotProps={{
                                    htmlInput: { min: "0.01", step: "0.01", inputMode: "decimal" },
                                    input: {
                                        startAdornment: (
                                            <InputAdornment position="start">
                                                <PaymentsOutlined fontSize="small" sx={{ color: "text.disabled" }} />
                                            </InputAdornment>
                                        )
                                    }
                                }}
                            />

                            <TextField
                                className={form.typeTransaction === "VIREMENT" ? "" : "form-grid__full"}
                                fullWidth
                                label="Description"
                                placeholder="What is this transaction for? (optional)"
                                value={form.description}
                                onChange={(e) => handleChange("description", e.target.value)}
                                disabled={saving}
                                helperText={`${form.description.length}/${DESCRIPTION_LIMIT}`}
                                slotProps={{
                                    htmlInput: { maxLength: DESCRIPTION_LIMIT },
                                    formHelperText: { sx: { textAlign: "right" } }
                                }}
                            />
                        </div>

                        <div className="flow-preview" style={{ marginTop: 20 }} aria-live="polite">
                            {form.typeTransaction !== "DEPOT" && (
                                <span className="flow-preview__node">
                                    <small>From</small>
                                    <strong>{sourceAccount ? accountOptionLabel(sourceAccount) : "Select an account"}</strong>
                                </span>
                            )}

                            {form.typeTransaction === "VIREMENT" && (
                                <ArrowForwardRounded className="flow-preview__arrow" aria-hidden="true" />
                            )}

                            {form.typeTransaction !== "RETRAIT" && (
                                <span className="flow-preview__node">
                                    <small>To</small>
                                    <strong>{destinationAccount ? accountOptionLabel(destinationAccount) : "Select an account"}</strong>
                                </span>
                            )}

                            <span className="flow-preview__amount">
                                {Number.isFinite(previewAmount) && previewAmount > 0
                                    ? formatMoney(previewAmount)
                                    : "0.00"}
                            </span>

                            <span style={{ flexBasis: "100%", fontSize: 12.5 }}>
                                {TYPE_HELP[form.typeTransaction]} Please verify the details before submitting.
                            </span>
                        </div>

                        <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20, flexWrap: "wrap" }}>
                            <Button
                                type="button"
                                variant="outlined"
                                color="inherit"
                                disabled={saving}
                                onClick={() => handleTypeChange(form.typeTransaction)}
                            >
                                Reset
                            </Button>

                            <Button
                                type="submit"
                                variant="contained"
                                size="large"
                                disabled={saving || loading}
                                startIcon={
                                    saving
                                        ? <CircularProgress size={18} color="inherit" />
                                        : TYPE_ICONS[form.typeTransaction]
                                }
                            >
                                {saving
                                    ? "Processing…"
                                    : `Submit ${getTransactionTypeLabel(form.typeTransaction).toLowerCase()}`}
                            </Button>
                        </div>
                    </form>
                )}
            </SectionCard>

            {/* TRANSACTION HISTORY */}
            <SectionCard
                title={isAdmin ? "All transactions" : "Transaction history"}
                subtitle={`Showing ${filteredTransactions.length} of ${transactions.length} transaction(s)`}
                icon={<HistoryOutlined />}
                toolbar={
                    <>
                        <SearchField
                            value={search}
                            onChange={setSearch}
                            placeholder="Search ID, account, description…"
                            label="Search transactions"
                        />

                        <TextField
                            select
                            size="small"
                            label="Type"
                            value={typeFilter}
                            onChange={(e) => setTypeFilter(e.target.value)}
                            sx={{ minWidth: 160, flex: { xs: "1 1 140px", sm: "0 0 auto" } }}
                        >
                            <MenuItem value="ALL">All types</MenuItem>
                            <MenuItem value="DEPOT">Deposits</MenuItem>
                            <MenuItem value="RETRAIT">Withdrawals</MenuItem>
                            <MenuItem value="VIREMENT">Transfers</MenuItem>
                            <MenuItem value="PAIEMENT_FACTURE">Bill payments</MenuItem>
                        </TextField>

                        <TextField
                            select
                            size="small"
                            label="Status"
                            value={statusFilter}
                            onChange={(e) => setStatusFilter(e.target.value)}
                            sx={{ minWidth: 160, flex: { xs: "1 1 140px", sm: "0 0 auto" } }}
                        >
                            <MenuItem value="ALL">All statuses</MenuItem>
                            <MenuItem value="VALIDEE">Validated</MenuItem>
                            <MenuItem value="REFUSEE">Refused</MenuItem>
                        </TextField>

                        {filtersActive && (
                            <Button
                                size="small"
                                startIcon={<FilterListOffOutlined fontSize="small" />}
                                onClick={clearFilters}
                            >
                                Clear filters
                            </Button>
                        )}
                    </>
                }
                flush
            >
                <DataTable
                    ariaLabel="Transaction history"
                    columns={columns}
                    rows={filteredTransactions}
                    getRowId={getTransactionId}
                    loading={firstLoad}
                    loadingLabel="Loading transactions…"
                    resetKey={`${search}|${typeFilter}|${statusFilter}`}
                    minWidth={980}
                    mobileBreakpoint="lg"
                    empty={
                        <EmptyState
                            icon={<ReceiptLongOutlined />}
                            title={filtersActive ? "No matching transactions" : "No transactions yet"}
                            description={
                                filtersActive
                                    ? "Try changing your search or filters."
                                    : "Completed operations will appear here."
                            }
                            action={
                                filtersActive ? (
                                    <Button onClick={clearFilters}>Clear filters</Button>
                                ) : null
                            }
                        />
                    }
                />
            </SectionCard>
        </div>
    );
}
