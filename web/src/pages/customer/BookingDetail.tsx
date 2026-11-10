import { useState, type CSSProperties, type FormEvent, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Star } from "lucide-react";
import { Link, useParams } from "react-router";
import { toast } from "sonner";
import { errorMessage } from "@/api/client";
import { fetchers, type BookingDetail as Detail, type StatusChange } from "@/api/endpoints";
import { ErrorNote, RowSkeletons, StatusPill, statusLabel } from "@/components/common";
import { SelectField, TextAreaField } from "@/components/form";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { billNumber, formatDateTime, formatMoney, formatTime } from "@/lib/format";
import { cn } from "@/lib/utils";

const TWO_HOURS = 2 * 60 * 60 * 1000;

/** A route line: one stop for each row of booking_status_log. */
function Timeline({ rows }: { rows: StatusChange[] }) {
  return (
    <ol className="grid">
      {rows.map((row, i) => {
        const latest = i === rows.length - 1;
        return (
          <li key={i} className="relative grid gap-0.5 pb-6 pl-7 last:pb-0">
            {!latest && <span aria-hidden className="absolute top-2 left-[6px] h-full w-0.5 bg-input" />}
            <span
              aria-hidden
              className={cn("absolute top-1.5 left-0 size-3.5 border-2 border-auto-green", latest ? "bg-auto-green" : "bg-card")}
            />
            <span className="font-semibold">{statusLabel(row.newStatus)}</span>
            <span className="text-sm text-ink-soft">{formatDateTime(row.changedAt)}</span>
            {row.remarks && <span className="text-sm">{row.remarks}</span>}
          </li>
        );
      })}
    </ol>
  );
}

function ActionDialog({
  label,
  title,
  description,
  variant,
  children,
}: {
  label: string;
  title: string;
  description: string;
  variant?: "default" | "outline";
  children: (close: () => void) => ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant={variant}>{label}</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogTitle className="text-3xl">{title}</DialogTitle>
        <DialogDescription>{description}</DialogDescription>
        {children(() => setOpen(false))}
      </DialogContent>
    </Dialog>
  );
}

function useAction(id: number, run: () => Promise<unknown>, done: string, close: () => void) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: run,
    onSuccess: () => {
      toast.success(done);
      client.invalidateQueries({ queryKey: ["booking", id] });
      client.invalidateQueries({ queryKey: ["bookings"] });
      client.invalidateQueries({ queryKey: ["slots"] });
      close();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
}

function PayForm({ id, amount, close }: { id: number; amount: number; close: () => void }) {
  const [method, setMethod] = useState("UPI");
  const [fail, setFail] = useState(false);
  const pay = useAction(
    id,
    () => fetchers.pay(id, { paymentMethod: method, simulate: fail ? "failure" : "success" }),
    fail ? "The simulated payment failed." : "Payment received.",
    close,
  );
  return (
    <form
      className="grid gap-4"
      onSubmit={(e: FormEvent) => {
        e.preventDefault();
        pay.mutate();
      }}
    >
      <SelectField label="Pay with" value={method} onChange={(e) => setMethod(e.target.value)}>
        {["UPI", "Credit Card", "Debit Card", "Net Banking"].map((m) => (
          <option key={m}>{m}</option>
        ))}
      </SelectField>
      <label className="flex items-center gap-2">
        <input type="checkbox" className="size-4 accent-[var(--auto-green)]" checked={fail} onChange={(e) => setFail(e.target.checked)} />
        Simulate a failed payment
      </label>
      <Button type="submit" disabled={pay.isPending}>
        {pay.isPending ? "Paying…" : `Pay ${formatMoney(amount)}`}
      </Button>
    </form>
  );
}

function CancelForm({ id, close }: { id: number; close: () => void }) {
  const [reason, setReason] = useState("");
  const cancel = useAction(id, () => fetchers.cancel(id, reason.trim()), "Booking cancelled.", close);
  return (
    <form
      className="grid gap-4"
      onSubmit={(e: FormEvent) => {
        e.preventDefault();
        cancel.mutate();
      }}
    >
      <TextAreaField label="Why are you cancelling?" rows={2} required value={reason} onChange={(e) => setReason(e.target.value)} />
      <Button type="submit" variant="destructive" disabled={!reason.trim() || cancel.isPending}>
        {cancel.isPending ? "Cancelling…" : "Cancel this booking"}
      </Button>
    </form>
  );
}

function ReviewForm({ id, close }: { id: number; close: () => void }) {
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const review = useAction(id, () => fetchers.review(id, { rating, comment: comment.trim() || undefined }), "Thanks for your review.", close);
  return (
    <form
      className="grid gap-4"
      onSubmit={(e: FormEvent) => {
        e.preventDefault();
        review.mutate();
      }}
    >
      <fieldset>
        <legend className="mb-1 text-sm font-medium">Your rating</legend>
        <div className="flex gap-1">
          {[1, 2, 3, 4, 5].map((n) => (
            <label key={n} className="cursor-pointer rounded-sm has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-ring">
              <input type="radio" name="rating" className="sr-only" aria-label={`${n} out of 5`} checked={rating === n} onChange={() => setRating(n)} />
              <Star aria-hidden className={cn("size-9 text-auto-yellow-deep", n <= rating && "fill-auto-yellow-deep")} />
            </label>
          ))}
        </div>
      </fieldset>
      <TextAreaField label="Comment (optional)" rows={3} value={comment} onChange={(e) => setComment(e.target.value)} />
      <Button type="submit" disabled={rating === 0 || review.isPending}>
        {review.isPending ? "Sending…" : "Post review"}
      </Button>
    </form>
  );
}

function Actions({ detail, now }: { detail: Detail; now: number }) {
  const b = detail.booking;
  const id = b.bookingId;
  const open = b.status === "pending" || b.status === "confirmed";
  const settled = b.paymentStatus === "success" || b.paymentStatus === "refunded";
  const canPay = !settled && !["cancelled", "expired", "pending"].includes(b.status);
  const canReview = b.status === "completed" && !b.reviewed;
  const tooLate = new Date(b.scheduledStart).getTime() - now < TWO_HOURS; // the API makes the final decision

  if (!canPay && !open && !canReview) return null;
  return (
    <div className="grid gap-2">
      <div className="flex flex-wrap gap-2">
        {canPay && (
          <ActionDialog label="Pay now" title="Pay for this booking" description="This is a simulated payment. No money moves.">
            {(close) => <PayForm id={id} amount={b.totalAmount} close={close} />}
          </ActionDialog>
        )}
        {canReview && (
          <ActionDialog label="Write a review" title={`Rate ${b.providerName}`} description="Your review appears on their profile.">
            {(close) => <ReviewForm id={id} close={close} />}
          </ActionDialog>
        )}
        {open &&
          (tooLate ? (
            <Button variant="outline" disabled>
              Cancel
            </Button>
          ) : (
            <ActionDialog label="Cancel" variant="outline" title="Cancel this booking?" description="Any payment you made will be refunded.">
              {(close) => <CancelForm id={id} close={close} />}
            </ActionDialog>
          ))}
      </div>
      {open && tooLate && <p className="text-sm text-ink-soft">Bookings can't be cancelled within 2 hours of the start.</p>}
    </div>
  );
}

export default function BookingDetail() {
  const id = Number(useParams().id);
  const query = useQuery({ queryKey: ["booking", id], queryFn: () => fetchers.myBooking(id) });

  if (query.isPending) {
    return (
      <div className="wrap py-10">
        <RowSkeletons rows={3} className="h-32" />
      </div>
    );
  }
  if (query.isError) {
    return (
      <div className="wrap py-10">
        <ErrorNote error={query.error} onRetry={() => query.refetch()} />
      </div>
    );
  }

  const { booking: b, items, timeline } = query.data;
  return (
    <div className="wrap grid gap-6 py-8 md:py-12">
      <Link to="/bookings" className="inline-flex items-center gap-1 text-sm font-medium">
        <ArrowLeft className="size-4" aria-hidden /> My bookings
      </Link>

      <div className="grid items-start gap-8 lg:grid-cols-[1.4fr_1fr]">
        <article className="ticket grid gap-5 p-5 md:p-7" style={{ "--tear": "calc(100% - 180px)" } as CSSProperties}>
          <header className="flex flex-wrap items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="label-caps serial">Bill no. {billNumber(b.bookingId)}</p>
              <h1 className="text-5xl">{b.providerName}</h1>
              <p className="mt-1">
                {formatDateTime(b.scheduledStart)} to {formatTime(b.scheduledEnd)}
              </p>
            </div>
            <StatusPill status={b.status} />
          </header>

          <p>
            <span className="label-caps block">Address</span>
            {[b.houseNo, b.street, b.areaName, b.cityName].filter(Boolean).join(", ")}
          </p>
          {b.specialInstructions && (
            <p>
              <span className="label-caps block">Your notes</span>
              {b.specialInstructions}
            </p>
          )}

          <ul className="mt-5 grid gap-1.5 border-t border-dashed pt-5">
            {items.map((line) => (
              <li key={line.itemNo} className="flex items-baseline gap-2">
                <span className="min-w-0">
                  {line.serviceName}
                  {line.variantName && ` (${line.variantName})`}
                  {line.quantity > 1 && ` ×${line.quantity}`}
                </span>
                <span className="leader" aria-hidden />
                <span className="figures whitespace-nowrap">{formatMoney(line.lineTotal)}</span>
              </li>
            ))}
          </ul>
          <dl className="grid gap-1">
            <div className="flex justify-between">
              <dt>Subtotal</dt>
              <dd className="figures">{formatMoney(b.subtotal)}</dd>
            </div>
            <div className="flex justify-between">
              <dt>Discount{b.couponCode && ` (${b.couponCode})`}</dt>
              <dd className="figures">−{formatMoney(b.discount)}</dd>
            </div>
            <div className="flex items-baseline justify-between">
              <dt className="font-display text-2xl font-bold">Total</dt>
              <dd className="figures text-xl font-bold">{formatMoney(b.totalAmount)}</dd>
            </div>
            <div className="flex justify-between text-sm text-ink-soft">
              <dt>Payment</dt>
              <dd className="capitalize">{b.paymentStatus ?? "Not paid yet"}</dd>
            </div>
          </dl>
          <Actions detail={query.data} now={query.dataUpdatedAt} />
        </article>

        <section aria-labelledby="timeline-heading" className="grid gap-4">
          <h2 id="timeline-heading" className="text-3xl">
            What's happened
          </h2>
          <Timeline rows={timeline} />
        </section>
      </div>
    </div>
  );
}
