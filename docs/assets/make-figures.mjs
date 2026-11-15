// Draws the README's figures as light and dark SVGs, in GitHub's palette with SEVAK's green.
// Every number comes from docs/BENCHMARKS.md or the test suite; change them there first.
//
//   node docs/assets/make-figures.mjs
//
// Writes stats-, results- and architecture-{light,dark}.svg next to this file.

import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const OUT = dirname(fileURLToPath(import.meta.url));
const FONT = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif";
const MONO = "ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace";

const THEMES = {
  light: {
    text: "#1f2328", muted: "#59636e", line: "#d1d9e0", faint: "#eff2f5", card: "#f6f8fa",
    accent: "#0a6b46", accentSoft: "#dff3e8", before: "#8c959f", yellow: "#d4a106",
  },
  dark: {
    text: "#f0f6fc", muted: "#9198a1", line: "#3d444d", faint: "#21262d", card: "#151b23",
    accent: "#3fb983", accentSoft: "#12352a", before: "#6e7681", yellow: "#f2c94c",
  },
};

const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
const text = (x, y, s, { size = 13, fill, weight = 400, anchor = "start", font = FONT } = {}) =>
  `<text x="${x}" y="${y}" fill="${fill}" font-size="${size}" font-weight="${weight}" text-anchor="${anchor}" font-family="${font}">${esc(s)}</text>`;

// ── 1. Four numbers ─────────────────────────────────────────────────────────────────
const STATS = [
  { big: "8 ms", lines: ["home page at 1M bookings,", "down from 2,334 ms"] },
  { big: "271", lines: ["backend tests against a", "real PostgreSQL"] },
  { big: "16", lines: ["tables under row-level", "security, +0.3–0.8 ms a page"] },
  { big: "0", lines: ["double bookings: a constraint", "refuses them, even under load"] },
];

function stats(t) {
  const w = 880, h = 118, col = w / STATS.length;
  let body = "";
  STATS.forEach((s, i) => {
    const cx = col * i + col / 2;
    body += text(cx, 50, s.big, { size: 34, fill: i === 0 ? t.accent : t.text, weight: 650, anchor: "middle" });
    s.lines.forEach((line, j) => (body += text(cx, 78 + j * 18, line, { fill: t.muted, anchor: "middle" })));
    if (i > 0) body += `<line x1="${col * i}" y1="18" x2="${col * i}" y2="100" stroke="${t.line}"/>`;
  });
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" role="img"><title>SEVAK in four numbers</title>${body}</svg>`;
}

// ── 2. Before and after, on a log scale ─────────────────────────────────────────────
const RESULTS = [
  { name: "Admin revenue chart", how: "stored summary, refreshed every 15 min", before: 4682, after: 0.019 },
  { name: "Home page, highest rated", how: "read the stored leaderboard", before: 2334, after: 8.0 },
  { name: "Customer spending summary", how: "total only this customer's payments", before: 1286, after: 0.74, phase: 8 },
  { name: "Admin bookings, page 5,000", how: "an index for the keyset", before: 514, after: 2.4 },
  { name: "Search", how: "rank by the stored score", before: 39.0, after: 2.4 },
  { name: "Live leaderboard", how: "count each provider's jobs first", before: 3041, after: 452 },
];

const fmtMs = (v) =>
  v >= 1000 ? `${v.toLocaleString("en-US")} ms` : v >= 10 ? `${+v.toFixed(1)} ms` : `${+v.toPrecision(2)} ms`;
const fmtX = (r) => {
  const x = r.before / r.after;
  if (x >= 10000) return `${(Math.round(x / 1000) * 1000).toLocaleString("en-US")}×`;
  if (x >= 100) return `${Math.round(x).toLocaleString("en-US")}×`;
  if (x >= 10) return `${Math.round(x)}×`;
  return `${x.toFixed(1)}×`;
};

function results(t) {
  const w = 880, top = 46, rowH = 50, left = 290, right = 770;
  const h = top + RESULTS.length * rowH + 44;
  const lo = Math.log10(0.01), hi = Math.log10(10000);
  const X = (v) => left + ((Math.log10(v) - lo) / (hi - lo)) * (right - left);
  let body = text(0, 22, "Median query time on 1M bookings, before and after (log scale)", { size: 14, fill: t.text, weight: 600 });
  body += `<circle cx="${w - 236}" cy="17" r="5" fill="${t.before}"/>` + text(w - 226, 22, "before", { fill: t.muted, size: 12 });
  body += `<circle cx="${w - 170}" cy="17" r="5" fill="${t.accent}"/>` + text(w - 160, 22, "after", { fill: t.muted, size: 12 });
  body += text(w, 22, "faster", { fill: t.muted, size: 12, anchor: "end" });
  for (const tick of [0.01, 0.1, 1, 10, 100, 1000, 10000]) {
    const x = X(tick);
    body += `<line x1="${x}" y1="${top - 6}" x2="${x}" y2="${top + RESULTS.length * rowH - 6}" stroke="${t.faint}"/>`;
    const label = tick >= 1000 ? `${tick / 1000} s` : `${tick} ms`;
    body += text(x, top + RESULTS.length * rowH + 14, label, { fill: t.muted, size: 11, anchor: "middle" });
  }
  RESULTS.forEach((r, i) => {
    const y = top + i * rowH + 18;
    body += text(0, y - 3, r.name, { size: 13, fill: t.text, weight: 600 });
    body += text(0, y + 14, r.how + (r.phase ? " (Phase 8)" : ""), { size: 11.5, fill: t.muted });
    const xb = X(r.before), xa = X(r.after);
    body += `<line x1="${xa}" y1="${y + 2}" x2="${xb}" y2="${y + 2}" stroke="${t.line}" stroke-width="3" stroke-linecap="round"/>`;
    body += `<circle cx="${xb}" cy="${y + 2}" r="6" fill="${t.before}"/>`;
    body += `<circle cx="${xa}" cy="${y + 2}" r="6.5" fill="${t.accent}"/>`;
    body += text(xb + 10, y + 6, fmtMs(r.before), { size: 11, fill: t.muted });
    body += text(xa - 10, y + 6, fmtMs(r.after), { size: 11, fill: t.accent, weight: 600, anchor: "end" });
    body += text(w, y + 6, fmtX(r), { size: 14, fill: t.text, weight: 650, anchor: "end" });
  });
  body += text(0, h - 6, "Each pair returns identical rows. Phase 6 run, except where marked; EXPLAIN ANALYZE, median of 3.", { size: 11, fill: t.muted });
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" role="img"><title>Before and after query times on 1M bookings</title>${body}</svg>`;
}

// ── 3. Who connects as what ─────────────────────────────────────────────────────────
function box(t, x, y, w, h, title, sub, lines, { accent = false } = {}) {
  let s = `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="10" fill="${accent ? t.accentSoft : t.card}" stroke="${accent ? t.accent : t.line}" stroke-width="${accent ? 1.5 : 1}"/>`;
  s += text(x + 16, y + 26, title, { size: 14, fill: t.text, weight: 650 });
  s += text(x + 16, y + 44, sub, { size: 11.5, fill: t.muted });
  lines.forEach((line, i) => {
    s += `<circle cx="${x + 20}" cy="${y + 66 + i * 20}" r="2.5" fill="${accent ? t.accent : t.muted}"/>`;
    s += text(x + 30, y + 70 + i * 20, line, { size: 12, fill: t.text });
  });
  return s;
}

function arrow(t, x1, y1, x2, y2, label, sub, { dashed = false, color } = {}) {
  const c = color ?? t.muted;
  const id = `a${Math.round(x1)}${Math.round(y1)}${Math.round(x2)}${Math.round(y2)}`;
  let s = `<defs><marker id="${id}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="${c}"/></marker></defs>`;
  s += `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${c}" stroke-width="1.6"${dashed ? ' stroke-dasharray="5 4"' : ""} marker-end="url(#${id})"/>`;
  const mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
  if (label) s += text(mx, my - 8, label, { size: 11.5, fill: t.text, weight: 600, anchor: "middle" });
  if (sub) s += text(mx, my + 17, sub, { size: 11, fill: t.muted, anchor: "middle" });
  return s;
}

function architecture(t) {
  const w = 880, h = 372;
  let s = "";
  s += box(t, 0, 20, 190, 150, "Web app", "React · Vercel", ["Generated API types", "“Show the SQL” drawer", "One-click demo logins"]);
  s += box(t, 300, 20, 250, 150, "API", "Spring Boot · Render", ["Rate limit → JWT → services", "Hand-written SQL (JdbcClient)", "Each transaction sets the caller"]);
  s += box(t, 660, 20, 220, 310, "PostgreSQL", "Supabase · Singapore", ["Row-level security, 16 tables", "Exclusion constraint", "Status rules as data", "Deferred subtotal check", "Materialized views", "SECURITY DEFINER windows"], { accent: true });
  s += box(t, 300, 220, 250, 110, "Owner work", "Flyway and two jobs", ["Migrations, demo reset", "Refresh the stored summaries"]);
  s += arrow(t, 192, 95, 297, 95, "HTTPS + JWT", "");
  s += arrow(t, 552, 95, 657, 95, "as sevak_app", "under the policies", { color: t.accent });
  s += arrow(t, 552, 275, 657, 275, "as the owner", "BYPASSRLS", { dashed: true });
  s += text(0, h - 8, "The API refuses to start if its role, its login or the tables' owner would break row-level security.", { size: 11, fill: t.muted });
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" role="img"><title>How SEVAK connects</title>${s}</svg>`;
}

for (const [name, t] of Object.entries(THEMES)) {
  writeFileSync(join(OUT, `stats-${name}.svg`), stats(t) + "\n");
  writeFileSync(join(OUT, `results-${name}.svg`), results(t) + "\n");
  writeFileSync(join(OUT, `architecture-${name}.svg`), architecture(t) + "\n");
}
console.log("Wrote stats, results and architecture figures (light and dark) to " + OUT);
