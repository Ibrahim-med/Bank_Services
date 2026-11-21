import {
    Alert,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    IconButton
} from "@mui/material";
import CloseOutlined from "@mui/icons-material/CloseOutlined";
import DownloadOutlined from "@mui/icons-material/DownloadOutlined";
import PrintOutlined from "@mui/icons-material/PrintOutlined";
import TaskAltOutlined from "@mui/icons-material/TaskAltOutlined";

import LoadingState from "./LoadingState";
import { downloadReceipt, printReceipt, receiptLines } from "../utils/receipt";

/*
 * Bill payment receipt (data from the API only).
 *   confirmation: true right after a payment, to show a success banner.
 */
export default function ReceiptDialog({ open, receipt, loading = false, error = "", confirmation = false, onClose }) {
    return (
        <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="receipt-dialog-title">
            <DialogTitle id="receipt-dialog-title" component="div" className="dialog-header">
                <div>
                    Payment receipt
                    {receipt && (
                        <p className="dialog-header__subtitle">{receipt.paymentReference}</p>
                    )}
                </div>

                <IconButton onClick={onClose} aria-label="Close receipt" size="small">
                    <CloseOutlined fontSize="small" />
                </IconButton>
            </DialogTitle>

            <DialogContent dividers>
                {loading && <LoadingState label="Loading receipt…" compact />}

                {error && <Alert severity="error">{error}</Alert>}

                {receipt && !loading && (
                    <>
                        {confirmation && (
                            <Alert severity="success" icon={<TaskAltOutlined />} sx={{ mb: 1 }}>
                                Payment confirmed. The amount has been debited from your account.
                            </Alert>
                        )}

                        <dl className="detail-list" aria-label="Receipt details">
                            {receiptLines(receipt).map(([label, value]) => (
                                <div key={label}>
                                    <dt>{label}</dt>
                                    <dd>{value ?? "—"}</dd>
                                </div>
                            ))}
                        </dl>
                    </>
                )}
            </DialogContent>

            <DialogActions>
                <Button
                    color="inherit"
                    variant="outlined"
                    startIcon={<PrintOutlined />}
                    disabled={!receipt}
                    onClick={() => printReceipt(receipt)}
                >
                    Print / PDF
                </Button>
                <Button
                    variant="contained"
                    startIcon={<DownloadOutlined />}
                    disabled={!receipt}
                    onClick={() => downloadReceipt(receipt)}
                >
                    Download
                </Button>
            </DialogActions>
        </Dialog>
    );
}
