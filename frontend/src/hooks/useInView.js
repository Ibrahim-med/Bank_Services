import { useEffect, useRef, useState } from "react";

/*
 * Reports when an element first scrolls into view (fires once).
 * Falls back to "visible" when IntersectionObserver is missing.
 */
export default function useInView({ rootMargin = "0px 0px -10% 0px", threshold = 0.12 } = {}) {
    const ref = useRef(null);
    const [inView, setInView] = useState(
        () => typeof window !== "undefined" && !("IntersectionObserver" in window)
    );

    useEffect(() => {
        const node = ref.current;

        if (!node || inView) return undefined;

        const observer = new IntersectionObserver(
            ([entry]) => {
                if (entry.isIntersecting) {
                    setInView(true);
                    observer.disconnect();
                }
            },
            { rootMargin, threshold }
        );

        observer.observe(node);

        return () => observer.disconnect();
    }, [inView, rootMargin, threshold]);

    return [ref, inView];
}
