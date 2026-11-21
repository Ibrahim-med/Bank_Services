import {
    describeAccountType,
    describeBillCategory,
    describeStatus,
    describeTransactionType
} from "../utils/format";

const DESCRIBERS = {
    status: describeStatus,
    transactionType: describeTransactionType,
    accountType: describeAccountType,
    billCategory: describeBillCategory
};

/*
 * Consistent badge for backend enum values.
 *   <StatusChip value="VALIDEE" />                       -> ● Validated
 *   <StatusChip kind="transactionType" value="DEPOT" />  -> Deposit
 */
export default function StatusChip({ value, kind = "status", label, tone }) {
    const description = (DESCRIBERS[kind] ?? describeStatus)(value);
    const showDot = kind === "status";

    // Keyed by value: when a status changes (e.g. Active -> Closed)
    // the badge remounts and replays its entrance (motion.css).
    return (
        <span key={String(value)} className={`status-chip tone-${tone ?? description.tone}`}>
            {showDot && <span className="status-chip__dot" aria-hidden="true" />}
            {label ?? description.label}
        </span>
    );
}
