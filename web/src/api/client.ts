import createClient, { type Middleware } from "openapi-fetch";
import type { paths } from "./schema";
import { clearToken, readToken } from "@/auth/token";
import { recordQuery } from "@/sql/queryLog";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

/** A failed request, carrying the RFC 9457 fields the API sends. */
export class ApiError extends Error {
  readonly status: number;
  readonly type: string;

  constructor(status: number, type: string, detail: string) {
    super(detail);
    this.status = status;
    this.type = type;
  }

  /** The last part of urn:sevak:error:slot-taken. */
  get code(): string {
    return this.type.split(":").pop() ?? "";
  }
}

const screens = new Map<string, string>();

const session: Middleware = {
  onRequest({ request, id }) {
    const token = readToken();
    if (token) request.headers.set("Authorization", `Bearer ${token}`);
    // Remember the screen now: the user may navigate away before the response arrives.
    screens.set(id, window.location.pathname);
    return request;
  },
  onResponse({ request, response, id }) {
    const key = response.headers.get("X-Sevak-Query");
    if (key) recordQuery(screens.get(id) ?? window.location.pathname, key);
    screens.delete(id);

    // An expired token ends the session. A wrong password on /auth/login is not that.
    if (response.status === 401 && !request.url.includes("/auth/")) {
      clearToken();
      window.dispatchEvent(new Event("sevak:signed-out"));
    }
    return response;
  },
};

export const api = createClient<paths>({ baseUrl: API_URL });
api.use(session);

type Result<T> = { data?: T; error?: unknown; response: Response };

/** Returns the body, or throws an ApiError holding the message to show the user. */
export async function call<T>(request: Promise<Result<T>>): Promise<T> {
  let result: Result<T>;
  try {
    result = await request;
  } catch {
    throw new ApiError(0, "urn:sevak:error:network", "We can't reach the server. Check your connection and try again.");
  }
  if (result.response.ok) return result.data as T;
  const problem = (result.error ?? {}) as { type?: string; title?: string; detail?: string };
  throw new ApiError(
    result.response.status,
    problem.type ?? "",
    problem.detail ?? problem.title ?? "Something went wrong. Please try again.",
  );
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "Something went wrong. Please try again.";
}
