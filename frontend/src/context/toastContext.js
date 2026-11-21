import { createContext, useContext } from "react";

export const ToastContext = createContext({ notify: () => {} });

/* Returns notify(message, severity = "success"). */
export function useToast() {
    return useContext(ToastContext).notify;
}
