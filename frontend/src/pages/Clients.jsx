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
    TextField,
    Tooltip
} from "@mui/material";

import PeopleAltOutlined from "@mui/icons-material/PeopleAltOutlined";
import PersonAddAlt1Outlined from "@mui/icons-material/PersonAddAlt1Outlined";
import EditOutlined from "@mui/icons-material/EditOutlined";
import DeleteOutline from "@mui/icons-material/DeleteOutlined";
import CloseOutlined from "@mui/icons-material/CloseOutlined";
import EmailOutlined from "@mui/icons-material/EmailOutlined";
import PhoneOutlined from "@mui/icons-material/PhoneOutlined";
import MarkEmailUnreadOutlined from "@mui/icons-material/MarkEmailUnreadOutlined";

import api from "../services/api";
import PageHeader from "../components/PageHeader";
import StatCard from "../components/StatCard";
import RefreshButton from "../components/RefreshButton";
import SectionCard from "../components/SectionCard";
import DataTable from "../components/DataTable";
import EmptyState from "../components/EmptyState";
import SearchField from "../components/SearchField";
import ConfirmDialog from "../components/ConfirmDialog";
import StatusChip from "../components/StatusChip";
import { useToast } from "../context/toastContext";
import { initials } from "../utils/format";

const blank = {
    nom: "",
    prenom: "",
    email: "",
    telephone: "",
    adresse: ""
};

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const getClientId = (c) =>
    c.idClient ?? c.id_client ?? c.id;

const getFullName = (client) =>
    [client.prenom, client.nom].filter(Boolean).join(" ") || "Unnamed client";

function validate(form) {
    const errors = {};

    if (!form.prenom.trim()) errors.prenom = "First name is required.";
    if (!form.nom.trim()) errors.nom = "Last name is required.";

    if (!form.email.trim()) {
        errors.email = "Email address is required.";
    } else if (!EMAIL_PATTERN.test(form.email.trim())) {
        errors.email = "Enter a valid email address.";
    }

    return errors;
}

export default function Clients() {
    const notify = useToast();

    const [clients, setClients] = useState([]);
    const [form, setForm] = useState(blank);
    const [formErrors, setFormErrors] = useState({});
    const [editingId, setEditingId] = useState(null);

    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [deleting, setDeleting] = useState(false);

    const [error, setError] = useState("");
    const [dialogError, setDialogError] = useState("");
    const [search, setSearch] = useState("");
    const [dialogOpen, setDialogOpen] = useState(false);
    const [clientToDelete, setClientToDelete] = useState(null);
    const [clientToActivate, setClientToActivate] = useState(null);
    const [sendingActivation, setSendingActivation] = useState(false);
    const [refreshKey, setRefreshKey] = useState(0);

    /* ---------------- LOAD ---------------- */

    useEffect(() => {
        let active = true;

        async function load() {
            setLoading(true);
            setError("");

            try {
                const response = await api.get("/clients");

                if (active) {
                    setClients(Array.isArray(response.data) ? response.data : []);
                }
            } catch (err) {
                if (active) {
                    setError(err.response?.data?.message || "Could not load clients.");
                }
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

    // Skeletons only before the first data arrives (no replay on refresh).
    const firstLoad = loading && clients.length === 0;

    const filteredClients = useMemo(() => {
        const term = search.trim().toLowerCase();

        if (!term) return clients;

        return clients.filter((client) => {
            const values = [
                getClientId(client),
                client.nom,
                client.prenom,
                client.email,
                client.telephone,
                client.adresse
            ];

            return values.some((value) =>
                String(value ?? "").toLowerCase().includes(term)
            );
        });
    }, [clients, search]);

    /* ---------------- FORM ---------------- */

    function change(event) {
        const { name, value } = event.target;

        setForm((previous) => ({ ...previous, [name]: value }));

        if (formErrors[name]) {
            setFormErrors((previous) => ({ ...previous, [name]: "" }));
        }
    }

    function openCreate() {
        setEditingId(null);
        setForm({ ...blank });
        setFormErrors({});
        setDialogError("");
        setDialogOpen(true);
    }

    function openEdit(client) {
        setEditingId(getClientId(client));

        setForm({
            nom: client.nom ?? "",
            prenom: client.prenom ?? "",
            email: client.email ?? "",
            telephone: client.telephone ?? "",
            adresse: client.adresse ?? ""
        });

        setFormErrors({});
        setDialogError("");
        setDialogOpen(true);
    }

    function closeDialog() {
        if (saving) return;

        setDialogOpen(false);
    }

    async function submit(event) {
        event.preventDefault();

        const errors = validate(form);

        if (Object.keys(errors).length) {
            setFormErrors(errors);
            return;
        }

        setSaving(true);
        setDialogError("");

        try {
            if (editingId !== null) {
                await api.put(`/clients/${editingId}`, form);
                notify("Client updated successfully.");
            } else {
                // Registers the client with a pending online banking login and
                // e-mails the activation link (no password is ever sent).
                const { data } = await api.post("/admin/clients", form);
                notify(`Client registered. Activation email sent to ${data.email} (username: ${data.username}).`);
            }

            setDialogOpen(false);
            setEditingId(null);
            setForm({ ...blank });

            reload();
        } catch (err) {
            setDialogError(
                err.response?.data?.message ||
                "Could not save client information."
            );
        } finally {
            setSaving(false);
        }
    }

    /* ---------------- DELETE ---------------- */

    async function confirmDelete() {
        if (!clientToDelete) return;

        const id = getClientId(clientToDelete);

        setDeleting(true);
        setError("");

        try {
            await api.delete(`/clients/${id}`);
            notify("Client deleted successfully.");
            setClientToDelete(null);
            reload();
        } catch (err) {
            setClientToDelete(null);
            setError(
                err.response?.data?.message ||
                "Could not delete client."
            );
        } finally {
            setDeleting(false);
        }
    }

    /* ---------------- ACTIVATION E-MAIL ---------------- */

    async function confirmSendActivation() {
        if (!clientToActivate) return;

        setSendingActivation(true);
        setError("");

        try {
            const { data } = await api.post(`/admin/clients/${getClientId(clientToActivate)}/activation-email`);
            notify(`Activation email sent to ${data.email} (username: ${data.username}).`);
            setClientToActivate(null);
            reload();
        } catch (err) {
            setClientToActivate(null);
            setError(err.response?.data?.message || "Could not send the activation email.");
        } finally {
            setSendingActivation(false);
        }
    }

    const canSendActivation = (client) =>
        Boolean(client.email) && (!client.accountStatus || client.accountStatus === "PENDING_ACTIVATION");

    /* ---------------- TABLE ---------------- */

    const columns = [
        {
            key: "client",
            header: "Client",
            mobile: "primary",
            render: (client) => (
                <div className="identity">
                    <span className="identity__avatar" aria-hidden="true">
                        {initials(client.prenom, client.nom)}
                    </span>
                    <div className="identity__text">
                        <strong>{getFullName(client)}</strong>
                        <span>Client #{getClientId(client) ?? "—"}</span>
                    </div>
                </div>
            )
        },
        {
            key: "email",
            header: "Email",
            render: (client) => client.email || <span className="cell-muted">—</span>
        },
        {
            key: "phone",
            header: "Phone",
            nowrap: true,
            render: (client) => client.telephone || <span className="cell-muted">—</span>
        },
        {
            key: "address",
            header: "Address",
            render: (client) => (
                <span className="cell-truncate cell-muted" title={client.adresse || ""}>
                    {client.adresse || "—"}
                </span>
            )
        },
        {
            key: "access",
            header: "Online access",
            render: (client) =>
                client.accountStatus ? (
                    <div className="identity__text">
                        <StatusChip value={client.accountStatus} />
                        <span className="cell-muted">{client.username}</span>
                    </div>
                ) : (
                    <StatusChip value="NONE" label="No login" tone="neutral" />
                )
        },
        {
            key: "actions",
            header: <span className="sr-only">Actions</span>,
            align: "right",
            mobile: "actions",
            width: 140,
            render: (client) => (
                <div className="row-actions" style={{ justifyContent: "flex-end" }}>
                    {canSendActivation(client) && (
                        <Tooltip
                            title={client.accountStatus ? "Resend activation email" : "Send activation email"}
                            arrow
                        >
                            <IconButton
                                size="small"
                                color="primary"
                                onClick={() => setClientToActivate(client)}
                                aria-label={`Send activation email to ${getFullName(client)}`}
                            >
                                <MarkEmailUnreadOutlined fontSize="small" />
                            </IconButton>
                        </Tooltip>
                    )}

                    <Tooltip title="Edit client" arrow>
                        <IconButton
                            size="small"
                            onClick={() => openEdit(client)}
                            aria-label={`Edit ${getFullName(client)}`}
                        >
                            <EditOutlined fontSize="small" />
                        </IconButton>
                    </Tooltip>

                    <Tooltip title="Delete client" arrow>
                        <IconButton
                            size="small"
                            color="error"
                            onClick={() => setClientToDelete(client)}
                            aria-label={`Delete ${getFullName(client)}`}
                        >
                            <DeleteOutline fontSize="small" />
                        </IconButton>
                    </Tooltip>
                </div>
            )
        }
    ];

    const withEmail = clients.filter((client) => client.email).length;
    const withPhone = clients.filter((client) => client.telephone).length;

    return (
        <div className="page">
            <PageHeader
                title="Clients"
                description="Register customers, manage their contact information and their online banking access."
                actions={
                    <>
                        <RefreshButton onClick={reload} loading={loading} />

                        <Button
                            variant="contained"
                            startIcon={<PersonAddAlt1Outlined />}
                            onClick={openCreate}
                        >
                            Add client
                        </Button>
                    </>
                }
            />

            {error && (
                <Alert severity="error" onClose={() => setError("")}>
                    {error}
                </Alert>
            )}

            <div className="stat-grid">
                <StatCard
                    label="Total clients"
                    value={clients.length}
                    hint="Registered customers"
                    icon={<PeopleAltOutlined />}
                    tone="primary"
                    loading={firstLoad}
                />
                <StatCard
                    label="With email"
                    value={withEmail}
                    hint="Reachable by email"
                    icon={<EmailOutlined />}
                    tone="success"
                    loading={firstLoad}
                />
                <StatCard
                    label="With phone number"
                    value={withPhone}
                    hint="Reachable by phone"
                    icon={<PhoneOutlined />}
                    tone="info"
                    loading={firstLoad}
                />
            </div>

            <SectionCard
                title="Registered clients"
                subtitle={`${filteredClients.length} of ${clients.length} client(s) displayed`}
                icon={<PeopleAltOutlined />}
                actions={
                    <SearchField
                        value={search}
                        onChange={setSearch}
                        placeholder="Search name, email, phone…"
                        label="Search clients"
                    />
                }
                flush
            >
                <DataTable
                    ariaLabel="Registered clients"
                    columns={columns}
                    rows={filteredClients}
                    getRowId={getClientId}
                    loading={firstLoad}
                    loadingLabel="Loading clients…"
                    resetKey={search}
                    minWidth={940}
                    empty={
                        <EmptyState
                            icon={<PeopleAltOutlined />}
                            title={search ? "No matching clients" : "No clients yet"}
                            description={
                                search
                                    ? "Try another name, email or phone number."
                                    : "Add your first client to get started."
                            }
                            action={
                                search ? (
                                    <Button onClick={() => setSearch("")}>Clear search</Button>
                                ) : (
                                    <Button
                                        variant="contained"
                                        startIcon={<PersonAddAlt1Outlined />}
                                        onClick={openCreate}
                                    >
                                        Add client
                                    </Button>
                                )
                            }
                        />
                    }
                />
            </SectionCard>

            {/* ADD / EDIT DIALOG */}
            <Dialog
                open={dialogOpen}
                onClose={closeDialog}
                fullWidth
                maxWidth="sm"
                aria-labelledby="client-dialog-title"
            >
                <DialogTitle id="client-dialog-title" component="div" className="dialog-header">
                    <div>
                        {editingId !== null ? "Edit client" : "Add new client"}
                        <p className="dialog-header__subtitle">
                            {editingId !== null
                                ? "Fields marked with * are required."
                                : "Fields marked with * are required. An activation link will be emailed to the client."}
                        </p>
                    </div>

                    <IconButton
                        onClick={closeDialog}
                        disabled={saving}
                        aria-label="Close dialog"
                        size="small"
                    >
                        <CloseOutlined fontSize="small" />
                    </IconButton>
                </DialogTitle>

                <form onSubmit={submit} noValidate>
                    <DialogContent dividers>
                        {dialogError && (
                            <Alert severity="error" sx={{ mb: 2 }}>
                                {dialogError}
                            </Alert>
                        )}

                        <div className="form-grid">
                            <TextField
                                label="First name"
                                name="prenom"
                                value={form.prenom}
                                onChange={change}
                                placeholder="e.g. Sarah"
                                required
                                fullWidth
                                autoFocus
                                disabled={saving}
                                error={Boolean(formErrors.prenom)}
                                helperText={formErrors.prenom}
                            />

                            <TextField
                                label="Last name"
                                name="nom"
                                value={form.nom}
                                onChange={change}
                                placeholder="e.g. Martin"
                                required
                                fullWidth
                                disabled={saving}
                                error={Boolean(formErrors.nom)}
                                helperText={formErrors.nom}
                            />

                            <TextField
                                className="form-grid__full"
                                label="Email address"
                                name="email"
                                type="email"
                                value={form.email}
                                onChange={change}
                                placeholder="name@example.com"
                                required
                                fullWidth
                                disabled={saving}
                                error={Boolean(formErrors.email)}
                                helperText={formErrors.email}
                            />

                            <TextField
                                className="form-grid__full"
                                label="Phone number"
                                name="telephone"
                                type="tel"
                                value={form.telephone}
                                onChange={change}
                                placeholder="Optional"
                                fullWidth
                                disabled={saving}
                            />

                            <TextField
                                className="form-grid__full"
                                label="Address"
                                name="adresse"
                                value={form.adresse}
                                onChange={change}
                                placeholder="Street, city, postal code (optional)"
                                fullWidth
                                multiline
                                minRows={2}
                                disabled={saving}
                            />
                        </div>
                    </DialogContent>

                    <DialogActions>
                        <Button onClick={closeDialog} color="inherit" variant="outlined" disabled={saving}>
                            Cancel
                        </Button>

                        <Button
                            type="submit"
                            variant="contained"
                            disabled={saving}
                            startIcon={saving ? <CircularProgress size={16} color="inherit" /> : null}
                        >
                            {saving
                                ? "Saving…"
                                : editingId !== null
                                    ? "Save changes"
                                    : "Add client"}
                        </Button>
                    </DialogActions>
                </form>
            </Dialog>

            {/* ACTIVATION E-MAIL CONFIRMATION */}
            <ConfirmDialog
                open={Boolean(clientToActivate)}
                tone="primary"
                icon={<MarkEmailUnreadOutlined fontSize="small" />}
                title={clientToActivate?.accountStatus ? "Resend the activation email?" : "Give this client online access?"}
                description={
                    clientToActivate?.accountStatus
                        ? "A new activation link is emailed to the client. Previous links stop working."
                        : "An online banking login is created and an activation link is emailed to the client, who will choose their own password."
                }
                confirmLabel="Send activation email"
                loadingLabel="Sending…"
                loading={sendingActivation}
                onConfirm={confirmSendActivation}
                onClose={() => setClientToActivate(null)}
            >
                {clientToActivate && (
                    <dl className="detail-list">
                        <div>
                            <dt>Client</dt>
                            <dd>{getFullName(clientToActivate)}</dd>
                        </div>
                        <div>
                            <dt>Email</dt>
                            <dd>{clientToActivate.email}</dd>
                        </div>
                        {clientToActivate.username && (
                            <div>
                                <dt>Username</dt>
                                <dd>{clientToActivate.username}</dd>
                            </div>
                        )}
                    </dl>
                )}
            </ConfirmDialog>

            {/* DELETE CONFIRMATION */}
            <ConfirmDialog
                open={Boolean(clientToDelete)}
                tone="error"
                icon={<DeleteOutline fontSize="small" />}
                title="Delete this client?"
                description="This action cannot be undone. Deletion is refused if the client has bank accounts, bills or an activated online login (a login still pending activation is removed with the client)."
                confirmLabel="Delete client"
                loadingLabel="Deleting…"
                loading={deleting}
                onConfirm={confirmDelete}
                onClose={() => setClientToDelete(null)}
            >
                {clientToDelete && (
                    <dl className="detail-list">
                        <div>
                            <dt>Client</dt>
                            <dd>{getFullName(clientToDelete)}</dd>
                        </div>
                        <div>
                            <dt>Client ID</dt>
                            <dd>#{getClientId(clientToDelete)}</dd>
                        </div>
                        {clientToDelete.email && (
                            <div>
                                <dt>Email</dt>
                                <dd>{clientToDelete.email}</dd>
                            </div>
                        )}
                    </dl>
                )}
            </ConfirmDialog>
        </div>
    );
}
