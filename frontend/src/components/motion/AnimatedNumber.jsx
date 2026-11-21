import useCountUp from "../../hooks/useCountUp";

/*
 * Counts up to `value` and renders it with `format`.
 * Screen readers get the final value only, never the
 * intermediate frames.
 */
export default function AnimatedNumber({ value, format = String, duration }) {
    const current = useCountUp(Number(value), duration);

    return (
        <>
            <span aria-hidden="true">{format(current)}</span>
            <span className="sr-only">{format(value)}</span>
        </>
    );
}
