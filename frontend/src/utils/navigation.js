import DashboardOutlined from "@mui/icons-material/DashboardOutlined";
import PeopleAltOutlined from "@mui/icons-material/PeopleAltOutlined";
import AccountBalanceWalletOutlined from "@mui/icons-material/AccountBalanceWalletOutlined";
import SwapHorizOutlined from "@mui/icons-material/SwapHorizOutlined";
import ReceiptLongOutlined from "@mui/icons-material/ReceiptLongOutlined";
import ReceiptOutlined from "@mui/icons-material/ReceiptOutlined";
import ManageAccountsOutlined from "@mui/icons-material/ManageAccountsOutlined";

/* Application navigation. `roles` limits an entry to specific roles. */
export const NAV_ITEMS = [
    { label: "Dashboard", path: "/app/dashboard", icon: DashboardOutlined },
    { label: "Clients", path: "/app/clients", icon: PeopleAltOutlined, roles: ["ADMIN"] },
    { label: "Bank Accounts", path: "/app/accounts", icon: AccountBalanceWalletOutlined },
    { label: "Transactions", path: "/app/transactions", icon: SwapHorizOutlined },
    { label: "Bills", path: "/app/bills", icon: ReceiptLongOutlined },
    { label: "Payment history", path: "/app/bill-payments", icon: ReceiptOutlined, roles: ["CLIENT"] },
    { label: "Profile & settings", path: "/app/profile", icon: ManageAccountsOutlined }
];

export function getRoleLabel(role) {
    if (role === "ADMIN") return "Administrator";
    if (role === "CLIENT") return "Client";
    return role || "Account";
}
