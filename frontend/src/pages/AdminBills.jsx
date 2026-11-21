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
    MenuItem,
    TextField,
    Tooltip
} from "@mui/material";

import ReceiptLongOutlined from "@mui/icons-material/ReceiptLongOutlined";
import PostAddOutlined from "@mui/icons-material/PostAddOutlined";
import PaymentsOutlined from "@mui/icons-material/PaymentsOutlined";
import TaskAltOutlined from "@mui/icons-material/TaskAltOutlined";
import BlockOutlined from "@mui/icons-material/BlockOutlined";
import CloseOutlined from "@mui/icons-material/CloseOutlined";

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
import {
    BILL_CATEGORIES,
    billDisplayStatus,
    describeBillCategory,
    formatDate,
    formatMoney
} from "../utils/format";
import { validateBill } from "../utils/validation";

const today = () => new Date().toISOString().slice(0, 10);

const blank = () => ({
    idClient: "",
    reference: "",
    provider: "",
    category: "ELECTRICITY",
    amount: "",
    issueDate: today(),
    dueDate: ""
});

/* Administrator: issue bills to clients, follow and cancel them. */
export default function AdminBills() {
    const notify = useToast();

    const [bills, setBills] = useState([]);
    const [clients, setClients] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [refreshKey, setRefreshKey] = useState(0);

    const [search, setSearch] = useState("");
    const [statusFilter, setStatusFilter] = useState("ALL");

    const [dialogOpen, setDialogOpen] = useState(false);
    const [form, setForm] = useState(blank);
    const [formErrors, setFormErrors] = useState({});
    const [dialogError, setDialogError] = useState("");
    const [saving, setSaving] = useState(false);

    const [billToCancel, setBillToCancel] = useState(null);
    const [cancelling, setCancelling] = useState(false);

    useEffect(() => {
        let active = true;

        async function load() {
            setLoading(true);
            setError("");

            try {
                const [billResponse, clientResponse] = await Promise.all([
                    api.get("/admin/bills"),
                    api.get("/clients")
                ]);

                if (active) {
                    setBills(Array.isArray(billResponse.data) ? billResponse.data : []);
                    setClients(Array.isArray(clientResponse.data) ? clientResponse.data : []);
                }
            } catch (err) {
                if (active) setError(err.response?.data?.message || "Could not load bills.");
            } finally {
                if (active) setLoading(false);
            }
        }

        load();

        return () => {
            active = false;
        };
    }, [refreshKey]);

    const reload = () => setRefreshKey((value) => value + 1);
    const firstLoad = loading && bills.length === 0;

    const summary = useMemo(() => {
        const unpaid = bills.filter((bill) => bill.status === "UNPAID");

        return {
            total: bills.length,
            outstanding: unpaid.reduce((sum, bill) => sum + Number(bill.amount || 0), 0),
            unpaid: unpaid.length,
            paid: bills.filter((bill) => bill.status === "PAID").length
        };
    }, [bills]);

    const filteredBills = useMemo(() => {
        const term = search.trim().toLowerCase();

        return bills.filter((bill) => {
            const matchesSearch = !term || [bill.reference, bill.provider, bill.clientName]
                .some((value) => String(value ?? "").toLowerCase().includes(term));

            const status = billDisplayStatus(bill);
            const matchesStatus = statusFilter === "ALL" || status === statusFilter
                || (statusFilter === "UNPAID" && bill.status === "UNPAID");

            return matchesSearch && matchesStatus;
        });
    }, [bills, search, statusFilter]);

    /* ---------------- ISSUE ---------------- */

    function change(event) {
        const { name, value } = event.target;

        setForm((previous) => ({ ...previous, [name]: value }));

        if (formErrors[name]) setFormErrors((previous) => ({ ...previous, [name]: "" }));
    }

    function openCreate() {
        setForm(blank());
        setFormErrors({});
        setDialogError("");
        setDialogOpen(true);
    }

    async function submit(event) {
        event.preventDefault();

        const errors = validateBill(form);

        if (Object.keys(errors).length) {
            setFormErrors(errors);
            return;
        }

        setSaving(true);
        setDialogError("");

        try {
            const { data } = await api.post("/admin/bills", {
                idClient: Number(form.idClient),
                reference: form.reference.trim(),
                provider: form.provider.trim(),
                category: form.category,
                amount: String(form.amount).trim(),
                issueDate: form.issueDate,
                dueDate: form.dueDate
            });

            notify(`Bill ${data.reference} issued to ${data.clientName}.`);
            setDialogOpen(false);
            reload();
        } catch (err) {
            setDialogError(err.response?.data?.message || "Could not issue the bill.");
        } finally {
            setSaving(false);
        }
    }

    /* ---------------- CANCEL ---------------- */

    async function confirmCancel() {
        if (!billToCancel) return;

        setCancelling(true);

        try {
            await api.patch(`/admin/bills/${billToCancel.idBill}/cancel`);
            notify(`Bill ${billToCancel.reference} cancelled.`);
            setBillToCancel(null);
            reload();
        } catch (err) {
            setBillToCancel(null);
            setError(err.response?.data?.message || "Could not cancel the bill.");
            reload();
        } finally {
            setCancelling(false);
        }
    }

    /* ---------------- TABLE ---------------- */

    const columns = [
        {
            key: "bill",
            header: "Bill",
            mobile: "primary",
            render: (bill) => (
                <div className="identity">
                    <span className={`identity__avatar tone-${describeBillCategory(bill.category).tone}`} aria-hidden="true">
                        <ReceiptLongOutlined fontSize="small" />
                    </span>
                    <div className="identity__text">
                        <strong>{bill.provider}</strong>
                        <span>{bill.reference}</span>
                    </div>
                </div>
            )
        },
        {
            key: "amount",
            header: "Amount",
            align: "right",
            mobile: "aside",
            render: (bill) => <span className="cell-amount">{formatMoney(bill.amount)}</span>
        },
        {
            key: "client",
            header: "Client",
            render: (bill) => <span className="cell-strong">{bill.clientName} <span className="cell-muted">#{bill.idClient}</span></span>
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
            width: 64,
            render: (bill) =>
                bill.status === "UNPAID" ? (
                    <Tooltip title="Cancel bill" arrow>
                        <IconButton size="small" color="error" onClick={() => setBillToCancel(bill)} aria-label={`Cancel bill ${bill.reference}`}>
                            <BlockOutlined fontSize="small" />
                        </IconButton>
                    </Tooltip>
                ) : null
        }
    ];

    const clientLabel = (client) => `${client.prenom} ${client.nom} (#${client.idClient})`;

    return (
        <div className="page">
            <PageHeader
                title="Bills"
                description="Issue utility and service bills to clients and follow their payment."
                actions={
                    <>
                        <RefreshButton onClick={reload} loading={loading} />
                        <Button variant="contained" startIcon={<PostAddOutlined />} onClick={openCreate}>
                            Issue bill
                        </Button>
                    </>
                }
            />

            {error && <Alert severity="error" onClose={() => setError("")}>{error}</Alert>}

            <div className="stat-grid">
                <StatCard label="Outstanding" value={summary.outstanding} format={formatMoney}
                    hint={`${summary.unpaid} unpaid bill(s)`} icon={<PaymentsOutlined />} tone="warning"
                    variant="featured" loading={firstLoad} />
                <StatCard label="Bills issued" value={summary.total} hint="All statuses"
                    icon={<ReceiptLongOutlined />} tone="primary" loading={firstLoad} />
                <StatCard label="Paid" value={summary.paid} hint="Settled by clients"
                    icon={<TaskAltOutlined />} tone="success" loading={firstLoad} />
            </div>

            <SectionCard
                title="All bills"
                subtitle={`${filteredBills.length} of ${bills.length} bill(s) displayed`}
                icon={<ReceiptLongOutlined />}
                toolbar={
                    <>
                        <SearchField value={search} onChange={setSearch}
                            placeholder="Search reference, provider, client…" label="Search bills" />
                        <TextField select size="small" label="Status" value={statusFilter}
                            onChange={(event) => setStatusFilter(event.target.value)}
                            sx={{ minWidth: 160, flex: { xs: "1 1 140px", sm: "0 0 auto" } }}>
                            <MenuItem value="ALL">All statuses</MenuItem>
                            <MenuItem value="UNPAID">Unpaid</MenuItem>
                            <MenuItem value="OVERDUE">Overdue</MenuItem>
                            <MenuItem value="PAID">Paid</MenuItem>
                            <MenuItem value="CANCELLED">Cancelled</MenuItem>
                        </TextField>
                    </>
                }
                flush
            >
                <DataTable
                    ariaLabel="All bills"
                    columns={columns}
                    rows={filteredBills}
                    getRowId={(bill) => bill.idBill}
                    loading={firstLoad}
                    loadingLabel="Loading bills…"
                    resetKey={`${search}|${statusFilter}`}
                    minWidth={900}
                    empty={
                        <EmptyState
                            icon={<ReceiptLongOutlined />}
                            title={search || statusFilter !== "ALL" ? "No matching bills" : "No bills issued yet"}
                            description="Bills you issue to clients appear here."
                            action={<Button variant="contained" startIcon={<PostAddOutlined />} onClick={openCreate}>Issue bill</Button>}
                        />
                    }
                />
            </SectionCard>

            {/* ISSUE DIALOG */}
            <Dialog open={dialogOpen} onClose={() => !saving && setDialogOpen(false)} fullWidth maxWidth="sm" aria-labelledby="issue-bill-title">
                <DialogTitle id="issue-bill-title" component="div" className="dialog-header">
                    <div>
                        Issue a bill
                        <p className="dialog-header__subtitle">The client is notified by email and can pay it online.</p>
                    </div>
                    <IconButton onClick={() => setDialogOpen(false)} disabled={saving} aria-label="Close dialog" size="small">
                        <CloseOutlined fontSize="small" />
                    </IconButton>
                </DialogTitle>

                <form onSubmit={submit} noValidate>
                    <DialogContent dividers>
                        {dialogError && <Alert severity="error" sx={{ mb: 2 }}>{dialogError}</Alert>}

                        <div className="form-grid">
                            <TextField className="form-grid__full" select label="Client" name="idClient"
                                value={form.idClient} onChange={change} required fullWidth disabled={saving}
                                error={Boolean(formErrors.idClient)} helperText={formErrors.idClient}>
                                {clients.map((client) => (
                                    <MenuItem key={client.idClient} value={String(client.idClient)}>{clientLabel(client)}</MenuItem>
                                ))}
                            </TextField>

                            <TextField label="Bill reference" name="reference" value={form.reference} onChange={change}
                                placeholder="e.g. ELEC-2026-11-0042" required fullWidth disabled={saving}
                                error={Boolean(formErrors.reference)} helperText={formErrors.reference} />

                            <TextField label="Provider" name="provider" value={form.provider} onChange={change}
                                placeholder="e.g. City Power" required fullWidth disabled={saving}
                                error={Boolean(formErrors.provider)} helperText={formErrors.provider} />

                            <TextField select label="Category" name="category" value={form.category} onChange={change}
                                required fullWidth disabled={saving}>
                                {BILL_CATEGORIES.map((category) => (
                                    <MenuItem key={category} value={category}>{describeBillCategory(category).label}</MenuItem>
                                ))}
                            </TextField>

                            <TextField label="Amount" name="amount" type="number" value={form.amount} onChange={change}
                                required fullWidth disabled={saving}
                                slotProps={{ htmlInput: { min: "0.01", step: "0.01", inputMode: "decimal" } }}
                                error={Boolean(formErrors.amount)} helperText={formErrors.amount} />

                            <TextField label="Issue date" name="issueDate" type="date" value={form.issueDate} onChange={change}
                                required fullWidth disabled={saving} slotProps={{ inputLabel: { shrink: true } }}
                                error={Boolean(formErrors.issueDate)} helperText={formErrors.issueDate} />

                            <TextField label="Due date" name="dueDate" type="date" value={form.dueDate} onChange={change}
                                required fullWidth disabled={saving} slotProps={{ inputLabel: { shrink: true } }}
                                error={Boolean(formErrors.dueDate)} helperText={formErrors.dueDate} />
                        </div>
                    </DialogContent>

                    <DialogActions>
                        <Button onClick={() => setDialogOpen(false)} color="inherit" variant="outlined" disabled={saving}>Cancel</Button>
                        <Button type="submit" variant="contained" disabled={saving}
                            startIcon={saving ? <CircularProgress size={16} color="inherit" /> : null}>
                            {saving ? "Issuing…" : "Issue bill"}
                        </Button>
                    </DialogActions>
                </form>
            </Dialog>

            <ConfirmDialog
                open={Boolean(billToCancel)}
                tone="error"
                icon={<BlockOutlined fontSize="small" />}
                title="Cancel this bill?"
                description="The client will no longer be able to pay it. Only unpaid bills can be cancelled."
                confirmLabel="Cancel bill"
                cancelLabel="Keep bill"
                loadingLabel="Cancelling…"
                loading={cancelling}
                onConfirm={confirmCancel}
                onClose={() => setBillToCancel(null)}
            >
                {billToCancel && (
                    <dl className="detail-list">
                        <div><dt>Reference</dt><dd>{billToCancel.reference}</dd></div>
                        <div><dt>Client</dt><dd>{billToCancel.clientName}</dd></div>
                        <div><dt>Amount</dt><dd>{formatMoney(billToCancel.amount)}</dd></div>
                    </dl>
                )}
            </ConfirmDialog>
        </div>
    );
}
