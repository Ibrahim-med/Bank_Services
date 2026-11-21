import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";

import AppLayout from "./components/AppLayout";
import Home from "./pages/Home";
import Dashboard from "./pages/Dashboard";
import Clients from "./pages/Clients";
import Accounts from "./pages/Accounts";
import Transactions from "./pages/Transactions";
import Login from "./pages/Login";
import ForgotPassword from "./pages/ForgotPassword";
import ResetPassword from "./pages/ResetPassword";
import ActivateAccount from "./pages/ActivateAccount";
import AdminBills from "./pages/AdminBills";
import ClientBills from "./pages/ClientBills";
import BillPayments from "./pages/BillPayments";
import Profile from "./pages/Profile";

import "./App.css";

function ProtectedRoute({ children, allowedRoles }) {
    const token = localStorage.getItem("token");
    const role = localStorage.getItem("role");

    if (!token) {
        return <Navigate to="/login" replace />;
    }

    if (allowedRoles && !allowedRoles.includes(role)) {
        return <Navigate to="/app/dashboard" replace />;
    }

    return children;
}

/* Bills: administrators issue and follow bills, clients consult and pay theirs. */
function BillsPage() {
    return localStorage.getItem("role") === "ADMIN" ? <AdminBills /> : <ClientBills />;
}

function MainLayout() {
    return (
        <AppLayout>
            <Routes>
                <Route
                    path="/"
                    element={<Navigate to="/app/dashboard" replace />}
                />

                <Route path="/dashboard" element={<Dashboard />} />

                <Route
                    path="/clients"
                    element={
                        <ProtectedRoute allowedRoles={["ADMIN"]}>
                            <Clients />
                        </ProtectedRoute>
                    }
                />

                <Route path="/accounts" element={<Accounts />} />
                <Route path="/transactions" element={<Transactions />} />

                <Route path="/bills" element={<BillsPage />} />

                <Route path="/profile" element={<Profile />} />

                <Route
                    path="/bill-payments"
                    element={
                        <ProtectedRoute allowedRoles={["CLIENT"]}>
                            <BillPayments />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="*"
                    element={<Navigate to="/app/dashboard" replace />}
                />
            </Routes>
        </AppLayout>
    );
}

/*
 * Theme state (light / dark + persistence) now lives in
 * ThemeModeProvider (see main.jsx). The toggle is placed in the
 * top bar, the Home navbar and the authentication pages.
 */
export default function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<Home />} />
                <Route path="/login" element={<Login />} />

                <Route
                    path="/forgot-password"
                    element={<ForgotPassword />}
                />

                <Route
                    path="/reset-password"
                    element={<ResetPassword />}
                />

                <Route
                    path="/activate-account"
                    element={<ActivateAccount />}
                />

                <Route
                    path="/app/*"
                    element={
                        <ProtectedRoute>
                            <MainLayout />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="*"
                    element={<Navigate to="/" replace />}
                />
            </Routes>
        </BrowserRouter>
    );
}
