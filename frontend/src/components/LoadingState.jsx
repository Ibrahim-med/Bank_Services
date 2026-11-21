import { CircularProgress } from "@mui/material";

/*
 * Centered loading indicator with an accessible label.
 */
export default function LoadingState({ label = "Loading…", compact = false }) {
    return (
        <div
            className={`loading-state ${compact ? "loading-state--compact" : ""}`.trim()}
            role="status"
            aria-live="polite"
        >
            <CircularProgress size={compact ? 22 : 30} thickness={4.5} />
            <span>{label}</span>
        </div>
    );
}
