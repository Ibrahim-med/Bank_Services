import { useState } from "react";
import { useLocation } from "react-router-dom";
import { Drawer, IconButton } from "@mui/material";
import MenuRounded from "@mui/icons-material/MenuRounded";
import AccountBalance from "@mui/icons-material/AccountBalance";

import Sidebar from "./Sidebar";
import ThemeToggle from "./ThemeToggle";
import { NAV_ITEMS, getRoleLabel } from "../utils/navigation";

/*
 * Authenticated application shell:
 * - permanent sidebar on desktop (>= 900px)
 * - top bar with menu button + slide-in drawer on smaller screens
 * - theme toggle in the top bar (no longer floating over content)
 */
export default function AppLayout({ children }) {
    const [drawerOpen, setDrawerOpen] = useState(false);
    const location = useLocation();

    const role = localStorage.getItem("role");
    const current = NAV_ITEMS.find((item) => location.pathname.startsWith(item.path));

    const closeDrawer = () => setDrawerOpen(false);

    return (
        <div className="app-shell">
            <aside className="app-shell__sidebar">
                <Sidebar />
            </aside>

            <Drawer
                open={drawerOpen}
                onClose={closeDrawer}
                transitionDuration={{ enter: 300, exit: 220 }}
                className="app-shell__drawer"
                slotProps={{ paper: { className: "app-shell__drawer-paper" } }}
                ModalProps={{ keepMounted: true }}
            >
                <Sidebar onNavigate={closeDrawer} />
            </Drawer>

            <div className="app-shell__main">
                <header className="topbar">
                    <div className="topbar__left">
                        <IconButton
                            className="topbar__menu"
                            onClick={() => setDrawerOpen(true)}
                            aria-label="Open navigation menu"
                            edge="start"
                        >
                            <MenuRounded />
                        </IconButton>

                        <span className="topbar__brand">
                            <span className="brand-mark brand-mark--sm" aria-hidden="true">
                                <AccountBalance sx={{ fontSize: 16 }} />
                            </span>
                            BankServices
                        </span>

                        <nav className="topbar__crumbs" aria-label="Breadcrumb">
                            <span>Workspace</span>
                            <span aria-hidden="true">/</span>
                            <strong aria-current="page">{current?.label ?? "Dashboard"}</strong>
                        </nav>
                    </div>

                    <div className="topbar__right">
                        <span className="topbar__role">{getRoleLabel(role)}</span>
                        <ThemeToggle />
                    </div>
                </header>

                <main className="app-shell__content" id="main-content">
                    {children}
                </main>
            </div>
        </div>
    );
}
