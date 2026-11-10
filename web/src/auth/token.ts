// The login token lives in localStorage so a page refresh or a new tab stays signed in. The trade-off is
// that any script on the page could read it, so the app renders no user HTML and loads no third-party script.
const KEY = "sevak.token";

function expiresAt(token: string): number {
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return Number(JSON.parse(atob(payload)).exp) * 1000;
  } catch {
    return 0;
  }
}

function storage(): Storage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

export function readToken(): string | null {
  const token = storage()?.getItem(KEY) ?? null;
  if (token && expiresAt(token) <= Date.now()) {
    clearToken();
    return null;
  }
  return token;
}

export function saveToken(token: string) {
  storage()?.setItem(KEY, token);
}

export function clearToken() {
  storage()?.removeItem(KEY);
}
