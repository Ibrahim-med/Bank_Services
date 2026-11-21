/*
 * Consistent page title block: title, optional badge,
 * description and right-aligned actions.
 *
 * variant="hero" renders the gradient welcome band (dashboard)
 * with an optional `eyebrow` label and decorative `art`.
 */
export default function PageHeader({ title, description, badge, actions, variant, eyebrow, art }) {
    const isHero = variant === "hero";

    return (
        <header className={`page-header ${isHero ? "page-header--hero" : ""}`.trim()}>
            <div className="page-header__text">
                {isHero && eyebrow && <span className="page-header__eyebrow">{eyebrow}</span>}

                <div className="page-header__title-row">
                    <h1 className="page-header__title">{title}</h1>
                    {badge}
                </div>

                {description && (
                    <p className="page-header__description">{description}</p>
                )}
            </div>

            {isHero && art && (
                <div className="page-header__art" aria-hidden="true">
                    {art}
                </div>
            )}

            {actions && <div className="page-header__actions">{actions}</div>}
        </header>
    );
}
