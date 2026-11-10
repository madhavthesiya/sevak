import { useMemo, useState, type CSSProperties, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight } from "lucide-react";
import { Link, useNavigate } from "react-router";
import { toast } from "sonner";
import { errorMessage } from "@/api/client";
import { fetchers, type ServiceItem } from "@/api/endpoints";
import { HOME_FOR_ROLE, useSession, type Role } from "@/auth/session";
import { ErrorNote, Monogram, Rating, RowSkeletons, VerifiedMark } from "@/components/common";
import { SelectField } from "@/components/form";
import { Button } from "@/components/ui/button";
import { DAY_NAMES, formatRupees } from "@/lib/format";
import { useSqlDrawer } from "@/sql/SqlDrawer";

function groupByCategory(services: ServiceItem[]) {
  const groups = new Map<string, ServiceItem[]>();
  for (const service of services) {
    groups.set(service.categoryName, [...(groups.get(service.categoryName) ?? []), service]);
  }
  return [...groups.entries()];
}

function SearchTicket() {
  const navigate = useNavigate();
  const services = useQuery({ queryKey: ["services"], queryFn: fetchers.services, staleTime: 300_000 });
  const areas = useQuery({ queryKey: ["areas"], queryFn: fetchers.areas, staleTime: 300_000 });
  const cities = useMemo(() => [...new Set((areas.data ?? []).map((a) => a.cityName))], [areas.data]);
  const [service, setService] = useState("");
  const [city, setCity] = useState("");
  const [day, setDay] = useState("");

  function submit(event: FormEvent) {
    event.preventDefault();
    const query = new URLSearchParams();
    if (service) query.set("service", service);
    if (city) query.set("city", city);
    if (day) query.set("day", day);
    navigate(`/search?${query}`);
  }

  return (
    <form onSubmit={submit} className="ticket grid gap-4 p-5 md:p-6" style={{ "--tear": "calc(100% - 92px)" } as CSSProperties}>
      <h2 className="text-3xl">What needs doing?</h2>
      <SelectField label="Service" value={service} onChange={(e) => setService(e.target.value)}>
        <option value="">Any service</option>
        {groupByCategory(services.data?.data ?? []).map(([category, items]) => (
          <optgroup key={category} label={category}>
            {items.map((item) => (
              <option key={item.serviceId} value={item.serviceName}>
                {item.serviceName}
              </option>
            ))}
          </optgroup>
        ))}
      </SelectField>
      <div className="grid grid-cols-2 gap-3">
        <SelectField label="City" value={city} onChange={(e) => setCity(e.target.value)}>
          <option value="">All cities</option>
          {cities.map((c) => (
            <option key={c}>{c}</option>
          ))}
        </SelectField>
        <SelectField label="Day" value={day} onChange={(e) => setDay(e.target.value)}>
          <option value="">Any day</option>
          {DAY_NAMES.map((d) => (
            <option key={d}>{d}</option>
          ))}
        </SelectField>
      </div>
      <Button type="submit" size="lg" className="mt-3 w-full">
        Find a pro <ArrowRight aria-hidden />
      </Button>
    </form>
  );
}

function DemoLogins() {
  const { signInAsDemo } = useSession();
  const navigate = useNavigate();
  const [busy, setBusy] = useState<Role | null>(null);

  async function go(role: Role) {
    setBusy(role);
    try {
      await signInAsDemo(role);
      navigate(HOME_FOR_ROLE[role]);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
      <span className="font-semibold">Look around as</span>
      {(["customer", "provider", "admin"] as Role[]).map((role) => (
        <Button
          key={role}
          variant="outline"
          size="sm"
          className="border-ink bg-transparent capitalize hover:bg-black/8"
          disabled={busy !== null}
          aria-label={`Log in as ${role}`}
          onClick={() => go(role)}
        >
          {busy === role ? "Logging in…" : role}
        </Button>
      ))}
    </div>
  );
}

function RateCard() {
  const services = useQuery({ queryKey: ["services"], queryFn: fetchers.services, staleTime: 300_000 });
  if (services.isPending) return <RowSkeletons rows={3} className="h-28" />;
  if (services.isError) return <ErrorNote error={services.error} onRetry={() => services.refetch()} />;
  return (
    <div className="grid gap-x-12 gap-y-9 md:grid-cols-2 lg:grid-cols-3">
      {groupByCategory(services.data.data).map(([category, items]) => (
        <section key={category} className="min-w-0">
          <h3 className="mb-2 text-2xl">{category}</h3>
          <ul>
            {items.map((item) => (
              <li key={item.serviceId}>
                <Link
                  to={`/search?service=${encodeURIComponent(item.serviceName)}`}
                  className="flex items-baseline gap-2 py-1.5 hover:text-auto-green"
                >
                  <span className="min-w-0">{item.serviceName}</span>
                  <span className="leader" aria-hidden />
                  <span className="figures text-sm whitespace-nowrap">from {formatRupees(item.basePrice)}</span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

function TopProviders() {
  const top = useQuery({ queryKey: ["top"], queryFn: () => fetchers.top(4), staleTime: 300_000 });
  if (top.isPending) return <RowSkeletons rows={4} />;
  if (top.isError) return <ErrorNote error={top.error} onRetry={() => top.refetch()} />;
  return (
    <ol className="grid gap-2 sm:grid-cols-2">
      {top.data.map((p) => (
        <li key={p.providerId}>
          <Link to={`/providers/${p.providerId}`} className="flex items-center gap-3 bg-card p-3 hover:bg-white/70">
            <Monogram name={p.providerName} />
            <span className="grid min-w-0">
              <span className="truncate font-display text-xl font-bold" style={{ fontStretch: "85%" }}>
                {p.providerName}
              </span>
              <span className="text-sm text-ink-soft">
                {p.cityName}
                {p.experienceYears != null && ` · ${p.experienceYears} yrs`}
              </span>
            </span>
            <span className="ml-auto grid justify-items-end">
              <Rating value={p.avgRating} />
              <VerifiedMark />
            </span>
          </Link>
        </li>
      ))}
    </ol>
  );
}

export function Home() {
  const { open } = useSqlDrawer();
  return (
    <>
      <section className="bg-auto-yellow pt-8 pb-10 md:pt-14 md:pb-16">
        <div className="wrap grid items-end gap-8 lg:grid-cols-[1.35fr_1fr] lg:gap-14">
          <div className="grid gap-6">
            <h1 className="text-[3.5rem] leading-[0.92] font-extrabold sm:text-7xl lg:text-[6.5rem]" style={{ fontStretch: "75%" }}>
              <span className="block">Leaking tap.</span>
              <span className="block">Loose fan.</span>
              <span className="block">Damp wall.</span>
            </h1>
            <p className="max-w-[46ch] text-lg">
              Book a verified local pro. You see the price before you confirm, and only the times they are really free.
            </p>
            <DemoLogins />
          </div>
          <SearchTicket />
        </div>
      </section>

      <section className="wrap py-12 md:py-16">
        <div className="mb-7 flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-4xl md:text-5xl">Rate card</h2>
          <p className="text-ink-soft">Starting prices. Each pro sets their own.</p>
        </div>
        <RateCard />
      </section>

      <section className="wrap pb-12 md:pb-16">
        <h2 className="mb-5 text-4xl md:text-5xl">Highest rated</h2>
        <TopProviders />
      </section>

      <section className="on-dark bg-auto-green-deep py-10 text-white md:py-14">
        <div className="wrap grid items-center gap-6 md:grid-cols-[2fr_1fr]">
          <h2 className="text-4xl md:text-5xl">Two people can't book the same hour. The database refuses the second one.</h2>
          <div className="grid justify-items-start gap-3">
            <p className="text-white/85">Every screen here can show you the SQL that built it.</p>
            <Button variant="auto" onClick={() => open("bookings.create")}>
              See the constraint
            </Button>
          </div>
        </div>
      </section>
    </>
  );
}
