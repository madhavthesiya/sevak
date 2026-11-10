import { StrictMode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router";
import { Toaster } from "sonner";
import { ApiError } from "@/api/client";
import { SessionProvider } from "@/auth/session";
import { SqlDrawerProvider } from "@/sql/SqlDrawer";
import App from "./App";
import "./index.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      // A 4xx will fail the same way again, so only retry when the network or server hiccups.
      retry: (failures, error) => failures < 2 && (!(error instanceof ApiError) || error.status === 0 || error.status >= 500),
    },
  },
});

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <SessionProvider>
          <SqlDrawerProvider>
            <App />
            <Toaster position="bottom-center" toastOptions={{ className: "!rounded-sm !font-sans" }} />
          </SqlDrawerProvider>
        </SessionProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
