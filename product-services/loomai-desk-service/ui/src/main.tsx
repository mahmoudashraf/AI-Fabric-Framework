import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { App } from "./App";
import "./styles.css";

// Email-return credentials are discarded, never used as a Desk session or permission.
function discardEmailReturnCredentials() {
  const fragment = new URLSearchParams(window.location.hash.slice(1));
  const returned =
    fragment.has("access_token") || fragment.has("refresh_token");
  if (returned) {
    window.history.replaceState(
      window.history.state,
      "",
      `${window.location.pathname}${window.location.search}`,
    );
  }
  return returned;
}
const emailReturn = discardEmailReturnCredentials();

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
      refetchOnWindowFocus: true,
      staleTime: 2000,
      gcTime: 60_000,
    },
    mutations: { retry: false },
  },
});
ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <App emailReturn={emailReturn} />
    </QueryClientProvider>
  </React.StrictMode>,
);
