import { Skeleton } from "@mui/material";

import AnimatedNumber from "./motion/AnimatedNumber";
import { formatNumber } from "../utils/format";

/* Counts stay whole numbers while animating. */
const formatCount = (value) => formatNumber(Math.round(value));

/*
 * Statistic tile.
 *  value:   a number (counts up, rendered with `format`) or any node
 *  tone:    primary | success | warning | error | violet | info | neutral
 *  variant: default | featured (navy card used for the key balance figure)
 */
export default function StatCard({
    label,
    value,
    format = formatCount,
    hint,
    icon,
    tone = "primary",
    variant = "default",
    loading = false
}) {
    return (
        <article className={`stat-card stat-card--${variant}`}>
            <div className="stat-card__top">
                <span className="stat-card__label">{label}</span>

                {icon && (
                    <span className={`stat-card__icon tone-${tone}`} aria-hidden="true">
                        {icon}
                    </span>
                )}
            </div>

            <div className="stat-card__value">
                {loading ? (
                    <Skeleton
                        variant="text"
                        width="60%"
                        sx={{
                            fontSize: "1.75rem",
                            bgcolor: variant === "featured" ? "rgba(255,255,255,0.12)" : undefined
                        }}
                    />
                ) : typeof value === "number" && Number.isFinite(value) ? (
                    <AnimatedNumber value={value} format={format} />
                ) : (
                    value
                )}
            </div>

            {hint && <p className="stat-card__hint">{hint}</p>}
        </article>
    );
}
