/*
 * Friendly placeholder for empty lists and search results.
 */
export default function EmptyState({ icon, title, description, action, compact = false }) {
    return (
        <div className={`empty-state ${compact ? "empty-state--compact" : ""}`.trim()} role="status">
            {icon && (
                <span className="empty-state__icon" aria-hidden="true">
                    {icon}
                </span>
            )}

            <p className="empty-state__title">{title}</p>

            {description && <p className="empty-state__description">{description}</p>}

            {action && <div className="empty-state__action">{action}</div>}
        </div>
    );
}
