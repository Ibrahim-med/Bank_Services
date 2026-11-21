/* =========================================================
   BILL PAYMENT RECEIPT
   Built only from the receipt returned by the API
   (GET /client/bill-payments/{id}/receipt or the payment response).
   The account number is already masked by the backend.
========================================================= */

import {
    describeAccountType,
    describeBillCategory,
    describeStatus,
    formatDate,
    formatDateTime,
    formatMoney
} from "./format";

export function receiptLines(receipt) {
    return [
        ["Payment reference", receipt.paymentReference],
        ["Payment status", describeStatus(receipt.status).label],
        ["Payment date", formatDateTime(receipt.paymentDate)],
        ["Amount", formatMoney(receipt.amount)],
        ["Bill reference", receipt.billReference],
        ["Provider", receipt.provider],
        ["Category", describeBillCategory(receipt.category).label],
        ["Bill issue date", formatDate(receipt.billIssueDate)],
        ["Bill due date", formatDate(receipt.billDueDate)],
        ["Debited account", `${receipt.accountNumberMasked} (${describeAccountType(receipt.accountType).label})`],
        ["Account holder", receipt.clientName],
        ["Transaction ID", `#${receipt.idTransaction}`]
    ];
}

export function buildReceiptText(receipt) {
    const lines = receiptLines(receipt);
    const width = Math.max(...lines.map(([label]) => label.length));

    return [
        "BANKSERVICES - BILL PAYMENT RECEIPT",
        "===================================",
        "",
        ...lines.map(([label, value]) => `${label.padEnd(width)} : ${value ?? "—"}`),
        "",
        "This receipt confirms that the payment above was debited from your account."
    ].join("\n");
}

export const receiptFileName = (receipt) =>
    `receipt-${String(receipt.paymentReference || receipt.idBillPayment).replace(/[^A-Za-z0-9-]/g, "_")}.txt`;

export function downloadReceipt(receipt) {
    const blob = new Blob([buildReceiptText(receipt)], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");

    link.href = url;
    link.download = receiptFileName(receipt);
    document.body.appendChild(link);
    link.click();
    link.remove();

    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

const escapeHtml = (value) =>
    String(value ?? "—").replace(/[&<>"']/g, (char) => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[char]);

/* Opens a printable version (the browser can also "Save as PDF"). */
export function printReceipt(receipt) {
    const rows = receiptLines(receipt)
        .map(([label, value]) => `<tr><th>${escapeHtml(label)}</th><td>${escapeHtml(value)}</td></tr>`)
        .join("");

    const win = window.open("", "_blank", "width=720,height=820");

    if (!win) return false;

    win.document.write(`<!doctype html><html><head><meta charset="utf-8">
<title>Receipt ${escapeHtml(receipt.paymentReference)}</title>
<style>
 body{font-family:system-ui,-apple-system,"Segoe UI",sans-serif;color:#0f172a;margin:40px}
 h1{font-size:20px;margin:0 0 4px} p{color:#475569;margin:0 0 24px}
 table{border-collapse:collapse;width:100%} th,td{padding:10px 12px;border-bottom:1px solid #e2e8f0;text-align:left;font-size:14px}
 th{color:#475569;font-weight:500;width:40%} td{font-weight:600}
 footer{margin-top:24px;font-size:12px;color:#64748b}
</style></head><body>
<h1>BankServices — Bill payment receipt</h1>
<p>Payment reference ${escapeHtml(receipt.paymentReference)}</p>
<table>${rows}</table>
<footer>This receipt confirms that the payment above was debited from your account.</footer>
<script>window.onload=function(){window.print();}</script>
</body></html>`);
    win.document.close();

    return true;
}
