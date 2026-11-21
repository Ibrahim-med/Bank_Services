import { useCallback, useLayoutEffect, useMemo, useState } from "react";
import { flushSync } from "react-dom";

import { prefersReducedMotion } from "../hooks/usePrefersReducedMotion";
import { CssBaseline, GlobalStyles, ThemeProvider } from "@mui/material";

import { createAppTheme, tokens, tokensToCssVars } from "../theme";
import {
    THEME_STORAGE_KEY,
    ThemeModeContext,
    readStoredMode
} from "./themeModeContext";

/*
 * Owns the light/dark preference for the whole application:
 * - persists it in localStorage (same key as before)
 * - mirrors it on <html data-theme="..."> for plain CSS
 * - builds the matching MUI theme (dialogs, menus and tooltips
 *   rendered in portals follow the theme automatically)
 * - exposes the design tokens as --bs-* CSS variables
 */
export default function ThemeModeProvider({ children }) {
    const [mode, setMode] = useState(readStoredMode);

    useLayoutEffect(() => {
        const root = document.documentElement;

        root.setAttribute("data-theme", mode);
        root.style.colorScheme = mode;
        root.style.backgroundColor = tokens[mode].bg;

        try {
            localStorage.setItem(THEME_STORAGE_KEY, mode);
        } catch {
            /* Storage unavailable (private mode): keep the in-memory value. */
        }
    }, [mode]);

    /*
     * Switches theme with a single cross-fade (View Transitions API)
     * so every surface changes together. Browsers without the API,
     * or users who prefer reduced motion, get an instant switch with
     * per-element transitions frozen for that frame (no flashing).
     */
    const toggleMode = useCallback(() => {
        const root = document.documentElement;
        const apply = () => setMode((current) => (current === "dark" ? "light" : "dark"));

        if (document.startViewTransition && !prefersReducedMotion()) {
            document.startViewTransition(() => flushSync(apply));
            return;
        }

        root.classList.add("theme-switching");
        flushSync(apply);
        requestAnimationFrame(() => {
            requestAnimationFrame(() => root.classList.remove("theme-switching"));
        });
    }, []);

    const theme = useMemo(() => createAppTheme(mode), [mode]);

    const value = useMemo(
        () => ({ mode, isDark: mode === "dark", toggleMode, setMode }),
        [mode, toggleMode]
    );

    return (
        <ThemeModeContext.Provider value={value}>
            <ThemeProvider theme={theme}>
                <CssBaseline />
                <GlobalStyles styles={{ ":root": tokensToCssVars(tokens[mode]) }} />
                {children}
            </ThemeProvider>
        </ThemeModeContext.Provider>
    );
}
