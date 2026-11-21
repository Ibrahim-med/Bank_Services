import { alpha, createTheme } from "@mui/material/styles";

/* =========================================================
   DESIGN TOKENS
   Single source of truth for both themes. MUI components read
   them through the theme; plain CSS reads them through the
   --bs-* custom properties emitted by ThemeModeProvider.
========================================================= */

export const tokens = {
    /* Warm cream neutrals (Circle-inspired) around the BankServices navy/blue. */
    light: {
        bg: "#F7F2EA",
        surface: "#FFFDF9",
        surfaceMuted: "#FAF5EE",
        surfaceHover: "#F4EDE2",
        border: "#E8DFD1",
        borderStrong: "#D5C9B7",

        text: "#14213D",
        textMuted: "#4F5A70",
        textSubtle: "#868FA0",

        sand: "#E8CDB2",
        sandSoft: "#F3E5D6",

        primary: "#1D4ED8",
        primaryHover: "#1E40AF",
        primarySoft: "#EEF3FE",
        primaryContrast: "#FFFFFF",

        success: "#047857",
        successSoft: "#E8F7F0",
        warning: "#B45309",
        warningSoft: "#FEF5E7",
        error: "#B91C1C",
        errorSoft: "#FDEEEE",
        info: "#0369A1",
        infoSoft: "#EAF4FB",
        violet: "#6D28D9",
        violetSoft: "#F2EDFD",

        navy: "#0B1B33",
        navyRaised: "#13284A",
        navyBorder: "#21385C",
        navyText: "#B6C3D6",
        navyMuted: "#7D90AB",

        shadowSm: "0 1px 2px rgba(20, 33, 61, 0.04), 0 6px 18px -10px rgba(20, 33, 61, 0.14)",
        shadowMd: "0 12px 30px -14px rgba(20, 33, 61, 0.22)",
        shadowLg: "0 30px 60px -24px rgba(20, 33, 61, 0.32)",
        focusRing: "0 0 0 3px rgba(29, 78, 216, 0.22)"
    },

    dark: {
        bg: "#0A1220",
        surface: "#111B2E",
        surfaceMuted: "#0E1727",
        surfaceHover: "#17233A",
        border: "#22304A",
        borderStrong: "#33425E",

        text: "#E9EEF6",
        textMuted: "#A2AFC3",
        textSubtle: "#6F7E95",

        sand: "#C9A689",
        sandSoft: "rgba(201, 166, 137, 0.14)",

        primary: "#6EA0FF",
        primaryHover: "#8DB4FF",
        primarySoft: "rgba(110, 160, 255, 0.12)",
        primaryContrast: "#08132A",

        success: "#4ADE9B",
        successSoft: "rgba(74, 222, 155, 0.12)",
        warning: "#FBBF4D",
        warningSoft: "rgba(251, 191, 77, 0.12)",
        error: "#F87171",
        errorSoft: "rgba(248, 113, 113, 0.12)",
        info: "#5CC2F2",
        infoSoft: "rgba(92, 194, 242, 0.12)",
        violet: "#B79CFF",
        violetSoft: "rgba(183, 156, 255, 0.12)",

        navy: "#0C1426",
        navyRaised: "#152038",
        navyBorder: "#1F2B44",
        navyText: "#B6C3D6",
        navyMuted: "#74859E",

        shadowSm: "0 1px 2px rgba(0, 0, 0, 0.30)",
        shadowMd: "0 6px 18px rgba(0, 0, 0, 0.35)",
        shadowLg: "0 24px 56px rgba(0, 0, 0, 0.55)",
        focusRing: "0 0 0 3px rgba(110, 160, 255, 0.30)"
    }
};

/* Converts a token set into CSS custom properties (--bs-bg, --bs-text...). */
export function tokensToCssVars(set) {
    return Object.fromEntries(
        Object.entries(set).map(([key, value]) => [
            `--bs-${key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`,
            value
        ])
    );
}

const FONT_STACK =
    '"Inter", system-ui, -apple-system, "Segoe UI", Roboto, Arial, sans-serif';
const DISPLAY_STACK = '"Outfit", "Inter", system-ui, sans-serif';
const LABEL_STACK = '"Barlow Semi Condensed", "Inter", system-ui, sans-serif';

/* =========================================================
   MUI THEME FACTORY
========================================================= */

export function createAppTheme(mode = "light") {
    const t = tokens[mode] ?? tokens.light;
    const isDark = mode === "dark";

    return createTheme({
        palette: {
            mode,
            primary: {
                main: t.primary,
                dark: t.primaryHover,
                light: t.primarySoft,
                contrastText: t.primaryContrast
            },
            secondary: {
                main: isDark ? "#94A3B8" : "#334155",
                contrastText: isDark ? "#0A0F1C" : "#FFFFFF"
            },
            success: { main: t.success, light: t.successSoft, contrastText: isDark ? "#05140D" : "#FFFFFF" },
            warning: { main: t.warning, light: t.warningSoft, contrastText: isDark ? "#1A1003" : "#FFFFFF" },
            error: { main: t.error, light: t.errorSoft, contrastText: isDark ? "#1C0606" : "#FFFFFF" },
            info: { main: t.info, light: t.infoSoft, contrastText: isDark ? "#04131B" : "#FFFFFF" },
            background: { default: t.bg, paper: t.surface },
            text: { primary: t.text, secondary: t.textMuted, disabled: t.textSubtle },
            divider: t.border,
            action: {
                hover: t.surfaceHover,
                selected: t.primarySoft
            },
            /* Custom tokens, usable in sx: bgcolor: "app.surfaceMuted" */
            app: t
        },

        shape: { borderRadius: 12 },

        typography: {
            fontFamily: FONT_STACK,
            fontSize: 14,
            h1: { fontFamily: DISPLAY_STACK, fontSize: "2rem", fontWeight: 400, letterSpacing: "-0.02em", lineHeight: 1.15 },
            h2: { fontFamily: DISPLAY_STACK, fontSize: "1.6rem", fontWeight: 400, letterSpacing: "-0.015em", lineHeight: 1.2 },
            h3: { fontFamily: DISPLAY_STACK, fontSize: "1.3rem", fontWeight: 500, letterSpacing: "-0.01em" },
            h4: { fontFamily: DISPLAY_STACK, fontSize: "1.15rem", fontWeight: 500 },
            h5: { fontFamily: DISPLAY_STACK, fontSize: "1.05rem", fontWeight: 500 },
            h6: { fontFamily: DISPLAY_STACK, fontSize: "0.975rem", fontWeight: 500 },
            subtitle1: { fontSize: "0.9375rem", fontWeight: 600 },
            subtitle2: { fontSize: "0.8125rem", fontWeight: 600 },
            body1: { fontSize: "0.9375rem", lineHeight: 1.6 },
            body2: { fontSize: "0.8125rem", lineHeight: 1.55 },
            caption: { fontSize: "0.75rem", lineHeight: 1.5 },
            overline: {
                fontSize: "0.6875rem",
                fontWeight: 650,
                letterSpacing: "0.07em",
                lineHeight: 1.5
            },
            button: {
                fontFamily: LABEL_STACK,
                fontSize: "0.95rem",
                textTransform: "uppercase",
                fontWeight: 600,
                letterSpacing: "0.07em"
            }
        },

        components: {
            MuiCssBaseline: {
                styleOverrides: {
                    body: {
                        backgroundColor: t.bg,
                        color: t.text
                    }
                }
            },

            MuiButton: {
                defaultProps: { disableElevation: true },
                styleOverrides: {
                    root: {
                        borderRadius: 999,
                        minHeight: 40,
                        paddingInline: 20,
                        transition:
                            "background-color .18s ease, border-color .18s ease, " +
                            "box-shadow .18s ease, transform .18s cubic-bezier(0.2, 0, 0, 1), opacity .18s ease",
                        "&.Mui-focusVisible": { boxShadow: t.focusRing },
                        /* Hover lift + press effect (pointer devices only) */
                        "@media (hover: hover)": {
                            "&:not(.Mui-disabled):hover": { transform: "translateY(-1px)" }
                        },
                        "&:not(.Mui-disabled):active": {
                            transform: "translateY(0) scale(0.98)",
                            transitionDuration: "80ms"
                        },
                        "&.Mui-disabled": { opacity: 0.6 },
                        "&.MuiButton-loading": { opacity: 1 }
                    },
                    contained: {
                        "@media (hover: hover)": {
                            "&:not(.Mui-disabled):hover": {
                                boxShadow: `0 6px 16px ${alpha(t.primary, isDark ? 0.25 : 0.28)}`
                            }
                        }
                    },
                    text: {
                        "@media (hover: hover)": {
                            "&:not(.Mui-disabled):hover": { transform: "none" }
                        }
                    },
                    sizeSmall: { minHeight: 32, paddingInline: 14, fontSize: "0.875rem" },
                    sizeLarge: { minHeight: 50, paddingInline: 28, fontSize: "1.02rem" },
                    outlined: {
                        borderColor: t.borderStrong,
                        backgroundColor: t.surface,
                        "&:hover": { borderColor: t.textSubtle, backgroundColor: t.surfaceHover }
                    },
                    outlinedInherit: { color: t.text },
                    outlinedError: { borderColor: alpha(t.error, 0.45), backgroundColor: "transparent" },
                    outlinedSuccess: { borderColor: alpha(t.success, 0.45), backgroundColor: "transparent" }
                }
            },

            MuiIconButton: {
                styleOverrides: {
                    root: {
                        borderRadius: 12,
                        transition: "background-color .15s ease, color .15s ease, transform .15s ease",
                        "&.Mui-focusVisible": { boxShadow: t.focusRing },
                        "&:not(.Mui-disabled):active": { transform: "scale(0.92)" }
                    }
                }
            },

            MuiPaper: {
                styleOverrides: {
                    root: { backgroundImage: "none" },
                    rounded: { borderRadius: 18 }
                }
            },

            MuiCard: {
                defaultProps: { elevation: 0 },
                styleOverrides: {
                    root: {
                        borderRadius: 22,
                        border: `1px solid ${t.border}`,
                        boxShadow: t.shadowSm
                    }
                }
            },

            MuiOutlinedInput: {
                styleOverrides: {
                    root: {
                        borderRadius: 12,
                        backgroundColor: isDark ? t.surfaceMuted : t.surface,
                        transition: "box-shadow .15s ease",
                        "& .MuiOutlinedInput-notchedOutline": { borderColor: t.borderStrong },
                        "&:hover:not(.Mui-disabled):not(.Mui-error) .MuiOutlinedInput-notchedOutline": {
                            borderColor: t.textSubtle
                        },
                        "&.Mui-focused": { boxShadow: t.focusRing },
                        "&.Mui-focused .MuiOutlinedInput-notchedOutline": {
                            borderColor: t.primary,
                            borderWidth: 1
                        },
                        "&.Mui-error.Mui-focused": { boxShadow: `0 0 0 3px ${alpha(t.error, 0.2)}` },
                        "&.Mui-disabled": { backgroundColor: t.surfaceMuted },
                        "&.Mui-disabled .MuiOutlinedInput-notchedOutline": { borderColor: t.border }
                    },
                    input: {
                        "&::placeholder": { color: t.textSubtle, opacity: 1 }
                    }
                }
            },

            MuiInputLabel: {
                styleOverrides: {
                    root: { color: t.textMuted }
                }
            },

            MuiFormHelperText: {
                styleOverrides: {
                    root: { marginInline: 2, marginTop: 6 }
                }
            },

            MuiTableCell: {
                styleOverrides: {
                    root: {
                        borderBottom: `1px solid ${t.border}`,
                        padding: "13px 16px",
                        fontSize: "0.8125rem"
                    },
                    head: {
                        backgroundColor: t.surfaceMuted,
                        color: t.textMuted,
                        fontFamily: LABEL_STACK,
                        fontWeight: 600,
                        fontSize: "0.8125rem",
                        letterSpacing: "0.08em",
                        textTransform: "uppercase",
                        whiteSpace: "nowrap",
                        paddingBlock: 11
                    }
                }
            },

            MuiTableRow: {
                styleOverrides: {
                    root: {
                        "&.MuiTableRow-hover:hover": { backgroundColor: t.surfaceHover },
                        "&:last-child td": { borderBottom: 0 }
                    }
                }
            },

            MuiTablePagination: {
                styleOverrides: {
                    root: { borderTop: `1px solid ${t.border}`, color: t.textMuted },
                    toolbar: { minHeight: 52 },
                    selectLabel: { fontSize: "0.8125rem" },
                    displayedRows: { fontSize: "0.8125rem" }
                }
            },

            MuiChip: {
                styleOverrides: {
                    root: { borderRadius: 999, fontWeight: 600 },
                    sizeSmall: { height: 24, fontSize: "0.75rem" }
                }
            },

            MuiAlert: {
                styleOverrides: {
                    root: { borderRadius: 14, alignItems: "flex-start", fontSize: "0.8125rem" },
                    standardSuccess: { backgroundColor: t.successSoft, color: t.text, border: `1px solid ${alpha(t.success, 0.25)}` },
                    standardError: { backgroundColor: t.errorSoft, color: t.text, border: `1px solid ${alpha(t.error, 0.25)}` },
                    standardWarning: { backgroundColor: t.warningSoft, color: t.text, border: `1px solid ${alpha(t.warning, 0.25)}` },
                    standardInfo: { backgroundColor: t.infoSoft, color: t.text, border: `1px solid ${alpha(t.info, 0.25)}` },
                    icon: { paddingTop: 8 },
                    message: { paddingTop: 9 }
                }
            },

            MuiDialog: {
                styleOverrides: {
                    paper: {
                        borderRadius: 26,
                        border: `1px solid ${t.border}`,
                        boxShadow: t.shadowLg
                    }
                }
            },

            MuiDialogTitle: {
                styleOverrides: {
                    root: {
                        padding: "24px 28px 10px",
                        fontFamily: DISPLAY_STACK,
                        fontSize: "1.3rem",
                        fontWeight: 500,
                        letterSpacing: "-0.01em"
                    }
                }
            },

            MuiDialogContent: {
                styleOverrides: { root: { padding: "14px 28px" } }
            },

            MuiDialogActions: {
                styleOverrides: { root: { padding: "14px 28px 24px", gap: 8 } }
            },

            MuiBackdrop: {
                styleOverrides: {
                    root: {
                        "&:not(.MuiBackdrop-invisible)": {
                            backgroundColor: isDark ? "rgba(2, 6, 15, 0.72)" : "rgba(15, 23, 42, 0.45)"
                        }
                    }
                }
            },

            MuiMenu: {
                styleOverrides: {
                    paper: {
                        border: `1px solid ${t.border}`,
                        borderRadius: 16,
                        boxShadow: t.shadowMd,
                        marginTop: 6
                    }
                }
            },

            MuiMenuItem: {
                styleOverrides: {
                    root: {
                        fontSize: "0.875rem",
                        minHeight: 38,
                        "&.Mui-selected": { backgroundColor: t.primarySoft },
                        "&.Mui-selected:hover": { backgroundColor: t.primarySoft }
                    }
                }
            },

            MuiTooltip: {
                styleOverrides: {
                    tooltip: {
                        backgroundColor: isDark ? "#E7ECF3" : "#0F172A",
                        color: isDark ? "#0A0F1C" : "#FFFFFF",
                        fontSize: "0.75rem",
                        fontWeight: 500,
                        borderRadius: 7,
                        padding: "6px 10px"
                    },
                    arrow: { color: isDark ? "#E7ECF3" : "#0F172A" }
                }
            },

            MuiSnackbarContent: {
                styleOverrides: { root: { borderRadius: 14 } }
            },

            MuiDrawer: {
                styleOverrides: { paper: { backgroundImage: "none" } }
            },

            MuiToggleButton: {
                styleOverrides: {
                    root: {
                        fontFamily: LABEL_STACK,
                        textTransform: "uppercase",
                        letterSpacing: "0.07em",
                        fontWeight: 600,
                        color: t.textMuted,
                        borderColor: t.borderStrong,
                        "&.Mui-selected": {
                            backgroundColor: t.primarySoft,
                            color: t.primary,
                            borderColor: alpha(t.primary, 0.5)
                        },
                        "&.Mui-selected:hover": { backgroundColor: t.primarySoft }
                    }
                }
            }
        }
    });
}

/* Kept for backward compatibility with any import of the default theme. */
const theme = createAppTheme("light");

export default theme;
