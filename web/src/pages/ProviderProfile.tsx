import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router";
import { fetchers, type WeeklySlot } from "@/api/endpoints";
import { EmptyState, ErrorNote, Monogram, Rating, RowSkeletons, VerifiedMark } from "@/components/common";
import { Button } from "@/components/ui/button";
import { DAY_NAMES, formatClock, formatDay, formatMoney } from "@/lib/format";

function WeekGrid({ slots }: { slots: WeeklySlot[] }) {
  return (
    <ul className="grid gap-1 md:grid-cols-7">
      {DAY_NAMES.map((day) => {
        const today = slots.filter((s) => s.dayOfWeek === day);
        return (
          <li key={day} className="flex items-baseline justify-between gap-3 bg-card p-3 md:block">
            <span className="font-semibold">{day.slice(0, 3)}</span>
            <span className="grid justify-items-end text-sm md:mt-1 md:justify-items-start">
              {today.length === 0 ? (
                <span className="text-ink-soft">Off</span>
              ) : (
                today.map((s) => (
                  <span key={s.startTime} className="whitespace-nowrap">
                    {formatClock(s.startTime)}–{formatClock(s.endTime)}
                  </span>
                ))
              )}
            </span>
          </li>
        );
      })}
    </ul>
  );
}

export default function ProviderProfile() {
  const id = Number(useParams().id);
  const profile = useQuery({ queryKey: ["profile", id], queryFn: () => fetchers.profile(id) });
  const reviews = useQuery({ queryKey: ["reviews", id], queryFn: () => fetchers.reviews(id) });

  if (profile.isPending) {
    return (
      <div className="wrap py-10">
        <RowSkeletons rows={4} className="h-24" />
      </div>
    );
  }
  if (profile.isError) {
    return (
      <div className="wrap py-10">
        <ErrorNote error={profile.error} onRetry={() => profile.refetch()} />
      </div>
    );
  }

  const { provider: p, services, availability, variants } = profile.data;

  return (
    <div className="wrap grid gap-10 py-8 md:py-12">
      <header className="flex flex-wrap items-start gap-4">
        <Monogram name={p.providerName} size="lg" />
        <div className="grid min-w-0 flex-1 gap-1.5">
          <h1 className="text-5xl md:text-6xl">{p.providerName}</h1>
          <p className="flex flex-wrap items-center gap-x-4 gap-y-1">
            <span>{p.cityName}</span>
            {p.experienceYears != null && <span>{p.experienceYears} years' experience</span>}
            {p.verificationStatus === "verified" && <VerifiedMark />}
          </p>
          <p className="flex flex-wrap items-center gap-x-3">
            <Rating value={p.avgRating} count={p.reviewCount} />
            {p.cityAverageRating != null && (
              <span className="text-sm text-ink-soft">
                {p.comparedToCity} for {p.cityName}, where the average is {p.cityAverageRating.toFixed(2)}
              </span>
            )}
          </p>
          {p.bio && <p className="max-w-[60ch]">{p.bio}</p>}
        </div>
      </header>

      <section aria-labelledby="services-heading">
        <h2 id="services-heading" className="mb-3 text-3xl">
          Services and prices
        </h2>
        <ul className="grid gap-2 md:grid-cols-2">
          {services.map((s) => {
            const own = variants.filter((v) => v.serviceId === s.serviceId);
            return (
              <li key={s.serviceId} className="flex items-center justify-between gap-3 bg-card p-4">
                <div className="min-w-0">
                  <h3 className="text-xl">{s.serviceName}</h3>
                  <p className="figures text-sm">
                    {own.length > 0
                      ? own.map((v) => `${v.variantName} ${formatMoney(v.price)}`).join(" · ")
                      : formatMoney(s.price)}
                  </p>
                </div>
                <Button asChild>
                  <Link to={`/book/${p.providerId}?service=${s.serviceId}`} aria-label={`Book ${s.serviceName}`}>
                    Book
                  </Link>
                </Button>
              </li>
            );
          })}
        </ul>
      </section>

      <section aria-labelledby="hours-heading">
        <h2 id="hours-heading" className="mb-3 text-3xl">
          Working week
        </h2>
        <WeekGrid slots={availability} />
      </section>

      <section aria-labelledby="reviews-heading">
        <h2 id="reviews-heading" className="mb-3 text-3xl">
          Reviews
        </h2>
        {reviews.isPending ? (
          <RowSkeletons rows={2} />
        ) : reviews.isError ? (
          <ErrorNote error={reviews.error} onRetry={() => reviews.refetch()} />
        ) : reviews.data.data.length === 0 ? (
          <EmptyState title="No reviews yet">The first customer to book can leave one.</EmptyState>
        ) : (
          <ul className="grid gap-2">
            {reviews.data.data.map((r, i) => (
              <li key={i} className="grid gap-1 bg-card p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-semibold">{r.customerName}</span>
                  <span className="text-sm text-ink-soft">{formatDay(r.reviewedAt)}</span>
                </div>
                <Rating value={r.rating} />
                {r.comment && <p className="max-w-[65ch]">{r.comment}</p>}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
