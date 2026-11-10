import { useMemo, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { CalendarClock } from "lucide-react";
import { Link, useSearchParams } from "react-router";
import { fetchers, type Offering } from "@/api/endpoints";
import { EmptyState, ErrorNote, Monogram, PageHeader, Rating, RowSkeletons } from "@/components/common";
import { SelectField, TextField } from "@/components/form";
import { Button } from "@/components/ui/button";
import { DAY_NAMES, formatDateTime, formatMoney, indiaDate } from "@/lib/format";

/** The soonest bookable hour, looked up per provider. */
function NextSlot({ providerId }: { providerId: number }) {
  const slots = useQuery({
    queryKey: ["slots", providerId, 60],
    queryFn: () => fetchers.slots(providerId, indiaDate(), 14, 60),
  });
  if (slots.isPending) return <span className="text-sm text-ink-soft">Checking times…</span>;
  const next = slots.data?.[0];
  return (
    <span className="inline-flex items-center gap-1 text-sm">
      <CalendarClock className="size-4 text-ink-soft" aria-hidden />
      {next ? `Next free ${formatDateTime(next.start)}` : "No free time in the next two weeks"}
    </span>
  );
}

function ResultRow({ offer }: { offer: Offering }) {
  const book = `/book/${offer.providerId}?service=${offer.serviceId}`;
  return (
    <li className="grid gap-3 bg-card p-4 sm:grid-cols-[auto_1fr_auto] sm:items-center">
      <div className="flex min-w-0 items-center gap-3 sm:contents">
        <Monogram name={offer.providerName} />
        <div className="grid min-w-0 gap-0.5">
          <h2 className="text-2xl">
            <Link to={`/providers/${offer.providerId}`} className="hover:text-auto-green">
              {offer.providerName}
            </Link>
          </h2>
          <p>
            {offer.serviceName} <span className="text-ink-soft">in {offer.cityName}</span>
          </p>
          <div className="flex flex-wrap gap-x-4 gap-y-1">
            <Rating value={offer.avgRating} count={offer.reviewCount} />
            <NextSlot providerId={offer.providerId} />
          </div>
        </div>
      </div>
      <div className="flex items-center justify-between gap-4 sm:grid sm:justify-items-end">
        <span className="figures text-lg font-semibold">{formatMoney(offer.price)}</span>
        <Button asChild>
          <Link to={book} aria-label={`Book ${offer.serviceName} with ${offer.providerName}`}>
            Book
          </Link>
        </Button>
      </div>
    </li>
  );
}

export default function Search() {
  const [params, setParams] = useSearchParams();
  const service = params.get("service") ?? "";
  const city = params.get("city") ?? "";
  const day = params.get("day") ?? "";

  const areas = useQuery({ queryKey: ["areas"], queryFn: fetchers.areas, staleTime: 300_000 });
  const cities = useMemo(() => [...new Set((areas.data ?? []).map((a) => a.cityName))], [areas.data]);

  const results = useQuery({
    queryKey: ["search", service, city, day],
    queryFn: () =>
      fetchers.search({ service: service || undefined, city: city || undefined, day: day || undefined, limit: 30 }),
  });

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const next = new URLSearchParams();
    for (const name of ["service", "city", "day"]) {
      const value = String(data.get(name) ?? "").trim();
      if (value) next.set(name, value);
    }
    setParams(next);
  }

  return (
    <div className="wrap py-8 md:py-12">
      <PageHeader title="Find a pro" />
      <form
        key={`${service}|${city}|${day}|${cities.length}`}
        onSubmit={apply}
        className="mb-6 grid items-end gap-3 sm:grid-cols-2 lg:grid-cols-[2fr_1fr_1fr_auto]"
      >
        <TextField label="Service" name="service" defaultValue={service} placeholder="Cleaning, wiring, tap repair…" />
        <SelectField label="City" name="city" defaultValue={city}>
          <option value="">All cities</option>
          {cities.map((c) => (
            <option key={c}>{c}</option>
          ))}
        </SelectField>
        <SelectField label="Day" name="day" defaultValue={day}>
          <option value="">Any day</option>
          {DAY_NAMES.map((d) => (
            <option key={d}>{d}</option>
          ))}
        </SelectField>
        <Button type="submit" variant="secondary" className="border border-ink">
          Search
        </Button>
      </form>

      {results.isPending ? (
        <RowSkeletons rows={5} className="h-28" />
      ) : results.isError ? (
        <ErrorNote error={results.error} onRetry={() => results.refetch()} />
      ) : results.data.data.length === 0 ? (
        <EmptyState title="No pros match that search">Try another city, any day, or a shorter service name.</EmptyState>
      ) : (
        <>
          <p className="mb-2 text-sm text-ink-soft" role="status">
            {results.data.total} {results.data.total === 1 ? "match" : "matches"}, best rated first
          </p>
          <ul className="grid gap-2">
            {results.data.data.map((offer) => (
              <ResultRow key={`${offer.providerId}-${offer.serviceId}`} offer={offer} />
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
