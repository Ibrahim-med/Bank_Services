import { Button } from "@mui/material";
import RefreshOutlined from "@mui/icons-material/Refresh";

/*
 * Page-level "Refresh" action. The icon spins while data is
 * loading (activity indicator) and the button is disabled so
 * requests cannot be stacked.
 */
export default function RefreshButton({ onClick, loading = false, disabled = false }) {
    return (
        <Button
            variant="outlined"
            color="inherit"
            onClick={onClick}
            disabled={loading || disabled}
            aria-busy={loading}
            startIcon={<RefreshOutlined className={loading ? "is-spinning" : undefined} />}
        >
            Refresh
        </Button>
    );
}
