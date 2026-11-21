import { useSyncExternalStore } from "react";

const QUERY = "(prefers-reduced-motion: reduce)";

function subscribe(callback) {
    const media = window.matchMedia(QUERY);
    media.addEventListener("change", callback);
    return () => media.removeEventListener("change", callback);
}

export function prefersReducedMotion() {
    return typeof window !== "undefined" && window.matchMedia(QUERY).matches;
}

/* True when the operating system asks for reduced motion. Updates live. */
export default function usePrefersReducedMotion() {
    return useSyncExternalStore(subscribe, prefersReducedMotion, () => false);
}
