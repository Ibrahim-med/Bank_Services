/*
 * Card with a standard header (icon, title, subtitle, actions),
 * an optional toolbar row (search / filters) and a body.
 * Set `flush` when the body is a table that should touch the edges.
 */
export default function SectionCard({
    title,
    subtitle,
    icon,
    actions,
    toolbar,
    flush = false,
    className = "",
    children
}) {
    const hasHeader = title || subtitle || actions;

    return (
        <section className={`section-card ${className}`.trim()}>
            {hasHeader && (
                <div className="section-card__header">
                    <div className="section-card__heading">
                        {icon && (
                            <span className="section-card__icon" aria-hidden="true">
                                {icon}
                            </span>
                        )}

                        <div>
                            {title && <h2 className="section-card__title">{title}</h2>}
                            {subtitle && <p className="section-card__subtitle">{subtitle}</p>}
                        </div>
                    </div>

                    {actions && <div className="section-card__actions">{actions}</div>}
                </div>
            )}

            {toolbar && <div className="section-card__toolbar">{toolbar}</div>}

            <div className={flush ? "section-card__body--flush" : "section-card__body"}>
                {children}
            </div>
        </section>
    );
}
