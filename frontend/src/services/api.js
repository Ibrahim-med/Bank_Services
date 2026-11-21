
import axios from "axios";

import { parseRetryAfter, rateLimitMessage } from "../utils/rateLimit";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api",
  headers: {
    "Content-Type": "application/json"
  },
  timeout: 15000
});

// Add JWT token to requests when available.
api.interceptors.request.use(
  config => {
    const token = localStorage.getItem("token");

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  error => Promise.reject(error)
);

// Handle API errors.
api.interceptors.response.use(
  response => response,
  error => {
    const isLoginRequest =
      error.config?.url?.includes("/auth/login");

    // Too many requests (HTTP 429): pages display response.data.message,
    // so replace it with a clear message that includes the waiting time.
    // The limits themselves are enforced by the backend.
    if (error.response?.status === 429) {
      const retryAfterSeconds = parseRetryAfter(
        error.response.headers?.["retry-after"]
      );

      error.rateLimited = true;
      error.retryAfterSeconds = retryAfterSeconds;
      error.response.data = {
        ...(error.response.data && typeof error.response.data === "object"
          ? error.response.data
          : {}),
        message: rateLimitMessage(retryAfterSeconds)
      };
    }

    // Do not clear the session for an incorrect login attempt.
    if (error.response?.status === 401 && !isLoginRequest) {
      localStorage.removeItem("token");
      localStorage.removeItem("username");
      localStorage.removeItem("role");
      localStorage.removeItem("idClient");

      if (window.location.pathname !== "/login") {
        window.location.assign("/login");
      }
    }

    if (!error.response) {
      console.error(
        "Network or CORS error:",
        error.message
      );
    }

    return Promise.reject(error);
  }
);

export default api;
