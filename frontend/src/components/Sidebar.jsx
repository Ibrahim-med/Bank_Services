import { NavLink, useLocation, useNavigate } from "react-router-dom";

import LogoutOutlined from "@mui/icons-material/LogoutOutlined";
import AccountBalance from "@mui/icons-material/AccountBalance";

import { initials } from "../utils/format";
import { NAV_ITEMS, getRoleLabel } from "../utils/navigation";
import { clearSession } from "../utils/session";

/* Link height (42px) + gap (2px); keep in sync with App.css. */
const LINK_PITCH = 44;

/*
 * Sidebar content. Rendered permanently on desktop and inside
 * a drawer on mobile (`onNavigate` closes the drawer).
 */
export default function Sidebar({ onNavigate }) {
    const navigate = useNavigate();
    const location = useLocation();

    const role = localStorage.getItem("role");
    const username = localStorage.getItem("username") || "User";

    const items = NAV_ITEMS.filter((item) => !item.roles || item.roles.includes(role));
    const activeIndex = items.findIndex((item) => location.pathname === item.path);

    function logout() {
        clearSession();

        onNavigate?.();
        navigate("/login", { replace: true });
    }

    return (
        <div className="sidebar">
            <div className="sidebar__brand">
                <span className="brand-mark" aria-hidden="true">
                    <AccountBalance fontSize="small" />
                </span>

                <div className="sidebar__brand-text">
                    <strong>BankServices</strong>
                    <span>Banking portal</span>
                </div>
            </div>

            <nav className="sidebar__nav" aria-label="Main navigation">
                <span className="sidebar__section-label">Menu</span>

                <div className="sidebar__links">
                    {/* Highlight that slides to the active link (motion.css) */}
                    <span
                        className="sidebar__indicator"
                        aria-hidden="true"
                        style={{
                            transform: `translateY(${Math.max(activeIndex, 0) * LINK_PITCH}px)`,
                            opacity: activeIndex === -1 ? 0 : 1
                        }}
                    />

                    {items.map((item) => (
                        <NavLink
                            key={item.path}
                            to={item.path}
                            end
                            onClick={onNavigate}
                            className={({ isActive }) =>
                                isActive ? "sidebar__link is-active" : "sidebar__link"
                            }
                        >
                            <span className="sidebar__link-icon" aria-hidden="true">
                                <item.icon fontSize="small" />
                            </span>
                            <span>{item.label}</span>
                        </NavLink>
                    ))}
                </div>
            </nav>

            <div className="sidebar__footer">
                <div className="sidebar__user">
                    <span className="sidebar__avatar" aria-hidden="true">
                        {initials(username)}
                    </span>

                    <div className="sidebar__user-text">
                        <strong title={username}>{username}</strong>
                        <span>{getRoleLabel(role)}</span>
                    </div>
                </div>

                <button type="button" className="sidebar__logout" onClick={logout}>
                    <LogoutOutlined fontSize="small" aria-hidden="true" />
                    <span>Sign out</span>
                </button>
            </div>
        </div>
    );
}
