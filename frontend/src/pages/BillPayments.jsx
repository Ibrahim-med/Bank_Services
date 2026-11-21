import { useEffect, useMemo, useState } from "react";
import { Alert, Button, IconButton, Tooltip } from "@mui/material";

import ReceiptOutlined from "@mui/icons-material/ReceiptOutlined";
import PaymentsOutlined from "@mui/icons-material/PaymentsOutlined";
import DownloadOutlined from "@mui/icons-material/DownloadOutlined";

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
import { describeBillCategory, formatDateTime, formatMoney } from "../utils/format";
import { downloadReceipt } from "../utils/receipt";

/* Client: history of bill payments with receipts. */
export default function BillPayments() {
    const [payments, setPayments] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [refreshKey, setRefreshKey] = useState(0);
    const [search, setSearch] = useState("");

    const [receipt, setReceipt] = useState(null);
    const [receiptOpen, setReceiptOpen] = useState(false);
    const [receiptLoading, setReceiptLoading] = useState(false);
    const [receiptError, setReceiptError] = useState("");

    useEffect(() => {
        let active = true;

        async function load() {
            setLoading(true);
            setError("");

            try {
                const { data } = await api.get("/client/bill-payments");
                if (active) setPayments(Array.isArray(data) ? data : []);
            } catch (err) {
                if (active) setError(err.response?.data?.message || "Could not load your payment history.");
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
    const firstLoad = loading && payments.length === 0;

    const total = useMemo(
        () => payments.reduce((sum, payment) => sum + Number(payment.amount || 0), 0),
        [payments]
    );

    const filtered = useMemo(() => {
        const term = search.trim().toLowerCase();

        if (!term) return payments;

        return payments.filter((payment) =>
            [payment.paymentReference, payment.billReference, payment.provider]
                .some((value) => String(value ?? "").toLowerCase().includes(term)));
    }, [payments, search]);

    async function openReceipt(payment) {
        setReceipt(null);
        setReceiptError("");
        setReceiptOpen(true);
        setReceiptLoading(true);

        try {
            const { data } = await api.get(`/client/bill-payments/${payment.idBillPayment}/receipt`);
            setReceipt(data);
        } catch (err) {
            setReceiptError(err.response?.data?.message || "Could not load the receipt.");
        } finally {
            setReceiptLoading(false);
        }
    }

    const columns = [
        {
            key: "payment",
            header: "Payment",
            mobile: "primary",
            render: (payment) => (
                <div className="identity">
                    <span className={`identity__avatar tone-${describeBillCategory(payment.category).tone}`} aria-hidden="true">
                        <ReceiptOutlined fontSize="small" />
                    </span>
                    <div className="identity__text">
                        <strong>{payment.provider}</strong>
                        <span>{payment.paymentReference}</span>
                    </div>
                </div>
            )
        },
        {
            key: "amount",
            header: "Amount",
            align: "right",
            mobile: "aside",
            render: (payment) => <span className="cell-amount cell-amount--out">{formatMoney(payment.amount)}</span>
        },
        { key: "bill", header: "Bill reference", render: (payment) => <span className="cell-strong">{payment.billReference}</span> },
        { key: "account", header: "Account", nowrap: true, render: (payment) => <span className="cell-muted">{payment.accountNumberMasked}</span> },
        { key: "date", header: "Paid on", nowrap: true, render: (payment) => <span className="cell-muted">{formatDateTime(payment.paymentDate)}</span> },
        { key: "status", header: "Status", render: (payment) => <StatusChip value={payment.status} /> },
        {
            key: "actions",
            header: <span className="sr-only">Actions</span>,
            align: "right",
            mobile: "actions",
            width: 104,
            render: (payment) => (
                <div className="row-actions" style={{ justifyContent: "flex-end" }}>
                    <Tooltip title="View receipt" arrow>
                        <IconButton size="small" onClick={() => openReceipt(payment)} aria-label={`View receipt ${payment.paymentReference}`}>
                            <ReceiptOutlined fontSize="small" />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="Download receipt" arrow>
                        <IconButton size="small" onClick={() => downloadReceipt(payment)} aria-label={`Download receipt ${payment.paymentReference}`}>
                            <DownloadOutlined fontSize="small" />
                        </IconButton>
                    </Tooltip>
                </div>
            )
        }
    ];

    return (
        <div className="page">
            <PageHeader
                title="Payment history"
                description="All bills you paid online, with their receipts."
                actions={<RefreshButton onClick={reload} loading={loading} />}
            />

            {error && <Alert severity="error" onClose={() => setError("")}>{error}</Alert>}

            <div className="stat-grid">
                <StatCard label="Total paid" value={total} format={formatMoney} hint="All bill payments"
                    icon={<PaymentsOutlined />} tone="primary" variant="featured" loading={firstLoad} />
                <StatCard label="Payments" value={payments.length} hint="Receipts available"
                    icon={<ReceiptOutlined />} tone="success" loading={firstLoad} />
            </div>

            <SectionCard
                title="Bill payments"
                subtitle={`${filtered.length} of ${payments.length} payment(s) displayed`}
                icon={<ReceiptOutlined />}
                actions={<SearchField value={search} onChange={setSearch}
                    placeholder="Search payment or bill reference…" label="Search payments" />}
                flush
            >
                <DataTable
                    ariaLabel="Bill payments"
                    columns={columns}
                    rows={filtered}
                    getRowId={(payment) => payment.idBillPayment}
                    loading={firstLoad}
                    loadingLabel="Loading payments…"
                    resetKey={search}
                    minWidth={860}
                    empty={
                        <EmptyState
                            icon={<ReceiptOutlined />}
                            title={search ? "No matching payments" : "No payments yet"}
                            description={search ? "Try another reference." : "Bills you pay will appear here with their receipt."}
                            action={search ? <Button onClick={() => setSearch("")}>Clear search</Button> : null}
                        />
                    }
                />
            </SectionCard>

            <ReceiptDialog
                open={receiptOpen}
                receipt={receipt}
                loading={receiptLoading}
                error={receiptError}
                onClose={() => setReceiptOpen(false)}
            />
        </div>
    );
}
