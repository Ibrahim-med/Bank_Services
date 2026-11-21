/* Signed-in session stored by the Login page. */

export const SESSION_KEYS = ["token", "username", "role", "idClient"];

export function readSession() {
    return {
        username: localStorage.getItem("username") || "",
        role: localStorage.getItem("role") || "",
        idClient: localStorage.getItem("idClient") || ""
    };
}

/* Removes the session (sign out). The theme preference is kept. */
export function clearSession() {
    SESSION_KEYS.forEach((key) => localStorage.removeItem(key));
}
