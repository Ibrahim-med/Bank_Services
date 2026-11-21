import useInView from "../../hooks/useInView";

/*
 * Fades its content in when it scrolls into view (once).
 *   variant: "up" (default) | "left" | "right" | "scale"
 *   stagger: direct children enter one after another.
 * Content is always present in the DOM (only its opacity /
 * position animate), so nothing is hidden from assistive tech.
 */
export default function Reveal({
    as: Component = "div",
    delay = 0,
    stagger = false,
    variant = "up",
    className = "",
    style,
    children,
    ...rest
}) {
    const [ref, inView] = useInView();

    const classes = [
        "reveal",
        variant !== "up" ? `reveal--${variant}` : "",
        stagger ? "reveal--stagger" : "",
        inView ? "is-visible" : "",
        className
    ].filter(Boolean).join(" ");

    return (
        <Component
            ref={ref}
            className={classes}
            style={{ ...style, "--reveal-delay": `${delay}ms` }}
            {...rest}
        >
            {children}
        </Component>
    );
}
