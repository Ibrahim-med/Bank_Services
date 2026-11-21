import { useEffect, useState } from "react";

/*
 * Scroll-spy: returns the id of the section currently crossing the
 * upper part of the viewport (or "" above the first one).
 * Uses IntersectionObserver, so no work happens on every scroll event.
 */
export default function useActiveSection(ids) {
    const [active, setActive] = useState("");
    const key = ids.join("|");

    useEffect(() => {
        if (typeof window === "undefined" || !("IntersectionObserver" in window)) return undefined;

        const sections = key.split("|")
            .map((id) => document.getElementById(id))
            .filter(Boolean);

        const visible = new Map();

        const observer = new IntersectionObserver(
            (entries) => {
                entries.forEach((entry) => visible.set(entry.target.id, entry.isIntersecting));
                // First section (in page order) intersecting the detection band.
                const current = sections.find((section) => visible.get(section.id));
                setActive(current ? current.id : "");
            },
            // Detection band: from 30% to 45% of the viewport height.
            { rootMargin: "-30% 0px -55% 0px", threshold: 0 }
        );

        sections.forEach((section) => observer.observe(section));

        return () => observer.disconnect();
    }, [key]);

    return active;
}
