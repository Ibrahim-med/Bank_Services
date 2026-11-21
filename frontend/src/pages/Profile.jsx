import { useEffect, useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import { Alert, Button, ToggleButton, ToggleButtonGroup } from "@mui/material";

import PersonOutlineRounded from "@mui/icons-material/PersonOutlineRounded";
import TuneOutlined from "@mui/icons-material/TuneOutlined";
import LightModeOutlined from "@mui/icons-material/LightModeOutlined";
import DarkModeOutlined from "@mui/icons-material/DarkModeOutlined";
import LockResetOutlined from "@mui/icons-material/LockResetOutlined";
import LogoutOutlined from "@mui/icons-material/LogoutOutlined";
import InfoOutlined from "@mui/icons-material/InfoOutlined";

import api from "../services/api";
import PageHeader from "../components/PageHeader";
import SectionCard from "../components/SectionCard";
import LoadingState from "../components/LoadingState";
import StatusChip from "../components/StatusChip";
import { useThemeMode } from "../context/themeModeContext";
import { initials } from "../utils/format";
import { getRoleLabel } from "../utils/navigation";
import { clearSession, readSession } from "../utils/session";

/*
 * Read-only profile + settings.
 * CLIENT: details come from GET /api/clients/{idClient}, which the backend
 * only serves to the owning client (or an administrator).
 * ADMIN: administrator logins are not linked to a client record, so only
 * the session identity is shown (no API call).
 * Settings only use features that already exist: theme preference,
 * the password-reset email flow and sign-out.
 */
export default function Profile() {
    const navigate = useNavigate();
    const { mode, setMode } = useThemeMode();
    const { username, role, idClient } = readSession();
    const isClient = role === "CLIENT";

    const [client, setClient] = useState(null);
    const [loading, setLoading] = useState(isClient && Boolean(idClient));
    const [error, setError] = useState(
        isClient && !idClient ? "Your client profile could not be identified. Please sign in again." : ""
    );

    useEffect(() => {
        if (!isClient || !idClient) return undefined;

        let active = true;

        api.get(`/clients/${encodeURIComponent(idClient)}`)
            .then(({ data }) => {
                if (active) setClient(data);
            })
            .catch((err) => {
                if (active) setError(err.response?.data?.message || "Could not load your profile.");
            })
            .finally(() => {
                if (active) setLoading(false);
            });

        return () => {
            active = false;
        };
    }, [isClient, idClient]);

    function signOut() {
        clearSession();
        navigate("/login", { replace: true });
    }

    const fullName = client ? [client.prenom, client.nom].filter(Boolean).join(" ") : username;

    return (
        <div className="page">
            <PageHeader
                title="Profile & settings"
                description="Your identity, contact details and preferences for BankServices."
            />

            {error && <Alert severity="error">{error}</Alert>}

            <div className="profile-grid">
                <SectionCard
                    title={isClient ? "Personal information" : "Account"}
                    subtitle={isClient ? "As registered by your bank" : "Administrator login"}
                    icon={<PersonOutlineRounded />}
                >
                    {loading ? (
                        <LoadingState compact label="Loading your profile…" />
                    ) : (
                        <>
                            <div className="profile-identity">
                                <span className="profile-identity__avatar" aria-hidden="true">
                                    {initials(...(client ? [client.prenom, client.nom] : [username]))}
                                </span>
                                <div>
                                    <p className="profile-identity__name">{fullName || "—"}</p>
                                    <p className="profile-identity__role">{getRoleLabel(role)}</p>
                                </div>
                            </div>

                            <dl className="profile-fields">
                                <div>
                                    <dt>Username</dt>
                                    <dd>{client?.username || username || "—"}</dd>
                                </div>

                                {isClient && client && (
                                    <>
                                        <div>
                                            <dt>Online access</dt>
                                            <dd>
                                                {client.accountStatus
                                                    ? <StatusChip value={client.accountStatus} />
                                                    : "—"}
                                            </dd>
                                        </div>
                                        <div>
                                            <dt>Email</dt>
                                            <dd>{client.email || "—"}</dd>
                                        </div>
                                        <div>
                                            <dt>Phone</dt>
                                            <dd>{client.telephone || "—"}</dd>
                                        </div>
                                        <div className="profile-fields__wide">
                                            <dt>Address</dt>
                                            <dd>{client.adresse || "—"}</dd>
                                        </div>
                                        <div>
                                            <dt>Client number</dt>
                                            <dd>#{client.idClient}</dd>
                                        </div>
                                    </>
                                )}

                                {!isClient && (
                                    <div>
                                        <dt>Role</dt>
                                        <dd>{getRoleLabel(role)}</dd>
                                    </div>
                                )}
                            </dl>

                            <p className="info-banner profile-note">
                                <InfoOutlined fontSize="small" aria-hidden="true" />
                                <span>
                                    {isClient
                                        ? "These details are managed by your bank. Contact a BankServices administrator to update them."
                                        : "Administrator logins are not linked to a client profile. Client details are managed from the Clients page."}
                                </span>
                            </p>
                        </>
                    )}
                </SectionCard>

                <SectionCard title="Settings" subtitle="Preferences on this device" icon={<TuneOutlined />}>
                    <div className="profile-setting">
                        <div>
                            <p className="profile-setting__title" id="appearance-label">Appearance</p>
                            <p className="profile-setting__text">Choose how BankServices looks on this device.</p>
                        </div>
                        <ToggleButtonGroup
                            exclusive
                            size="small"
                            value={mode}
                            onChange={(_event, value) => value && setMode(value)}
                            aria-labelledby="appearance-label"
                        >
                            <ToggleButton value="light" aria-label="Light theme">
                                <LightModeOutlined fontSize="small" sx={{ mr: 0.75 }} /> Light
                            </ToggleButton>
                            <ToggleButton value="dark" aria-label="Dark theme">
                                <DarkModeOutlined fontSize="small" sx={{ mr: 0.75 }} /> Dark
                            </ToggleButton>
                        </ToggleButtonGroup>
                    </div>

                    <div className="profile-setting">
                        <div>
                            <p className="profile-setting__title">Password</p>
                            <p className="profile-setting__text">
                                Receive a secure, single-use link by email to choose a new password.
                            </p>
                        </div>
                        <Button
                            component={RouterLink}
                            to="/forgot-password"
                            variant="outlined"
                            color="inherit"
                            startIcon={<LockResetOutlined />}
                        >
                            Reset password
                        </Button>
                    </div>

                    <div className="profile-setting">
                        <div>
                            <p className="profile-setting__title">Session</p>
                            <p className="profile-setting__text">Sign out of BankServices on this device.</p>
                        </div>
                        <Button variant="outlined" color="error" startIcon={<LogoutOutlined />} onClick={signOut}>
                            Sign out
                        </Button>
                    </div>
                </SectionCard>
            </div>
        </div>
    );
}
