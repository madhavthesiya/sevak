import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Navigate, useLocation, useNavigate } from "react-router";
import { fetchers, type Me } from "@/api/endpoints";
import { clearToken, readToken, saveToken } from "./token";

export type Role = "customer" | "provider" | "admin";

/** The account for each one-click login. The README publishes these, and they hold demo data only. */
export const DEMO_ACCOUNTS: Record<Role, string> = {
  customer: "aarav.sharma@gmail.com",
  provider: "ravi.electrician@gmail.com",
  admin: "admin.raj@sevak.com",
};
export const DEMO_PASSWORD = "Demo@1234";

export const HOME_FOR_ROLE: Record<Role, string> = {
  customer: "/bookings",
  provider: "/provider/jobs",
  admin: "/admin/revenue",
};

type Session = {
  me: Me | undefined;
  role: Role | undefined;
  loading: boolean;
  signIn: (token: string) => Promise<Me>;
  signInAsDemo: (role: Role) => Promise<Me>;
  signOut: () => void;
};

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const client = useQueryClient();
  const navigate = useNavigate();
  const [hasToken, setHasToken] = useState(() => readToken() !== null);

  const meQuery = useQuery({ queryKey: ["me"], queryFn: fetchers.me, enabled: hasToken, staleTime: Infinity, retry: false });

  const signOut = useCallback(() => {
    clearToken();
    setHasToken(false);
    client.clear(); // one account's cached data must never appear for the next
    navigate("/");
  }, [client, navigate]);

  useEffect(() => {
    const expired = () => {
      setHasToken(false);
      client.clear();
      navigate("/login?expired=1");
    };
    window.addEventListener("sevak:signed-out", expired);
    return () => window.removeEventListener("sevak:signed-out", expired);
  }, [client, navigate]);

  const signIn = useCallback(
    async (token: string) => {
      saveToken(token);
      client.clear();
      const me = await client.fetchQuery({ queryKey: ["me"], queryFn: fetchers.me, staleTime: Infinity });
      setHasToken(true);
      return me;
    },
    [client],
  );

  const signInAsDemo = useCallback(
    async (role: Role) => {
      const { accessToken } = await fetchers.login({ email: DEMO_ACCOUNTS[role], password: DEMO_PASSWORD });
      return signIn(accessToken);
    },
    [signIn],
  );

  const value = useMemo<Session>(
    () => ({
      me: hasToken ? meQuery.data : undefined,
      role: hasToken ? (meQuery.data?.role as Role | undefined) : undefined,
      loading: hasToken && meQuery.isPending,
      signIn,
      signInAsDemo,
      signOut,
    }),
    [hasToken, meQuery.data, meQuery.isPending, signIn, signInAsDemo, signOut],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession() {
  const value = useContext(SessionContext);
  if (!value) throw new Error("useSession must be used inside SessionProvider");
  return value;
}

/** Sends guests to log in, and sends the wrong role to their own home. The API still enforces every rule. */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const session = useSession();
  const location = useLocation();
  if (session.loading) return <div className="wrap py-16" aria-busy="true" />;
  if (!session.role) {
    return <Navigate to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`} replace />;
  }
  if (session.role !== role) return <Navigate to={HOME_FOR_ROLE[session.role]} replace />;
  return <>{children}</>;
}
