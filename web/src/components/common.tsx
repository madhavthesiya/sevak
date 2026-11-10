import type { ReactNode } from "react";
import { BadgeCheck, Ban, CircleCheck, CircleDot, Clock3, Hammer, Star, TimerOff } from "lucide-react";
import { Link } from "react-router";
import { errorMessage } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { initials } from "@/lib/format";
import { cn } from "@/lib/utils";

const STATUS: Record<string, { label: string; icon: typeof Clock3; className: string }> = {
  pending: { label: "Waiting for reply", icon: Clock3, className: "bg-[#fff0c4] text-[#5e4500]" },
  confirmed: { label: "Confirmed", icon: CircleDot, className: "bg-[#dbe8ff] text-[#16407f]" },
  in_progress: { label: "In progress", icon: Hammer, className: "bg-[#cfeeea] text-[#0a4f4b]" },
  completed: { label: "Completed", icon: CircleCheck, className: "bg-[#d8efe0] text-[#124f30]" },
  cancelled: { label: "Cancelled", icon: Ban, className: "bg-[#efe3df] text-[#673f33]" },
  expired: { label: "Expired", icon: TimerOff, className: "bg-[#e5e7e4] text-[#464c47]" },
};

export function statusLabel(status: string) {
  return STATUS[status]?.label ?? status.replace(/_/g, " ");
}

export function StatusPill({ status }: { status: string }) {
  const s = STATUS[status] ?? STATUS.expired;
  const Icon = s.icon;
  return (
    <span className={cn("inline-flex items-center gap-1 rounded-sm px-2 py-0.5 text-[0.8rem] font-semibold", s.className)}>
      <Icon className="size-3.5" aria-hidden /> {statusLabel(status)}
    </span>
  );
}

export function Monogram({ name, size = "md" }: { name: string; size?: "md" | "lg" }) {
  return (
    <span
      aria-hidden
      className={cn(
        "grid shrink-0 place-items-center bg-bottle font-display font-bold text-auto-yellow",
        size === "lg" ? "size-20 text-4xl" : "size-12 text-xl",
      )}
      style={{ fontStretch: "78%" }}
    >
      {initials(name)}
    </span>
  );
}

export function Rating({ value, count }: { value: number | null | undefined; count?: number }) {
  if (value == null) return <span className="text-sm text-ink-soft">New on SEVAK</span>;
  return (
    <span className="inline-flex items-center gap-1 text-sm">
      <Star className="size-4 fill-auto-yellow-deep text-auto-yellow-deep" aria-hidden />
      <span className="figures font-semibold">{value.toFixed(1)}</span>
      {count !== undefined && (
        <span className="text-ink-soft">
          ({count} {count === 1 ? "review" : "reviews"})
        </span>
      )}
    </span>
  );
}

export function VerifiedMark() {
  return (
    <span className="inline-flex items-center gap-1 text-sm font-semibold text-auto-green">
      <BadgeCheck className="size-4" aria-hidden /> Verified
    </span>
  );
}

export function PageHeader({ title, intro, actions }: { title: string; intro?: string; actions?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4 pb-6">
      <div className="min-w-0">
        <h1 className="text-4xl md:text-5xl">{title}</h1>
        {intro && <p className="mt-2 max-w-[60ch] text-ink-soft">{intro}</p>}
      </div>
      {actions}
    </div>
  );
}

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: { to: string; label: string } }) {
  return (
    <div className="grid justify-items-start gap-2 border-2 border-dashed border-input/60 p-6 md:p-8">
      <h2 className="text-2xl">{title}</h2>
      {children && <p className="max-w-[55ch] text-ink-soft">{children}</p>}
      {action && (
        <Button asChild className="mt-2">
          <Link to={action.to}>{action.label}</Link>
        </Button>
      )}
    </div>
  );
}

export function ErrorNote({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div role="alert" className="grid justify-items-start gap-2 border-2 border-destructive/40 bg-card p-5">
      <p className="font-semibold">{errorMessage(error)}</p>
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}

export function RowSkeletons({ rows = 4, className = "h-20" }: { rows?: number; className?: string }) {
  return (
    <div className="grid gap-2" aria-busy="true" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className={className} />
      ))}
    </div>
  );
}

export function FieldError({ id, message }: { id?: string; message?: string }) {
  if (!message) return null;
  return (
    <p id={id} className="text-sm font-medium text-destructive">
      {message}
    </p>
  );
}
