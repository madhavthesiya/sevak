import { useSyncExternalStore } from "react";

// Which queries powered which screen, filled from each response's X-Sevak-Query header.
// Kept per screen for the whole visit, so a screen served from cache still knows its queries.
const byScreen = new Map<string, string[]>();
const EMPTY: string[] = [];
const listeners = new Set<() => void>();

export function recordQuery(screen: string, key: string) {
  const keys = byScreen.get(screen) ?? EMPTY;
  if (keys.includes(key)) return;
  byScreen.set(screen, [...keys, key]);
  listeners.forEach((listener) => listener());
}

export function useScreenQueries(screen: string): string[] {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => byScreen.get(screen) ?? EMPTY,
  );
}
