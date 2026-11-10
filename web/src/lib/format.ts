// Every amount and date on screen goes through here, so India formatting stays consistent.

const TIME_ZONE = "Asia/Kolkata";

const money = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" });
const wholeRupees = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

/** ₹1,500.00 */
export function formatMoney(amount: number | null | undefined): string {
  return money.format(amount ?? 0);
}

/** ₹1,500, for rate cards and chart axes where paise would be noise. */
export function formatRupees(amount: number | null | undefined): string {
  return wholeRupees.format(amount ?? 0);
}

function parts(value: string | Date, options: Intl.DateTimeFormatOptions) {
  const found: Record<string, string> = {};
  for (const part of new Intl.DateTimeFormat("en-GB", { timeZone: TIME_ZONE, ...options }).formatToParts(
    new Date(value),
  )) {
    found[part.type] = part.value;
  }
  return found;
}

/** Sat, 5 Apr */
export function formatDay(value: string | Date): string {
  const p = parts(value, { weekday: "short", day: "numeric", month: "short" });
  return `${p.weekday}, ${p.day} ${p.month}`;
}

/** 10:00 AM */
export function formatTime(value: string | Date): string {
  const p = parts(value, { hour: "numeric", minute: "2-digit", hour12: true });
  return `${Number(p.hour)}:${p.minute} ${(p.dayPeriod ?? "").toUpperCase()}`;
}

/** Sat, 5 Apr · 10:00 AM */
export function formatDateTime(value: string | Date): string {
  return `${formatDay(value)} · ${formatTime(value)}`;
}

/** The YYYY-MM-DD date in India, which can differ from the browser's own date. */
export function indiaDate(value: string | Date = new Date()): string {
  const p = parts(value, { year: "numeric", month: "2-digit", day: "2-digit" });
  return `${p.year}-${p.month}-${p.day}`;
}

/** The date and short month used on ticket stubs. */
export function ticketDate(value: string | Date): { day: string; month: string; weekday: string } {
  const p = parts(value, { weekday: "short", day: "numeric", month: "short" });
  return { day: p.day, month: p.month, weekday: p.weekday };
}

/** "2026-04" becomes "Apr 26". */
export function formatMonth(month: string): string {
  const [year, m] = month.split("-");
  const name = new Intl.DateTimeFormat("en-GB", { month: "short", timeZone: "UTC" }).format(
    new Date(Date.UTC(Number(year), Number(m) - 1, 1)),
  );
  return `${name} ${year.slice(2)}`;
}

/** 90 becomes "1 hr 30 min". */
export function formatMinutes(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return `${rest} min`;
  return rest === 0 ? `${hours} hr` : `${hours} hr ${rest} min`;
}

/** "14:00:00" becomes "2:00 PM". */
export function formatClock(time: string): string {
  const [h, m] = time.split(":").map(Number);
  const suffix = h >= 12 ? "PM" : "AM";
  return `${h % 12 === 0 ? 12 : h % 12}:${String(m).padStart(2, "0")} ${suffix}`;
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0].toUpperCase())
    .join("");
}

/** Booking numbers are printed like the serial on a bill book. */
export function billNumber(id: number): string {
  return String(id).padStart(5, "0");
}

export const DAY_NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
