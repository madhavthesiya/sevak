import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { fetchers, type BookingSummary } from "@/api/endpoints";
import { EmptyState, ErrorNote, PageHeader, RowSkeletons, StatusPill } from "@/components/common";
import { billNumber, formatMoney, formatTime, ticketDate } from "@/lib/format";

const OPEN = ["pending", "confirmed", "in_progress"];

function Stub({ booking }: { booking: BookingSummary }) {
  const date = ticketDate(booking.scheduledStart);
  return (
    <li>
      <Link to={`/bookings/${booking.bookingId}`} className="flex min-w-0 items-stretch bg-card hover:bg-white/70">
        <span className="grid w-20 shrink-0 place-content-center justify-items-center bg-bottle py-3 text-white">
          <span className="text-xs">{date.weekday}</span>
          <span className="font-display text-4xl leading-none font-bold" style={{ fontStretch: "78%" }}>
            {date.day}
          </span>
          <span className="text-xs">{date.month}</span>
        </span>
        <span className="grid min-w-0 flex-1 gap-1 p-3 md:grid-cols-[1fr_auto] md:items-center md:gap-4 md:px-5">
          <span className="grid min-w-0">
            <span className="truncate font-display text-xl font-bold" style={{ fontStretch: "85%" }}>
              {booking.providerName}
            </span>
            <span className="truncate text-sm text-ink-soft">
              {booking.services ? `${booking.services} · ` : ""}
              {formatTime(booking.scheduledStart)}
            </span>
          </span>
          <span className="flex flex-wrap items-center justify-between gap-3 md:justify-end">
            <StatusPill status={booking.status} />
            <span className="figures font-semibold">{formatMoney(booking.totalAmount)}</span>
          </span>
        </span>
        <span className="serial hidden items-center px-4 text-xs text-ink-soft [writing-mode:vertical-rl] lg:flex" aria-hidden>
          No. {billNumber(booking.bookingId)}
        </span>
      </Link>
    </li>
  );
}

export default function Bookings() {
  const bookings = useQuery({ queryKey: ["bookings"], queryFn: fetchers.myBookings });

  return (
    <div className="wrap py-8 md:py-12">
      <PageHeader title="My bookings" />
      {bookings.isPending ? (
        <RowSkeletons rows={5} />
      ) : bookings.isError ? (
        <ErrorNote error={bookings.error} onRetry={() => bookings.refetch()} />
      ) : bookings.data.data.length === 0 ? (
        <EmptyState title="No bookings yet" action={{ to: "/search", label: "Find a service" }}>
          When you book a pro, it will show up here with its status and bill.
        </EmptyState>
      ) : (
        <div className="grid gap-8">
          {[
            { title: "Coming up", rows: bookings.data.data.filter((b) => OPEN.includes(b.status)).reverse() },
            { title: "Past", rows: bookings.data.data.filter((b) => !OPEN.includes(b.status)) },
          ]
            .filter((group) => group.rows.length > 0)
            .map((group) => (
              <section key={group.title} aria-label={group.title}>
                <h2 className="mb-3 text-3xl">{group.title}</h2>
                <ul className="grid gap-2">
                  {group.rows.map((b) => (
                    <Stub key={b.bookingId} booking={b} />
                  ))}
                </ul>
              </section>
            ))}
        </div>
      )}
    </div>
  );
}
