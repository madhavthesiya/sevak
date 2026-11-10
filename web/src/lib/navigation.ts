/** Only same-site paths, so a crafted ?next= link can't send someone to another website after login. */
export function safeNext(next: string | null): string | null {
  return next && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/\\") ? next : null;
}
