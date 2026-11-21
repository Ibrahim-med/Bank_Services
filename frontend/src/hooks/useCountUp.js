import { useEffect, useRef, useState } from "react";

import usePrefersReducedMotion from "./usePrefersReducedMotion";

const easeOutCubic = (t) => 1 - Math.pow(1 - t, 3);

/*
 * Animates a number from its previous value to `target`.
 * The first render starts from 0; later changes (e.g. after a
 * refresh) animate from the last displayed value. With reduced
 * motion the target is returned immediately.
 */
export default function useCountUp(target, duration = 800) {
    const reduced = usePrefersReducedMotion();
    const [display, setDisplay] = useState(0);
    const fromRef = useRef(0);
    const shownRef = useRef(0);
    const frameRef = useRef(0);

    useEffect(() => {
        if (reduced || !Number.isFinite(target)) {
            fromRef.current = target;
            shownRef.current = target;
            return undefined;
        }

        const from = fromRef.current;
        const start = performance.now();

        const tick = (now) => {
            const progress = Math.min(1, (now - start) / duration);
            const value = from + (target - from) * easeOutCubic(progress);

            shownRef.current = value;
            setDisplay(value);

            if (progress < 1) {
                frameRef.current = requestAnimationFrame(tick);
            }
        };

        frameRef.current = requestAnimationFrame(tick);

        return () => {
            cancelAnimationFrame(frameRef.current);
            // The next animation continues from the value on screen.
            fromRef.current = shownRef.current;
        };
    }, [target, duration, reduced]);

    return reduced || !Number.isFinite(target) ? target : display;
}
