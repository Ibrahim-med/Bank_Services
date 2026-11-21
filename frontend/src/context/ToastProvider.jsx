import { useCallback, useMemo, useState } from "react";
import { Alert, Snackbar } from "@mui/material";

import { ToastContext } from "./toastContext";

/*
 * Lightweight notification system: success / info messages
 * appear in the bottom-right corner and close by themselves.
 * Errors that need attention stay inline on the page.
 */
export default function ToastProvider({ children }) {
    const [toast, setToast] = useState({
        open: false,
        key: 0,
        message: "",
        severity: "success"
    });

    const notify = useCallback((message, severity = "success") => {
        setToast({ open: true, key: Date.now(), message, severity });
    }, []);

    const handleClose = (_event, reason) => {
        if (reason === "clickaway") return;
        setToast((current) => ({ ...current, open: false }));
    };

    const value = useMemo(() => ({ notify }), [notify]);

    return (
        <ToastContext.Provider value={value}>
            {children}

            <Snackbar
                key={toast.key}
                open={toast.open}
                autoHideDuration={toast.severity === "error" ? 7000 : 4500}
                onClose={handleClose}
                anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
            >
                <Alert
                    onClose={handleClose}
                    severity={toast.severity}
                    role={toast.severity === "error" ? "alert" : "status"}
                    sx={{ minWidth: { sm: 320 }, boxShadow: "var(--bs-shadow-md)" }}
                >
                    {toast.message}
                </Alert>
            </Snackbar>
        </ToastContext.Provider>
    );
}
