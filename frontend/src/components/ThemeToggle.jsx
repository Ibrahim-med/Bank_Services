import { IconButton, Tooltip } from "@mui/material";
import DarkModeOutlined from "@mui/icons-material/DarkModeOutlined";
import LightModeOutlined from "@mui/icons-material/LightModeOutlined";

import { useThemeMode } from "../context/themeModeContext";

/*
 * Light / dark switch. `tone="onDark"` is used on navy
 * backgrounds (Home hero navbar, auth brand panel).
 */
export default function ThemeToggle({ tone = "default", className = "", sx }) {
    const { isDark, toggleMode } = useThemeMode();
    const label = isDark ? "Switch to light mode" : "Switch to dark mode";

    return (
        <Tooltip title={label} arrow>
            <IconButton
                onClick={toggleMode}
                aria-label={label}
                className={`theme-toggle theme-toggle--${tone} icon-swap ${className}`.trim()}
                sx={sx}
            >
                {isDark ? (
                    <LightModeOutlined fontSize="small" />
                ) : (
                    <DarkModeOutlined fontSize="small" />
                )}
            </IconButton>
        </Tooltip>
    );
}
