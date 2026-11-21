import {
    Button,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle
} from "@mui/material";
import WarningAmberOutlined from "@mui/icons-material/WarningAmberOutlined";

/*
 * Confirmation dialog for important or destructive actions.
 *   tone: error | warning | success | primary
 * The dialog cannot be dismissed while `loading` is true.
 */
export default function ConfirmDialog({
    open,
    title,
    description,
    children,
    confirmLabel = "Confirm",
    loadingLabel = "Working…",
    cancelLabel = "Cancel",
    tone = "primary",
    icon,
    loading = false,
    onConfirm,
    onClose
}) {
    const handleClose = () => {
        if (!loading) onClose?.();
    };

    return (
        <Dialog
            open={open}
            onClose={handleClose}
            fullWidth
            maxWidth="xs"
            aria-labelledby="confirm-dialog-title"
            aria-describedby="confirm-dialog-description"
        >
            <DialogTitle id="confirm-dialog-title" component="div" className="confirm-dialog__title">
                <span className={`confirm-dialog__icon tone-${tone}`} aria-hidden="true">
                    {icon ?? <WarningAmberOutlined fontSize="small" />}
                </span>
                <span>{title}</span>
            </DialogTitle>

            <DialogContent>
                {description && (
                    <p id="confirm-dialog-description" className="confirm-dialog__description">
                        {description}
                    </p>
                )}
                {children}
            </DialogContent>

            <DialogActions>
                <Button onClick={handleClose} disabled={loading} color="inherit" variant="outlined">
                    {cancelLabel}
                </Button>

                <Button
                    onClick={onConfirm}
                    disabled={loading}
                    variant="contained"
                    color={tone === "warning" ? "warning" : tone}
                    startIcon={loading ? <CircularProgress size={16} color="inherit" /> : null}
                    autoFocus
                >
                    {loading ? loadingLabel : confirmLabel}
                </Button>
            </DialogActions>
        </Dialog>
    );
}
