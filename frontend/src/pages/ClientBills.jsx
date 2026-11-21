import { useEffect, useMemo, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import {
    Alert,
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    IconButton,
    MenuItem,
    TextField,
    Tooltip
} from "@mui/material";

import ReceiptLongOutlined from "@mui/icons-material/ReceiptLongOutlined";
import PaymentsOutlined from "@mui/icons-material/PaymentsOutlined";
import EventBusyOutlined from "@mui/icons-material/EventBusyOutlined";
import TaskAltOutlined from "@mui/icons-material/TaskAltOutlined";
import VisibilityOutlined from "@mui/icons-material/VisibilityOutlined";
import ReceiptOutlined from "@mui/icons-material/ReceiptOutlined";
import CloseOutlined from "@mui/icons-material/CloseOutlined";
import FilterListOffOutlined from "@mui/icons-material/FilterListOffOutlined";

import api from "../services/api";
import PageHeader from "../components/PageHeader";
import StatCard from "../components/StatCard";
import RefreshButton from "../components/RefreshButton";
import SectionCard from "../components/SectionCard";
import DataTable from "../components/DataTable";
import EmptyState from "../components/EmptyState";
import SearchField from "../components/SearchField";
import StatusChip from "../components/StatusChip";
import ReceiptDialog from "../components/ReceiptDialog";
import { useToast } from "../context/toastContext";
import {
    BILL_CATEGORIES,
    billDisplayStatus,
    describeAccountType,
    describeBillCategory,
    formatDate,
    formatDateTime,
    formatMoney
} from "../utils/format";

const STATUS_FILTERS = [
    { value: "ALL", label: "All bills" },
    { value: "UNPAID", label: "Unpaid" },
    { value: "OVERDUE", label: "Overdue" },
    { value: "PAID", label: "Paid" },
    { value: "CANCELLED", label: "Cancelled" }
];

const errorMessage = (err, fallback) => err.response?.data?.message || fallback;

/* Bills of the signed-in client: consult, filter, pay, view receipts. */
export default function ClientBills() {
    const notify = useToast();
    const idClient = localStorage.getItem("idClient");

    const [bills, setBills] = useState([]);
    const [accounts, setAccounts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [refreshKey, setRefreshKey] = useState(0);

    const [search, setSearch] = useState("");
    const [statusFilter, setStatusFilter] = useState("ALL");
    const [categoryFilter, setCategoryFilter] = useState("ALL");

    // Details
    const [details, setDetails] = useState(null);
    const [detailsLoading, setDetailsLoading] = useState(false);
    const [detailsError, setDetailsError] = useState("");

    // Payment
    const [billToPay, setBillToPay] = useState(null);
    const [selectedAccount, setSelectedAccount] = useState("");
    const [accountError, setAccountError] = useState("");
    const [paying, setPaying] = useState(false);
    const [payError, setPayError] = useState("");

    // Receipt
    const [receipt, setReceipt] = useState(null);
    const [receiptOpen, setReceiptOpen] = useState(false);
    const [receiptLoading, setReceiptLoading] = useState(false);
    const [receiptError, setReceiptError] = useState("");
    const [justPaid, setJustPaid] = useState(false);

    /* ---------------- LOAD ---------------- */

    useEffect(() => {
        let active = true;

        async function load() {
            setLoading(true);
            setError("");

            try {
                if (!idClient) {
                    throw new Error("Client ID is missing. Please sign in again.");
                }

                const [billResponse, accountResponse] = await Promise.all([
                    api.get("/client/bills"),
                    api.get(`/comptes/client/${encodeURIComponent(idClient)}`)
                ]);

                if (active) {
                    setBills(Array.isArray(billResponse.data) ? billResponse.data : []);
                    setAccounts(Array.isArray(accountResponse.data) ? accountResponse.data : []);
                }
            } catch (err) {
                if (active) setError(errorMessage(err, err.message || "Could not load your bills."));
            } finally {
                if (active) setLoading(false);
            }
        }

        load();

        return () => {
            active = false;
        };
    }, [idClient, refreshKey]);

    const reload = () => setRefreshKey((value) => value + 1);
    const firstLoad = loading && bills.length === 0;

    const activeAccounts = accounts.filter((account) => account.statut === "ACTIVE");

    /* ---------------- SUMMARY ---------------- */

    const summary = useMemo(() => {
        const unpaid = bills.filter((bill) => bill.status === "UNPAID");

        return {
            unpaidCount: unpaid.length,
            amountDue: unpaid.reduce((sum, bill) => sum + Number(bill.amount || 0), 0),
            overdue: unpaid.filter((bill) => bill.overdue).length,
            paid: bills.filter((bill) => bill.status === "PAID").length
        };
    }, [bills]);

    /* ---------------- FILTERS ---------------- */

    const filteredBills = useMemo(() => {
        const term = search.trim().toLowerCase();

        return bills.filter((bill) => {
            if (term && !String(bill.reference ?? "").toLowerCase().includes(term)) return false;
            if (categoryFilter !== "ALL" && bill.category !== categoryFilter) return false;
            if (statusFilter === "ALL") return true;
            if (statusFilter === "OVERDUE") return bill.status === "UNPAID" && bill.overdue;
            return bill.status === statusFilter;
        });
    }, [bills, search, statusFilter, categoryFilter]);

    const filtersActive = Boolean(search) || statusFilter !== "ALL" || categoryFilter !== "ALL";

    const clearFilters = () => {
        setSearch("");
        setStatusFilter("ALL");
        setCategoryFilter("ALL");
    };

    /* ---------------- DETAILS ---------------- */

    async function openDetails(bill) {
        setDetails(bill);
        setDetailsError("");
        setDetailsLoading(true);

        try {
            const { data } = await api.get(`/client/bills/${bill.idBill}`);
            setDetails(data);
        } catch (err) {
            setDetailsError(errorMessage(err, "Could not load the bill details."));
        } finally {
            setDetailsLoading(false);
        }
    }

    /* ---------------- PAYMENT ---------------- */

    function openPayment(bill) {
        setDetails(null);
        setBillToPay(bill);
        setPayError("");
        setAccountError("");
        setSelectedAccount(activeAccounts.length === 1 ? String(activeAccounts[0].idCompte) : "");
    }

    function closePayment() {
        if (!paying) setBillToPay(null);
    }

    const payingAccount = activeAccounts.find(
        (account) => String(account.idCompte) === String(selectedAccount)
    );

    // Indicative only: the backend re-checks funds on the locked account.
    const balanceAfter = payingAccount && billToPay
        ? Number(payingAccount.solde) - Number(billToPay.amount)
        : null;
    const availableFunds = payingAccount
        ? Number(payingAccount.solde) + Number(payingAccount.decouvertAutorise || 0)
        : null;
    const insufficient = payingAccount && billToPay
        ? Number(billToPay.amount) > availableFunds
        : false;

    async function confirmPayment() {
        if (!billToPay) return;

        if (!selectedAccount) {
            setAccountError("Please select the account to debit.");
            return;
        }

        setPaying(true);
        setPayError("");

        try {
            // Only the account is sent: the amount is always taken from the bill by the server.
            const { data } = await api.post(`/client/bills/${billToPay.idBill}/pay`, {
                idCompte: Number(selectedAccount)
            });

            setBillToPay(null);
            setReceipt(data);
            setReceiptError("");
            setJustPaid(true);
            setReceiptOpen(true);
            notify(`Bill ${data.billReference} paid successfully.`);
            reload();
        } catch (err) {
            setPayError(errorMessage(err, "The payment could not be completed. No amount was debited."));
            // The bill may have changed meanwhile (e.g. paid in another tab).
            if (err.response?.status === 409) reload();
        } finally {
            setPaying(false);
        }
    }

    /* ---------------- RECEIPT ---------------- */

    async function openReceipt(bill) {
        setDetails(null);
        setJustPaid(false);
        setReceipt(null);
        setReceiptError("");
        setReceiptOpen(true);
        setReceiptLoading(true);

        try {
            const { data } = await api.get(`/client/bill-payments/${bill.idBillPayment}/receipt`);
            setReceipt(data);
        } catch (err) {
            setReceiptError(errorMessage(err, "Could not load the receipt."));
        } finally {
            setReceiptLoading(false);
        }
    }

    /* ---------------- TABLE ---------------- */

    const columns = [
        {
            key: "bill",
            header: "Bill",
            mobile: "primary",
            render: (bill) => {
                const { tone } = describeBillCategory(bill.category);

                return (
                    <div className="identity">
                        <span className={`identity__avatar tone-${tone}`} aria-hidden="true">
                            <ReceiptLongOutlined fontSize="small" />
                        </span>
                        <div className="identity__text">
                            <strong>{bill.provider}</strong>
                            <span>{bill.reference}</span>
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
            render: (bill) => <span className="cell-amount">{formatMoney(bill.amount)}</span>
        },
        {
            key: "category",
            header: "Category",
            render: (bill) => <StatusChip kind="billCategory" value={bill.category} />
        },
        {
            key: "due",
            header: "Due date",
            nowrap: true,
            render: (bill) => <span className="cell-muted">{formatDate(bill.dueDate)}</span>
        },
        {
            key: "status",
            header: "Status",
            render: (bill) => <StatusChip value={billDisplayStatus(bill)} />
        },
        {
            key: "actions",
            header: <span className="sr-only">Actions</span>,
            align: "right",
            mobile: "actions",
            width: 170,
            render: (bill) => (
                <div className="row-actions" style={{ justifyContent: "flex-end" }}>
                    <Tooltip title="View details" arrow>
                        <IconButton size="small" onClick={() => openDetails(bill)} aria-label={`View bill ${bill.reference}`}>
                            <VisibilityOutlined fontSize="small" />
                        </IconButton>
                    </Tooltip>

                    {bill.status === "UNPAID" && (
                        <Button size="small" variant="contained" onClick={() => openPayment(bill)}>
                            Pay
                        </Button>
                    )}

                    {bill.status === "PAID" && bill.idBillPayment && (
                        <Tooltip title="View receipt" arrow>
                            <IconButton size="small" onClick={() => openReceipt(bill)} aria-label={`Receipt for bill ${bill.reference}`}>
                                <ReceiptOutlined fontSize="small" />
                            </IconButton>
                        </Tooltip>
                    )}
                </div>
            )
        }
    ];

    return (
        <div className="page">
            <PageHeader
                title="Bills"
                description="Review your electricity, water, internet and telephone bills and pay them from your accounts."
                actions={<RefreshButton onClick={reload} loading={loading} disabled={paying} />}
            />

            {error && (
                <Alert severity="error" onClose={() => setError("")}>
                    {error}
                </Alert>
            )}

            <div className="stat-grid">
                <StatCard
                    label="Amount due"
                    value={summary.amountDue}
                    format={formatMoney}
                    hint={`${summary.unpaidCount} unpaid bill(s)`}
                    icon={<PaymentsOutlined />}
                    tone="warning"
                    variant="featured"
                    loading={firstLoad}
                />
                <StatCard
                    label="Overdue"
                    value={summary.overdue}
                    hint="Unpaid after their due date"
                    icon={<EventBusyOutlined />}
                    tone="error"
                    loading={firstLoad}
                />
                <StatCard
                    label="Paid"
                    value={summary.paid}
                    hint="Bills settled"
                    icon={<TaskAltOutlined />}
                    tone="success"
                    loading={firstLoad}
                />
            </div>

            <SectionCard
                title="My bills"
                subtitle={`${filteredBills.length} of ${bills.length} bill(s) displayed`}
                icon={<ReceiptLongOutlined />}
                toolbar={
                    <>
                        <SearchField
                            value={search}
                            onChange={setSearch}
                            placeholder="Search by bill reference…"
                            label="Search bills by reference"
                        />

                        <TextField
                            select
                            size="small"
                            label="Status"
                            value={statusFilter}
                            onChange={(event) => setStatusFilter(event.target.value)}
                            sx={{ minWidth: 160, flex: { xs: "1 1 140px", sm: "0 0 auto" } }}
                        >
                            {STATUS_FILTERS.map((option) => (
                                <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                            ))}
                        </TextField>

                        <TextField
                            select
                            size="small"
                            label="Category"
                            value={categoryFilter}
                            onChange={(event) => setCategoryFilter(event.target.value)}
                            sx={{ minWidth: 170, flex: { xs: "1 1 140px", sm: "0 0 auto" } }}
                        >
                            <MenuItem value="ALL">All categories</MenuItem>
                            {BILL_CATEGORIES.map((category) => (
                                <MenuItem key={category} value={category}>
                                    {describeBillCategory(category).label}
                                </MenuItem>
                            ))}
                        </TextField>

                        {filtersActive && (
                            <Button color="inherit" startIcon={<FilterListOffOutlined />} onClick={clearFilters}>
                                Clear
                            </Button>
                        )}
                    </>
                }
                flush
            >
                <DataTable
                    ariaLabel="My bills"
                    columns={columns}
                    rows={filteredBills}
                    getRowId={(bill) => bill.idBill}
                    loading={firstLoad}
                    loadingLabel="Loading bills…"
                    resetKey={`${search}|${statusFilter}|${categoryFilter}`}
                    minWidth={820}
                    empty={
                        <EmptyState
                            icon={<ReceiptLongOutlined />}
                            title={filtersActive ? "No matching bills" : "No bills yet"}
                            description={
                                filtersActive
                                    ? "Try another reference, status or category."
                                    : "Bills issued to you by BankServices will appear here."
                            }
                            action={filtersActive ? <Button onClick={clearFilters}>Clear filters</Button> : null}
                        />
                    }
                />
            </SectionCard>

            {/* DETAILS */}
            <Dialog open={Boolean(details)} onClose={() => setDetails(null)} fullWidth maxWidth="sm" aria-labelledby="bill-details-title">
                <DialogTitle id="bill-details-title" component="div" className="dialog-header">
                    <div>
                        Bill details
                        {details && <p className="dialog-header__subtitle">{details.reference}</p>}
                    </div>
                    <IconButton onClick={() => setDetails(null)} aria-label="Close dialog" size="small">
                        <CloseOutlined fontSize="small" />
                    </IconButton>
                </DialogTitle>

                <DialogContent dividers>
                    {detailsError && <Alert severity="error" sx={{ mb: 2 }}>{detailsError}</Alert>}

                    {details && (
                        <dl className="detail-list" aria-busy={detailsLoading}>
                            <div><dt>Reference</dt><dd>{details.reference}</dd></div>
                            <div><dt>Provider</dt><dd>{details.provider}</dd></div>
                            <div><dt>Category</dt><dd>{describeBillCategory(details.category).label}</dd></div>
                            <div><dt>Amount</dt><dd>{formatMoney(details.amount)}</dd></div>
                            <div><dt>Issue date</dt><dd>{formatDate(details.issueDate)}</dd></div>
                            <div><dt>Due date</dt><dd>{formatDate(details.dueDate)}</dd></div>
                            <div><dt>Status</dt><dd><StatusChip value={billDisplayStatus(details)} /></dd></div>
                            {details.paidAt && <div><dt>Paid on</dt><dd>{formatDateTime(details.paidAt)}</dd></div>}
                            {details.paymentReference && <div><dt>Payment reference</dt><dd>{details.paymentReference}</dd></div>}
                        </dl>
                    )}
                </DialogContent>

                <DialogActions>
                    <Button color="inherit" variant="outlined" onClick={() => setDetails(null)}>Close</Button>
                    {details?.status === "UNPAID" && (
                        <Button variant="contained" onClick={() => openPayment(details)}>Pay this bill</Button>
                    )}
                    {details?.status === "PAID" && details.idBillPayment && (
                        <Button variant="contained" onClick={() => openReceipt(details)}>View receipt</Button>
                    )}
                </DialogActions>
            </Dialog>

            {/* PAYMENT */}
            <Dialog open={Boolean(billToPay)} onClose={closePayment} fullWidth maxWidth="sm" aria-labelledby="pay-bill-title">
                <DialogTitle id="pay-bill-title" component="div" className="dialog-header">
                    <div>
                        Pay bill
                        {billToPay && (
                            <p className="dialog-header__subtitle">
                                {billToPay.provider} · {billToPay.reference}
                            </p>
                        )}
                    </div>
                    <IconButton onClick={closePayment} disabled={paying} aria-label="Close dialog" size="small">
                        <CloseOutlined fontSize="small" />
                    </IconButton>
                </DialogTitle>

                <DialogContent dividers>
                    {payError && <Alert severity="error" sx={{ mb: 2 }}>{payError}</Alert>}

                    {billToPay && (
                        <>
                            {activeAccounts.length === 0 ? (
                                <Alert severity="warning">
                                    You have no active bank account to pay from.{" "}
                                    <RouterLink to="/app/accounts" className="text-link">See my accounts</RouterLink>
                                </Alert>
                            ) : (
                                <TextField
                                    select
                                    fullWidth
                                    label="Account to debit"
                                    value={selectedAccount}
                                    onChange={(event) => {
                                        setSelectedAccount(event.target.value);
                                        setAccountError("");
                                    }}
                                    disabled={paying}
                                    required
                                    error={Boolean(accountError)}
                                    helperText={accountError}
                                >
                                    {activeAccounts.map((account) => (
                                        <MenuItem key={account.idCompte} value={String(account.idCompte)}>
                                            <span style={{ display: "flex", justifyContent: "space-between", gap: 16, width: "100%" }}>
                                                <span>
                                                    {account.numero} · {describeAccountType(account.typeCompte).label}
                                                </span>
                                                <span className="cell-muted">{formatMoney(account.solde)}</span>
                                            </span>
                                        </MenuItem>
                                    ))}
                                </TextField>
                            )}

                            <dl className="detail-list">
                                <div><dt>Bill reference</dt><dd>{billToPay.reference}</dd></div>
                                <div><dt>Provider</dt><dd>{billToPay.provider}</dd></div>
                                <div><dt>Due date</dt><dd>{formatDate(billToPay.dueDate)}</dd></div>
                                <div><dt>Amount to pay</dt><dd>{formatMoney(billToPay.amount)}</dd></div>
                                {payingAccount && (
                                    <div>
                                        <dt>Balance after payment</dt>
                                        <dd>{formatMoney(balanceAfter)}</dd>
                                    </div>
                                )}
                            </dl>

                            {insufficient && (
                                <Alert severity="warning" sx={{ mt: 2 }}>
                                    This account does not have enough available funds for this bill.
                                </Alert>
                            )}
                        </>
                    )}
                </DialogContent>

                <DialogActions>
                    <Button color="inherit" variant="outlined" onClick={closePayment} disabled={paying}>
                        Cancel
                    </Button>
                    <Button
                        variant="contained"
                        onClick={confirmPayment}
                        disabled={paying || activeAccounts.length === 0 || insufficient}
                        startIcon={paying ? <CircularProgress size={16} color="inherit" /> : null}
                    >
                        {paying ? "Processing…" : `Confirm payment of ${formatMoney(billToPay?.amount)}`}
                    </Button>
                </DialogActions>
            </Dialog>

            <ReceiptDialog
                open={receiptOpen}
                receipt={receipt}
                loading={receiptLoading}
                error={receiptError}
                confirmation={justPaid}
                onClose={() => setReceiptOpen(false)}
            />
        </div>
    );
}
