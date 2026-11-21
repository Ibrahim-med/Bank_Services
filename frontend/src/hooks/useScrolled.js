import { useEffect, useState } from "react";

/*
 * True once the page has scrolled more than `offset` pixels.
 * Passive listener throttled to one check per animation frame;
 * the component only re-renders when the boolean flips.
 */
export default function useScrolled(offset = 16) {
    const [scrolled, setScrolled] = useState(
        () => typeof window !== "undefined" && window.scrollY > offset
    );

    useEffect(() => {
        let frame = 0;

        const update = () => {
            frame = 0;
            setScrolled(window.scrollY > offset);
        };

        const onScroll = () => {
            if (!frame) frame = requestAnimationFrame(update);
        };

        window.addEventListener("scroll", onScroll, { passive: true });
        update();

        return () => {
            window.removeEventListener("scroll", onScroll);
            cancelAnimationFrame(frame);
        };
    }, [offset]);

    return scrolled;
}
