import { useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Minus, Plus } from "lucide-react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import { ApiError, errorMessage } from "@/api/client";
import { fetchers, type OfferedService, type OfferedVariant } from "@/api/endpoints";
import { EmptyState, ErrorNote, FieldError, Monogram, Rating, RowSkeletons } from "@/components/common";
import { TextAreaField } from "@/components/form";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { formatDay, formatDateTime, formatMinutes, formatMoney, formatTime, indiaDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import { bookingSchema, type BookingValues } from "@/lib/validation";

type Picked = Record<number, { variantId?: number; quantity: number }>;

function Step({ n, title, children }: { n: number; title: string; children: ReactNode }) {
  return (
    <section className="grid min-w-0 gap-3" aria-labelledby={`step-${n}`}>
      <h2 id={`step-${n}`} className="flex items-baseline gap-3 text-3xl">
        <span className="figures text-lg font-bold text-auto-green">{n}</span>
        {title}
      </h2>
      {children}
    </section>
  );
}

function ServiceRow({
  service,
  variants,
  picked,
  onChange,
}: {
  service: OfferedService;
  variants: OfferedVariant[];
  picked?: { variantId?: number; quantity: number };
  onChange: (next?: { variantId?: number; quantity: number }) => void;
}) {
  const on = picked !== undefined;
  const quantity = picked?.quantity ?? 1;
  return (
    <li className={cn("grid gap-3 bg-card p-4", on && "outline-2 -outline-offset-2 outline-auto-green")}>
      <label className="flex cursor-pointer items-center gap-3">
        <input
          type="checkbox"
          className="size-5 accent-[var(--auto-green)]"
          checked={on}
          onChange={(e) => onChange(e.target.checked ? { variantId: variants[0]?.variantId, quantity: 1 } : undefined)}
        />
        <span className="min-w-0 flex-1 font-semibold">{service.serviceName}</span>
        {variants.length === 0 && <span className="figures">{formatMoney(service.price)}</span>}
      </label>

      {on && (
        <div className="flex flex-wrap items-center justify-between gap-3 pl-8">
          {variants.length > 0 ? (
            <div role="radiogroup" aria-label={`${service.serviceName} option`} className="flex flex-wrap gap-1.5">
              {variants.map((v) => (
                <label
                  key={v.variantId}
                  className={cn(
                    "cursor-pointer rounded-md border border-input px-3 py-1.5 text-sm",
                    "has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-ring",
                    picked?.variantId === v.variantId ? "border-ink bg-ink text-white" : "bg-card",
                  )}
                >
                  <input
                    type="radio"
                    className="sr-only"
                    name={`variant-${service.serviceId}`}
                    checked={picked?.variantId === v.variantId}
                    onChange={() => onChange({ variantId: v.variantId, quantity })}
                  />
                  {v.variantName} · <span className="figures">{formatMoney(v.price)}</span> · {formatMinutes(v.duration)}
                </label>
              ))}
            </div>
          ) : (
            <span />
          )}
          <div className="flex items-center gap-1" role="group" aria-label={`${service.serviceName} quantity`}>
            <Button type="button" variant="outline" size="icon-sm" aria-label="One less" disabled={quantity <= 1} onClick={() => onChange({ ...picked, quantity: quantity - 1 })}>
              <Minus aria-hidden />
            </Button>
            <span className="figures w-7 text-center" aria-live="polite">
              {quantity}
            </span>
            <Button type="button" variant="outline" size="icon-sm" aria-label="One more" disabled={quantity >= 20} onClick={() => onChange({ ...picked, quantity: quantity + 1 })}>
              <Plus aria-hidden />
            </Button>
          </div>
        </div>
      )}
    </li>
  );
}

export default function Book() {
  const providerId = Number(useParams().providerId);
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const client = useQueryClient();

  const profile = useQuery({ queryKey: ["profile", providerId], queryFn: () => fetchers.profile(providerId) });
  const addresses = useQuery({ queryKey: ["addresses"], queryFn: fetchers.addresses });

  const [picked, setPicked] = useState<Picked>({});
  const [day, setDay] = useState<string>();
  const [couponInput, setCouponInput] = useState("");
  const [coupon, setCoupon] = useState<string>();
  const [couponError, setCouponError] = useState<string>();

  const form = useForm<BookingValues>({ resolver: zodResolver(bookingSchema), defaultValues: { items: [] } });
  const { errors, isSubmitted } = form.formState;
  const slot = form.watch("slot");
  const locationId = form.watch("locationId");

  // Pre-tick the service the customer clicked Book on.
  const preselected = useRef(false);
  useEffect(() => {
    if (preselected.current || !profile.data) return;
    preselected.current = true;
    const id = Number(params.get("service"));
    if (profile.data.services.some((s) => s.serviceId === id)) {
      setPicked({ [id]: { variantId: profile.data.variants.find((v) => v.serviceId === id)?.variantId, quantity: 1 } });
    }
  }, [profile.data, params]);

  const items = useMemo(
    () => Object.entries(picked).map(([id, p]) => ({ serviceId: Number(id), variantId: p.variantId, quantity: p.quantity })),
    [picked],
  );
  useEffect(() => {
    form.setValue("items", items, { shouldValidate: isSubmitted });
  }, [items, form, isSubmitted]);

  const quote = useQuery({
    queryKey: ["quote", providerId, items, coupon],
    queryFn: () => fetchers.quote({ providerId, items, couponCode: coupon }),
    enabled: items.length > 0,
    placeholderData: keepPreviousData,
    retry: false,
  });

  // A change of services can drop the order below a coupon's minimum: show why and reprice without it.
  useEffect(() => {
    if (quote.error instanceof ApiError && quote.error.code === "coupon-invalid" && coupon) {
      setCouponError(quote.error.message);
      setCoupon(undefined);
    }
  }, [quote.error, coupon]);

  const applyCoupon = useMutation({
    mutationFn: (code: string) => fetchers.quote({ providerId, items, couponCode: code }),
    onSuccess: (_, code) => {
      setCoupon(code);
      setCouponError(undefined);
    },
    onError: (error) => setCouponError(errorMessage(error)),
  });

  const minutes = items.length > 0 ? quote.data?.minutes : undefined;
  const slots = useQuery({
    queryKey: ["slots", providerId, minutes],
    queryFn: () => fetchers.slots(providerId, indiaDate(), 14, minutes!),
    enabled: minutes !== undefined,
  });

  const days = useMemo(() => [...new Set((slots.data ?? []).map((s) => s.date))], [slots.data]);
  const activeDay = day && days.includes(day) ? day : days[0];
  const times = useMemo(() => (slots.data ?? []).filter((s) => s.date === activeDay), [slots.data, activeDay]);

  // A longer job may no longer fit the chosen time.
  useEffect(() => {
    if (slot && slots.data && !slots.data.some((s) => s.start === slot)) {
      form.setValue("slot", "");
    }
  }, [slots.data, slot, form]);

  // Retrying the same request reuses its key, so a dropped response can't create a second booking.
  const attempt = useRef<{ body: string; key: string } | null>(null);
  const create = useMutation({
    mutationFn: (values: BookingValues) => {
      const body = {
        providerId,
        locationId: values.locationId,
        couponCode: coupon,
        scheduledStart: values.slot,
        items: values.items,
        specialInstructions: values.specialInstructions?.trim() || undefined,
      };
      const text = JSON.stringify(body);
      if (attempt.current?.body !== text) attempt.current = { body: text, key: crypto.randomUUID() };
      return fetchers.createBooking(body, attempt.current.key);
    },
    onSuccess: (booking) => {
      toast.success("Booking sent. The provider will reply soon.");
      client.invalidateQueries({ queryKey: ["bookings"] });
      client.invalidateQueries({ queryKey: ["slots", providerId] });
      navigate(`/bookings/${booking.bookingId}`);
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === "slot-taken") {
        form.setValue("slot", "");
        client.invalidateQueries({ queryKey: ["slots", providerId] });
      }
      toast.error(errorMessage(error));
    },
  });

  if (profile.isPending || addresses.isPending) {
    return (
      <div className="wrap py-10">
        <RowSkeletons rows={5} className="h-24" />
      </div>
    );
  }
  if (profile.isError || addresses.isError) {
    return (
      <div className="wrap py-10">
        <ErrorNote error={profile.error ?? addresses.error} />
      </div>
    );
  }

  const { provider, services, variants } = profile.data;
  const lineFor = (serviceId: number) => {
    const service = services.find((s) => s.serviceId === serviceId)!;
    const p = picked[serviceId];
    const variant = variants.find((v) => v.variantId === p.variantId);
    return { service, variant, quantity: p.quantity, total: (variant?.price ?? service.price) * p.quantity };
  };

  return (
    <form
      noValidate
      onSubmit={form.handleSubmit((values) => create.mutate(values))}
      className="wrap grid items-start gap-8 py-8 md:py-12 lg:grid-cols-[1fr_22rem] lg:gap-12"
    >
      <div className="grid min-w-0 gap-9">
        <header className="flex items-center gap-3">
          <Monogram name={provider.providerName} />
          <div className="min-w-0">
            <h1 className="text-4xl">Book {provider.providerName}</h1>
            <Rating value={provider.avgRating} count={provider.reviewCount} />
          </div>
        </header>

        <Step n={1} title="Choose services">
          <ul className="grid gap-2">
            {services.map((s) => (
              <ServiceRow
                key={s.serviceId}
                service={s}
                variants={variants.filter((v) => v.serviceId === s.serviceId)}
                picked={picked[s.serviceId]}
                onChange={(next) =>
                  setPicked((current) => {
                    const copy = { ...current };
                    if (next) copy[s.serviceId] = next;
                    else delete copy[s.serviceId];
                    return copy;
                  })
                }
              />
            ))}
          </ul>
          <FieldError message={errors.items?.message ?? errors.items?.root?.message} />
        </Step>

        <Step n={2} title="Pick a time">
          {items.length === 0 ? (
            <p className="text-ink-soft">Choose a service first. The free times depend on how long the job takes.</p>
          ) : slots.isPending ? (
            <RowSkeletons rows={2} className="h-12" />
          ) : slots.isError ? (
            <ErrorNote error={slots.error} onRetry={() => slots.refetch()} />
          ) : days.length === 0 ? (
            <EmptyState title="No free time for a job this long">Try fewer services, or look at another provider.</EmptyState>
          ) : (
            <>
              <div role="group" aria-label="Day" className="-mx-1 flex gap-1.5 overflow-x-auto px-1 py-1">
                {days.map((d) => (
                  <button
                    type="button"
                    key={d}
                    aria-pressed={d === activeDay}
                    onClick={() => setDay(d)}
                    className={cn(
                      "shrink-0 rounded-md border border-input px-3 py-2 text-sm font-medium whitespace-nowrap",
                      d === activeDay ? "border-ink bg-ink text-white" : "bg-card hover:bg-accent",
                    )}
                  >
                    {formatDay(`${d}T06:30:00Z`)}
                  </button>
                ))}
              </div>
              <div role="group" aria-label="Start time" className="grid grid-cols-3 gap-1.5 sm:grid-cols-5 md:grid-cols-6">
                {times.map((t) => (
                  <button
                    type="button"
                    key={t.start}
                    aria-pressed={t.start === slot}
                    onClick={() => form.setValue("slot", t.start, { shouldValidate: isSubmitted })}
                    className={cn(
                      "figures h-11 rounded-md border border-input text-sm",
                      t.start === slot ? "border-auto-green bg-auto-green text-white" : "bg-card hover:bg-accent",
                    )}
                  >
                    {formatTime(t.start)}
                  </button>
                ))}
              </div>
            </>
          )}
          <FieldError message={errors.slot?.message} />
        </Step>

        <Step n={3} title="Where should they come?">
          {addresses.data.length === 0 ? (
            <EmptyState title="No saved addresses" action={{ to: "/addresses", label: "Add an address" }}>
              Save an address, then come back to finish this booking.
            </EmptyState>
          ) : (
            <div role="radiogroup" aria-label="Address" className="grid gap-2 sm:grid-cols-2">
              {addresses.data.map((a) => {
                const inCity = a.cityName === provider.cityName;
                return (
                  <label
                    key={a.locationId}
                    className={cn(
                      "grid cursor-pointer gap-0.5 bg-card p-4",
                      "has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-ring",
                      locationId === a.locationId && "outline-2 -outline-offset-2 outline-auto-green",
                      !inCity && "cursor-not-allowed text-ink-soft",
                    )}
                  >
                    <input
                      type="radio"
                      name="address"
                      className="sr-only"
                      disabled={!inCity}
                      checked={locationId === a.locationId}
                      onChange={() => form.setValue("locationId", a.locationId, { shouldValidate: isSubmitted })}
                    />
                    <span className="font-semibold">{a.label ?? "Address"}</span>
                    <span className="text-sm">
                      {[a.houseNo, a.street, a.areaName, a.cityName].filter(Boolean).join(", ")}
                    </span>
                    {!inCity && <span className="text-sm">{provider.providerName} doesn't work in {a.cityName}</span>}
                  </label>
                );
              })}
            </div>
          )}
          <FieldError message={errors.locationId?.message} />
          <Link to="/addresses" className="text-sm font-medium text-auto-green underline underline-offset-2">
            Add another address
          </Link>
        </Step>

        <Step n={4} title="Anything to add?">
          <div className="grid gap-1.5">
            <label htmlFor="coupon" className="text-sm font-medium">
              Coupon code
            </label>
            <div className="flex gap-2">
              <Input id="coupon" className="max-w-52 uppercase" value={couponInput} onChange={(e) => setCouponInput(e.target.value)} />
              <Button
                type="button"
                variant="secondary"
                className="border border-ink"
                disabled={!couponInput.trim() || items.length === 0 || applyCoupon.isPending}
                onClick={() => applyCoupon.mutate(couponInput.trim())}
              >
                Apply
              </Button>
            </div>
            {items.length === 0 && <p className="text-sm text-ink-soft">Choose a service to use a coupon.</p>}
            <div role="status">
              <FieldError message={couponError} />
              {coupon && !couponError && <p className="text-sm font-medium text-auto-green">{quote.data?.couponCode ?? coupon} applied</p>}
            </div>
          </div>
          <TextAreaField
            label="Notes for the pro (optional)"
            rows={2}
            error={errors.specialInstructions?.message}
            {...form.register("specialInstructions")}
          />
        </Step>
      </div>

      <aside className="ticket grid gap-3 p-5 lg:sticky lg:top-24" style={{ "--tear": "calc(100% - 76px)" } as CSSProperties} aria-label="Your bill">
        <h2 className="text-3xl">Your bill</h2>
        {items.length === 0 ? (
          <p className="text-ink-soft">Your services and price will appear here.</p>
        ) : (
          <ul className="grid gap-1.5">
            {items.map((item) => {
              const line = lineFor(item.serviceId);
              return (
                <li key={item.serviceId} className="flex items-baseline gap-2 text-[0.95rem]">
                  <span className="min-w-0">
                    {line.service.serviceName}
                    {line.variant && ` (${line.variant.variantName})`}
                    {line.quantity > 1 && ` ×${line.quantity}`}
                  </span>
                  <span className="leader" aria-hidden />
                  <span className="figures whitespace-nowrap">{formatMoney(line.total)}</span>
                </li>
              );
            })}
          </ul>
        )}
        {quote.data && items.length > 0 && (
          <dl className="grid gap-1 border-t pt-3" aria-live="polite">
            <div className="flex justify-between">
              <dt>Subtotal</dt>
              <dd className="figures">{formatMoney(quote.data.subtotal)}</dd>
            </div>
            <div className="flex justify-between">
              <dt>Discount</dt>
              <dd className="figures" data-testid="discount">−{formatMoney(quote.data.discount)}</dd>
            </div>
            <div className="flex justify-between text-sm text-ink-soft">
              <dt>Time needed</dt>
              <dd>{formatMinutes(quote.data.minutes)}</dd>
            </div>
            <div className="mt-1 flex items-baseline justify-between">
              <dt className="font-display text-2xl font-bold">Total</dt>
              <dd className="figures text-xl font-bold" data-testid="total">{formatMoney(quote.data.totalAmount)}</dd>
            </div>
          </dl>
        )}
        {slot && <p className="text-sm">{formatDateTime(slot)}</p>}
        <div className="mt-5 grid gap-2">
          <Button type="submit" size="lg" className="w-full" disabled={create.isPending}>
            {create.isPending ? "Sending…" : "Confirm booking"}
          </Button>
          <p className="text-center text-sm text-ink-soft">Pay after the pro accepts. Free to cancel until 2 hours before.</p>
        </div>
      </aside>
    </form>
  );
}
