import { useEffect, useMemo, useState } from "react";
import {
    Alert,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    IconButton,
    InputAdornment,
    MenuItem,
    TextField,
    ToggleButton,
    ToggleButtonGroup,
    Tooltip
} from "@mui/material";

import AccountBalanceWalletOutlined from "@mui/icons-material/AccountBalanceWalletOutlined";
import AddOutlined from "@mui/icons-material/AddOutlined";
import CloseOutlined from "@mui/icons-material/CloseOutlined";
import SavingsOutlined from "@mui/icons-material/SavingsOutlined";
import AccountBalanceOutlined from "@mui/icons-material/AccountBalanceOutlined";
import LockOutlined from "@mui/icons-material/LockOutlined";
import LockOpenOutlined from "@mui/icons-material/LockOpenOutlined";
import CreditCardOutlined from "@mui/icons-material/CreditCardOutlined";

import api from "../services/api";
import PageHeader from "../components/PageHeader";
import StatCard from "../components/StatCard";
import RefreshButton from "../components/RefreshButton";
import SectionCard from "../components/SectionCard";
import DataTable from "../components/DataTable";
import EmptyState from "../components/EmptyState";
import SearchField from "../components/SearchField";
import StatusChip from "../components/StatusChip";
import ConfirmDialog from "../components/ConfirmDialog";
import { useToast } from "../context/toastContext";
import { formatDate, formatMoney, formatNumber } from "../utils/format";

/* =========================================
   CONSTANTS AND HELPERS
========================================= */

const emptyForm = {
    numero: "",
    typeCompte: "COURANT",
    idClient: "",
    decouvertAutorise: "0",
    tauxInteret: "0"
};

const getId = (account) =>
    account?.idCompte ?? account?.id_compte ?? account?.id;

const getClientId = (account) =>
    account?.client?.idClient ??
    account?.client?.id_client ??
    account?.idClient ??
    account?.id_client;

const getAccountType = (account) =>
    account.typeCompte || account.type_compte || "UNKNOWN";

const getAccountStatus = (account) =>
    String(account.statut || account.status || "ACTIVE").toUpperCase();

const getClientName = (account) =>
    [account.client?.prenom, account.client?.nom].filter(Boolean).join(" ");

function validate(form) {
    const errors = {};

    if (!form.numero.trim()) errors.numero = "Account number is required.";
    if (!form.idClient) errors.idClient = "Select the client who owns this account.";

    if (form.typeCompte === "COURANT" && Number(form.decouvertAutorise) < 0) {
        errors.decouvertAutorise = "The overdraft cannot be negative.";
    }

    if (form.typeCompte === "EPARGNE" && Number(form.tauxInteret) < 0) {
        errors.tauxInteret = "The interest rate cannot be negative.";
    }

    return errors;
}

/* =========================================
   ACCOUNTS COMPONENT
========================================= */

export default function Accounts() {
    const notify = useToast();

    const role = localStorage.getItem("role");
    const idClient = localStorage.getItem("idClient");
    const isAdmin = role === "ADMIN";
    const missingClientId = !isAdmin && !idClient;

    const [accounts, setAccounts] = useState([]);
    const [clients, setClients] = useState([]);
    const [form, setForm] = useState(emptyForm);
    const [formErrors, setFormErrors] = useState({});

    const [loading, setLoading] = useState(!missingClientId);
    const [saving, setSaving] = useState(false);
    const [closing, setClosing] = useState(false);
    const [reopening, setReopening] = useState(false);

    const [error, setError] = useState("");
    const [dialogError, setDialogError] = useState("");
    const [search, setSearch] = useState("");
    const [statusFilter, setStatusFilter] = useState("ALL");

    const [dialogOpen, setDialogOpen] = useState(false);
    const [closeDialogOpen, setCloseDialogOpen] = useState(false);
    const [reopenDialogOpen, setReopenDialogOpen] = useState(false);

    const [selectedAccount, setSelectedAccount] = useState(null);
    const [refreshKey, setRefreshKey] = useState(0);

    /* =========================================
       LOAD ACCOUNTS AND CLIENTS
    ========================================= */

    useEffect(() => {
        if (missingClientId) return;

        let active = true;

        async function load() {
            setLoading(true);
            setError("");

            try {
                const accountResponse = isAdmin
                    ? await api.get("/comptes")
                    : await api.get(
                        `/comptes/client/${encodeURIComponent(idClient)}`
                    );

                if (active) {
                    setAccounts(
                        Array.isArray(accountResponse.data)
                            ? accountResponse.data
                            : []
                    );
                }

                if (isAdmin) {
                    const clientResponse = await api.get("/clients");

                    if (active) {
                        setClients(
                            Array.isArray(clientResponse.data)
                                ? clientResponse.data
                                : []
                        );
                    }
                }
            } catch (err) {
                if (active) {
                    setError(
                        err.response?.data?.message ||
                        "Could not load bank accounts."
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
    }, [isAdmin, idClient, missingClientId, refreshKey]);

    const reload = () => setRefreshKey((value) => value + 1);

    // Skeletons only before the first data arrives (no replay on refresh).
    const firstLoad = loading && accounts.length === 0;

    /* =========================================
       FORM HANDLING
    ========================================= */

    function change(event) {
        const { name, value } = event.target;

        setForm((previous) => ({ ...previous, [name]: value }));

        if (formErrors[name]) {
            setFormErrors((previous) => ({ ...previous, [name]: "" }));
        }
    }

    function openCreateDialog() {
        setForm({ ...emptyForm });
        setFormErrors({});
        setDialogError("");
        setDialogOpen(true);
    }

    function closeCreateDialog() {
        if (saving) return;

        setDialogOpen(false);
    }

    /* =========================================
       CREATE ACCOUNT
    ========================================= */

    async function createAccount(event) {
        event.preventDefault();

        const errors = validate(form);

        if (Object.keys(errors).length) {
            setFormErrors(errors);
            return;
        }

        setSaving(true);
        setDialogError("");

        try {
            const payload = {
                numero: form.numero.trim(),
                typeCompte: form.typeCompte,
                idClient: Number(form.idClient),
                decouvertAutorise: Number(form.decouvertAutorise),
                tauxInteret: Number(form.tauxInteret)
            };

            await api.post("/comptes", payload);

            notify("Bank account created successfully.");
            setForm({ ...emptyForm });
            setDialogOpen(false);

            reload();
        } catch (err) {
            setDialogError(
                err.response?.data?.message ||
                "Could not create the account. Check the required backend fields."
            );
        } finally {
            setSaving(false);
        }
    }

    /* =========================================
       CLOSE ACCOUNT
    ========================================= */

    function openCloseDialog(account) {
        setSelectedAccount(account);
        setCloseDialogOpen(true);
    }

    async function closeAccount() {
        if (!selectedAccount) return;

        setClosing(true);
        setError("");

        try {
            await api.patch(
                `/comptes/${getId(selectedAccount)}/close`
            );

            notify(`Account ${selectedAccount.numero} closed successfully.`);

            setCloseDialogOpen(false);
            setSelectedAccount(null);

            reload();
        } catch (err) {
            setCloseDialogOpen(false);
            setError(
                err.response?.data?.message ||
                "Could not close the account."
            );
        } finally {
            setClosing(false);
        }
    }

    /* =========================================
       REOPEN ACCOUNT
    ========================================= */

    function openReopenDialog(account) {
        setSelectedAccount(account);
        setReopenDialogOpen(true);
    }

    async function reopenAccount() {
        if (!selectedAccount) return;

        setReopening(true);
        setError("");

        try {
            await api.patch(
                `/comptes/${getId(selectedAccount)}/reopen`
            );

            notify(`Account ${selectedAccount.numero} reopened successfully.`);

            setReopenDialogOpen(false);
            setSelectedAccount(null);

            reload();
        } catch (err) {
            setReopenDialogOpen(false);
            setError(
                err.response?.data?.message ||
                "Could not reopen the account."
            );
        } finally {
            setReopening(false);
        }
    }

    /* =========================================
       SEARCH AND FILTER
    ========================================= */

    const filteredAccounts = useMemo(() => {
        const term = search.toLowerCase().trim();

        return accounts.filter((account) => {
            if (statusFilter !== "ALL" && getAccountStatus(account) !== statusFilter) {
                return false;
            }

            if (!term) return true;

            const values = [
                getId(account),
                account.numero,
                getAccountType(account),
                getClientId(account),
                getAccountStatus(account),
                getClientName(account)
            ];

            return values.some((value) =>
                String(value ?? "").toLowerCase().includes(term)
            );
        });
    }, [accounts, search, statusFilter]);

    /* =========================================
       SUMMARY
    ========================================= */

    const totalBalance = accounts.reduce(
        (total, account) => total + Number(account.solde || 0),
        0
    );

    const currentCount = accounts.filter(
        (account) => getAccountType(account) === "COURANT"
    ).length;

    const savingsCount = accounts.filter(
        (account) => getAccountType(account) === "EPARGNE"
    ).length;

    const activeCount = accounts.filter(
        (account) => getAccountStatus(account) === "ACTIVE"
    ).length;

    const closedCount = accounts.filter(
        (account) => getAccountStatus(account) === "CLOSED"
    ).length;

    /* =========================================
       TABLE COLUMNS
    ========================================= */

    const columns = [
        {
            key: "account",
            header: "Account",
            mobile: "primary",
            render: (account) => {
                const isSavings = getAccountType(account) === "EPARGNE";

                return (
                    <div className="identity">
                        <span
                            className={`identity__avatar tone-${isSavings ? "violet" : "primary"}`}
                            aria-hidden="true"
                        >
                            {isSavings ? <SavingsOutlined /> : <CreditCardOutlined />}
                        </span>
                        <div className="identity__text">
                            <strong>{account.numero || "—"}</strong>
                            <span>ID #{getId(account) ?? "—"}</span>
                        </div>
                    </div>
                );
            }
        },
        {
            key: "balance",
            header: "Balance",
            align: "right",
            mobile: "aside",
            render: (account) => (
                <span className="cell-amount">{formatMoney(account.solde)}</span>
            )
        },
        {
            key: "type",
            header: "Type",
            render: (account) => (
                <StatusChip kind="accountType" value={getAccountType(account)} />
            )
        },
        {
            key: "client",
            header: "Client",
            render: (account) => {
                const name = getClientName(account);
                const id = getClientId(account);

                return name ? (
                    <span>
                        <span className="cell-strong">{name}</span>
                        <span className="cell-muted"> · #{id}</span>
                    </span>
                ) : (
                    <span className="cell-muted">{id != null ? `Client #${id}` : "—"}</span>
                );
            }
        },
        {
            key: "created",
            header: "Opened",
            nowrap: true,
            render: (account) => (
                <span className="cell-muted">{formatDate(account.dateCreation)}</span>
            )
        },
        {
            key: "status",
            header: "Status",
            render: (account) => <StatusChip value={getAccountStatus(account)} />
        },
        ...(isAdmin
            ? [{
                key: "actions",
                header: <span className="sr-only">Actions</span>,
                align: "right",
                mobile: "actions",
                render: (account) => {
                    const isActive = getAccountStatus(account) === "ACTIVE";
                    const hasZeroBalance = Number(account.solde) === 0;

                    if (!isActive) {
                        return (
                            <Button
                                size="small"
                                color="success"
                                variant="outlined"
                                startIcon={<LockOpenOutlined fontSize="small" />}
                                onClick={() => openReopenDialog(account)}
                                sx={{ whiteSpace: "nowrap" }}
                            >
                                Reopen
                            </Button>
                        );
                    }

                    const button = (
                        <Button
                            size="small"
                            color="error"
                            variant="outlined"
                            startIcon={<LockOutlined fontSize="small" />}
                            disabled={!hasZeroBalance}
                            onClick={() => openCloseDialog(account)}
                            sx={{ whiteSpace: "nowrap" }}
                        >
                            Close
                        </Button>
                    );

                    return hasZeroBalance ? button : (
                        <Tooltip title="Only accounts with a zero balance can be closed." arrow>
                            {/* span keeps the tooltip working on a disabled button */}
                            <span style={{ display: "inline-flex" }} tabIndex={0}>
                                {button}
                            </span>
                        </Tooltip>
                    );
                }
            }]
            : [])
    ];

    const filtersActive = search || statusFilter !== "ALL";
    const pageError = missingClientId
        ? "Client ID is missing. Sign out and sign in again."
        : error;

    /* =========================================
       PAGE UI
    ========================================= */

    return (
        <div className="page">
            <PageHeader
                title="Bank accounts"
                description={
                    isAdmin
                        ? "Manage and monitor all customer bank accounts."
                        : "View and monitor your personal bank accounts."
                }
                actions={
                    <>
                        <RefreshButton onClick={reload} loading={loading} disabled={missingClientId} />

                        {isAdmin && (
                            <Button
                                variant="contained"
                                startIcon={<AddOutlined />}
                                onClick={openCreateDialog}
                            >
                                New account
                            </Button>
                        )}
                    </>
                }
            />

            {pageError && (
                <Alert severity="error" onClose={missingClientId ? undefined : () => setError("")}>
                    {pageError}
                </Alert>
            )}

            <div className="stat-grid">
                <StatCard
                    variant="featured"
                    label="Combined balance"
                    value={totalBalance}
                    format={formatMoney}
                    hint={`Across ${formatNumber(accounts.length)} account(s)`}
                    icon={<AccountBalanceOutlined />}
                    loading={firstLoad}
                />
                <StatCard
                    label="Total accounts"
                    value={accounts.length}
                    hint={`${activeCount} active · ${closedCount} closed`}
                    icon={<AccountBalanceWalletOutlined />}
                    tone="primary"
                    loading={firstLoad}
                />
                <StatCard
                    label="Current accounts"
                    value={currentCount}
                    hint="Everyday banking"
                    icon={<CreditCardOutlined />}
                    tone="success"
                    loading={firstLoad}
                />
                <StatCard
                    label="Savings accounts"
                    value={savingsCount}
                    hint="Interest-bearing"
                    icon={<SavingsOutlined />}
                    tone="violet"
                    loading={firstLoad}
                />
            </div>

            <SectionCard
                title={isAdmin ? "All bank accounts" : "My bank accounts"}
                subtitle={`${filteredAccounts.length} of ${accounts.length} account(s) displayed`}
                icon={<AccountBalanceWalletOutlined />}
                toolbar={
                    <>
                        <SearchField
                            value={search}
                            onChange={setSearch}
                            placeholder="Search number, client, ID…"
                            label="Search accounts"
                        />

                        <ToggleButtonGroup
                            size="small"
                            exclusive
                            value={statusFilter}
                            onChange={(_event, value) => value && setStatusFilter(value)}
                            aria-label="Filter by status"
                        >
                            <ToggleButton value="ALL">All ({accounts.length})</ToggleButton>
                            <ToggleButton value="ACTIVE">Active ({activeCount})</ToggleButton>
                            <ToggleButton value="CLOSED">Closed ({closedCount})</ToggleButton>
                        </ToggleButtonGroup>
                    </>
                }
                flush
            >
                <DataTable
                    ariaLabel="Bank accounts"
                    columns={columns}
                    rows={filteredAccounts}
                    getRowId={getId}
                    loading={firstLoad}
                    loadingLabel="Loading bank accounts…"
                    resetKey={`${search}|${statusFilter}`}
                    minWidth={isAdmin ? 900 : 780}
                    empty={
                        <EmptyState
                            icon={<AccountBalanceWalletOutlined />}
                            title={filtersActive ? "No matching accounts" : "No bank accounts yet"}
                            description={
                                filtersActive
                                    ? "Try another search term or status filter."
                                    : "There are no accounts to display yet."
                            }
                            action={
                                filtersActive ? (
                                    <Button
                                        onClick={() => {
                                            setSearch("");
                                            setStatusFilter("ALL");
                                        }}
                                    >
                                        Clear filters
                                    </Button>
                                ) : null
                            }
                        />
                    }
                />
            </SectionCard>

            {/* CREATE ACCOUNT DIALOG */}
            <Dialog
                open={dialogOpen}
                onClose={closeCreateDialog}
                fullWidth
                maxWidth="sm"
                aria-labelledby="account-dialog-title"
            >
                <DialogTitle id="account-dialog-title" component="div" className="dialog-header">
                    <div>
                        Create bank account
                        <p className="dialog-header__subtitle">
                            Fields marked with * are required.
                        </p>
                    </div>

                    <IconButton
                        onClick={closeCreateDialog}
                        disabled={saving}
                        aria-label="Close dialog"
                        size="small"
                    >
                        <CloseOutlined fontSize="small" />
                    </IconButton>
                </DialogTitle>

                <form onSubmit={createAccount} noValidate>
                    <DialogContent dividers>
                        {dialogError && (
                            <Alert severity="error" sx={{ mb: 2 }}>
                                {dialogError}
                            </Alert>
                        )}

                        <div className="form-grid">
                            <TextField
                                label="Account number"
                                name="numero"
                                value={form.numero}
                                onChange={change}
                                placeholder="Enter a unique account number"
                                required
                                fullWidth
                                autoFocus
                                disabled={saving}
                                error={Boolean(formErrors.numero)}
                                helperText={formErrors.numero}
                            />

                            <TextField
                                select
                                label="Account type"
                                name="typeCompte"
                                value={form.typeCompte}
                                onChange={change}
                                fullWidth
                                disabled={saving}
                            >
                                <MenuItem value="COURANT">Current</MenuItem>
                                <MenuItem value="EPARGNE">Savings</MenuItem>
                            </TextField>

                            <TextField
                                select
                                className="form-grid__full"
                                label="Client"
                                name="idClient"
                                value={form.idClient}
                                onChange={change}
                                required
                                fullWidth
                                disabled={saving}
                                error={Boolean(formErrors.idClient)}
                                helperText={
                                    formErrors.idClient ||
                                    (clients.length === 0 ? "No clients available. Create a client first." : "")
                                }
                            >
                                <MenuItem value="" disabled>
                                    Select a client
                                </MenuItem>

                                {clients.map((client) => (
                                    <MenuItem
                                        key={client.idClient}
                                        value={client.idClient}
                                    >
                                        {client.prenom} {client.nom} (#{client.idClient})
                                    </MenuItem>
                                ))}
                            </TextField>

                            {form.typeCompte === "COURANT" && (
                                <TextField
                                    className="form-grid__full"
                                    label="Authorized overdraft"
                                    type="number"
                                    name="decouvertAutorise"
                                    value={form.decouvertAutorise}
                                    onChange={change}
                                    fullWidth
                                    disabled={saving}
                                    error={Boolean(formErrors.decouvertAutorise)}
                                    helperText={
                                        formErrors.decouvertAutorise ||
                                        "Maximum amount the balance may go below zero."
                                    }
                                    slotProps={{ htmlInput: { min: 0, step: "0.01" } }}
                                />
                            )}

                            {form.typeCompte === "EPARGNE" && (
                                <TextField
                                    className="form-grid__full"
                                    label="Interest rate"
                                    type="number"
                                    name="tauxInteret"
                                    value={form.tauxInteret}
                                    onChange={change}
                                    fullWidth
                                    disabled={saving}
                                    error={Boolean(formErrors.tauxInteret)}
                                    helperText={formErrors.tauxInteret}
                                    slotProps={{
                                        htmlInput: { min: 0, step: "0.01" },
                                        input: {
                                            endAdornment: <InputAdornment position="end">%</InputAdornment>
                                        }
                                    }}
                                />
                            )}
                        </div>

                        <Alert severity="info" sx={{ mt: 2 }}>
                            New accounts start with a balance of 0.00. Deposits can be made from the Transactions page.
                        </Alert>
                    </DialogContent>

                    <DialogActions>
                        <Button
                            onClick={closeCreateDialog}
                            color="inherit"
                            variant="outlined"
                            disabled={saving}
                        >
                            Cancel
                        </Button>

                        <Button
                            type="submit"
                            variant="contained"
                            disabled={saving}
                            startIcon={
                                saving
                                    ? <CircularProgress size={16} color="inherit" />
                                    : <AddOutlined />
                            }
                        >
                            {saving ? "Creating…" : "Create account"}
                        </Button>
                    </DialogActions>
                </form>
            </Dialog>

            {/* CLOSE ACCOUNT CONFIRMATION */}
            <ConfirmDialog
                open={closeDialogOpen}
                tone="error"
                icon={<LockOutlined fontSize="small" />}
                title="Close this bank account?"
                description="The account will be marked as closed. Its transaction history is preserved and it can be reopened later."
                confirmLabel="Close account"
                loadingLabel="Closing…"
                loading={closing}
                onConfirm={closeAccount}
                onClose={() => setCloseDialogOpen(false)}
            >
                {selectedAccount && (
                    <dl className="detail-list">
                        <div>
                            <dt>Account number</dt>
                            <dd>{selectedAccount.numero}</dd>
                        </div>
                        <div>
                            <dt>Account ID</dt>
                            <dd>#{getId(selectedAccount)}</dd>
                        </div>
                        <div>
                            <dt>Balance</dt>
                            <dd>{formatMoney(selectedAccount.solde)}</dd>
                        </div>
                    </dl>
                )}
            </ConfirmDialog>

            {/* REOPEN ACCOUNT CONFIRMATION */}
            <ConfirmDialog
                open={reopenDialogOpen}
                tone="success"
                icon={<LockOpenOutlined fontSize="small" />}
                title="Reopen this bank account?"
                description="The account status changes from Closed to Active. Its balance and transaction history are preserved."
                confirmLabel="Reopen account"
                loadingLabel="Reopening…"
                loading={reopening}
                onConfirm={reopenAccount}
                onClose={() => setReopenDialogOpen(false)}
            >
                {selectedAccount && (
                    <dl className="detail-list">
                        <div>
                            <dt>Account number</dt>
                            <dd>{selectedAccount.numero}</dd>
                        </div>
                        <div>
                            <dt>Account ID</dt>
                            <dd>#{getId(selectedAccount)}</dd>
                        </div>
                        <div>
                            <dt>Balance</dt>
                            <dd>{formatMoney(selectedAccount.solde)}</dd>
                        </div>
                    </dl>
                )}
            </ConfirmDialog>
        </div>
    );
}
