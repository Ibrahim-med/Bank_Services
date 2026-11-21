import { createContext, useContext } from "react";

/* Same key the application used before, so saved preferences are kept. */
export const THEME_STORAGE_KEY = "bankservices-theme";

export const ThemeModeContext = createContext({
    mode: "light",
    isDark: false,
    toggleMode: () => {},
    setMode: () => {}
});

export function readStoredMode() {
    try {
        return localStorage.getItem(THEME_STORAGE_KEY) === "dark" ? "dark" : "light";
    } catch {
        return "light";
    }
}

/* Returns { mode, isDark, toggleMode, setMode }. */
export function useThemeMode() {
    return useContext(ThemeModeContext);
}
