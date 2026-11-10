import { useQuery } from "@tanstack/react-query";
import { fetchers } from "@/api/endpoints";
import { EmptyState, ErrorNote, PageHeader, Rating, RowSkeletons } from "@/components/common";
import { formatDay } from "@/lib/format";

/** A five-point scale with two markers, so the gap between you and your city can be seen. */
function Comparison({ mine, city, cityName, verdict }: { mine: number; city: number; cityName: string; verdict: string }) {
  const at = (value: number) => `${(value / 5) * 100}%`;
  return (
    <section className="grid gap-4 bg-card p-5 md:p-6" aria-label="Your rating compared with your city">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h2 className="text-4xl">{verdict}</h2>
        <p className="text-ink-soft">for {cityName}</p>
      </div>
      <div className="relative mx-10 mt-7 mb-7 h-2 bg-secondary">
        <span className="absolute inset-y-0 left-0 bg-auto-green" style={{ width: at(mine) }} />
        <span className="absolute -top-7 -translate-x-1/2 text-sm font-semibold whitespace-nowrap" style={{ left: at(mine) }}>
          You <span className="figures">{mine.toFixed(2)}</span>
        </span>
        <span className="absolute -top-1 h-4 w-0.5 bg-ink" style={{ left: at(city) }} />
        <span className="absolute top-4 -translate-x-1/2 text-sm whitespace-nowrap" style={{ left: at(city) }}>
          {cityName} <span className="figures">{city.toFixed(2)}</span>
        </span>
      </div>
    </section>
  );
}

export default function Reviews() {
  const profile = useQuery({ queryKey: ["ownProfile"], queryFn: fetchers.ownProfile });
  const providerId = profile.data?.provider.providerId;
  const reviews = useQuery({
    queryKey: ["reviews", providerId],
    queryFn: () => fetchers.reviews(providerId!, 50),
    enabled: providerId !== undefined,
  });

  if (profile.isPending) {
    return (
      <div className="wrap py-10">
        <RowSkeletons rows={3} className="h-24" />
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

  const p = profile.data.provider;
  return (
    <div className="wrap grid gap-6 py-8 md:py-12">
      <PageHeader title="Reviews and rating" />
      {p.avgRating != null && p.cityAverageRating != null && (
        <Comparison mine={p.avgRating} city={p.cityAverageRating} cityName={p.cityName} verdict={p.comparedToCity} />
      )}
      {reviews.isPending ? (
        <RowSkeletons rows={2} />
      ) : reviews.isError ? (
        <ErrorNote error={reviews.error} onRetry={() => reviews.refetch()} />
      ) : reviews.data.data.length === 0 ? (
        <EmptyState title="No reviews yet">Customers can review you once a job is complete.</EmptyState>
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
    </div>
  );
}
