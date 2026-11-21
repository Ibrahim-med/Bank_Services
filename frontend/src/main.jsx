import React from "react";
import ReactDOM from "react-dom/client";

import App from "./App";
import ThemeModeProvider from "./context/ThemeModeProvider";
import ToastProvider from "./context/ToastProvider";
import "./index.css";
import "./styles/design-tokens.css";
import "./motion.css";
/* Last: the redesign layer restyles classes from App.css and motion.css. */
import "./styles/app-redesign.css";

ReactDOM.createRoot(document.getElementById("root")).render(
    <React.StrictMode>
        <ThemeModeProvider>
            <ToastProvider>
                <App />
            </ToastProvider>
        </ThemeModeProvider>
    </React.StrictMode>
);
