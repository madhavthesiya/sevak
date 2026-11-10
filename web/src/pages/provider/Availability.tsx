import { useState, type FormEvent } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { X } from "lucide-react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { errorMessage } from "@/api/client";
import { fetchers } from "@/api/endpoints";
import { ErrorNote, FieldError, PageHeader, RowSkeletons } from "@/components/common";
import { SelectField, TextField } from "@/components/form";
import { Button } from "@/components/ui/button";
import { DAY_NAMES, formatClock, formatMoney } from "@/lib/format";
import { hoursSchema, type HoursValues } from "@/lib/validation";

function Hours() {
  const client = useQueryClient();
  const slots = useQuery({ queryKey: ["ownSlots"], queryFn: fetchers.ownSlots });
  const [conflict, setConflict] = useState<string>();
  const form = useForm<HoursValues>({
    resolver: zodResolver(hoursSchema),
    defaultValues: { dayOfWeek: 1, startTime: "09:00", endTime: "13:00" },
  });
  const { errors } = form.formState;
  const refresh = () => client.invalidateQueries({ queryKey: ["ownSlots"] });

  const add = useMutation({
    mutationFn: (v: HoursValues) => fetchers.addSlot(v),
    onSuccess: () => {
      setConflict(undefined);
      toast.success("Hours added.");
      refresh();
    },
    // The database's exclusion constraint rejects overlaps; the API words that for people.
    onError: (error) => setConflict(errorMessage(error)),
  });
  const remove = useMutation({
    mutationFn: fetchers.removeSlot,
    onSuccess: () => {
      toast.success("Hours removed.");
      refresh();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <section className="grid gap-4" aria-labelledby="hours-heading">
      <h2 id="hours-heading" className="text-3xl">
        Working hours
      </h2>
      {slots.isPending ? (
        <RowSkeletons rows={7} className="h-12" />
      ) : slots.isError ? (
        <ErrorNote error={slots.error} onRetry={() => slots.refetch()} />
      ) : (
        <ul className="grid gap-1">
          {DAY_NAMES.map((day, index) => {
            const own = slots.data.filter((s) => s.dayOfWeek === index + 1);
            return (
              <li key={day} className="grid items-center gap-2 bg-card p-3 sm:grid-cols-[7rem_1fr]">
                <span className="font-semibold">{day}</span>
                <div className="flex flex-wrap gap-1.5">
                  {own.length === 0 && <span className="text-sm text-ink-soft">Day off</span>}
                  {own.map((s) => (
                    <span key={s.slotId} className="inline-flex items-center gap-1 bg-secondary py-0.5 pr-0.5 pl-2 text-sm">
                      {formatClock(s.startTime)}–{formatClock(s.endTime)}
                      <Button
                        variant="ghost"
                        size="icon-xs"
                        disabled={remove.isPending}
                        aria-label={`Remove ${day} ${formatClock(s.startTime)}`}
                        onClick={() => remove.mutate(s.slotId)}
                      >
                        <X aria-hidden />
                      </Button>
                    </span>
                  ))}
                </div>
              </li>
            );
          })}
        </ul>
      )}
      <form onSubmit={form.handleSubmit((v) => add.mutate(v))} noValidate className="grid items-start gap-3 bg-card p-4 sm:grid-cols-[1.4fr_1fr_1fr_auto]">
        <SelectField label="Day" {...form.register("dayOfWeek", { valueAsNumber: true })}>
          {DAY_NAMES.map((day, i) => (
            <option key={day} value={i + 1}>
              {day}
            </option>
          ))}
        </SelectField>
        <TextField label="From" type="time" error={errors.startTime?.message} {...form.register("startTime")} />
        <TextField label="To" type="time" error={errors.endTime?.message} {...form.register("endTime")} />
        <Button type="submit" className="sm:mt-[1.625rem]" disabled={add.isPending}>
          Add hours
        </Button>
        <div role="alert" className="sm:col-span-4">
          <FieldError message={conflict} />
        </div>
      </form>
    </section>
  );
}

function Services() {
  const client = useQueryClient();
  const own = useQuery({ queryKey: ["ownServices"], queryFn: fetchers.ownServices });
  const catalog = useQuery({ queryKey: ["services"], queryFn: fetchers.services, staleTime: 300_000 });
  const [serviceId, setServiceId] = useState("");
  const [price, setPrice] = useState("");
  const refresh = () => client.invalidateQueries({ queryKey: ["ownServices"] });

  const offer = useMutation({
    mutationFn: () => fetchers.offerService({ serviceId: Number(serviceId), price: Number(price) }),
    onSuccess: () => {
      toast.success("Service saved.");
      setServiceId("");
      setPrice("");
      refresh();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const stop = useMutation({
    mutationFn: fetchers.stopOffering,
    onSuccess: () => {
      toast.success("Service removed.");
      refresh();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    offer.mutate();
  }

  return (
    <section className="grid content-start gap-4" aria-labelledby="services-heading">
      <h2 id="services-heading" className="text-3xl">
        Services you offer
      </h2>
      {own.isPending ? (
        <RowSkeletons rows={2} className="h-14" />
      ) : own.isError ? (
        <ErrorNote error={own.error} onRetry={() => own.refetch()} />
      ) : (
        <ul className="grid gap-1">
          {own.data.map((s) => (
            <li key={s.serviceId} className="flex items-center justify-between gap-3 bg-card p-3">
              <span className="min-w-0">{s.serviceName}</span>
              <span className="flex items-center gap-3">
                <span className="figures">{formatMoney(s.price)}</span>
                <Button variant="outline" size="sm" disabled={stop.isPending} aria-label={`Stop offering ${s.serviceName}`} onClick={() => stop.mutate(s.serviceId)}>
                  Remove
                </Button>
              </span>
            </li>
          ))}
        </ul>
      )}
      <form onSubmit={submit} className="grid gap-3 bg-card p-4">
        <SelectField label="Service" required value={serviceId} onChange={(e) => setServiceId(e.target.value)}>
          <option value="">Choose a service</option>
          {catalog.data?.data.map((s) => (
            <option key={s.serviceId} value={s.serviceId}>
              {s.serviceName}
            </option>
          ))}
        </SelectField>
        <TextField label="Your price (₹)" type="number" min={1} max={999999} step="1" required value={price} onChange={(e) => setPrice(e.target.value)} />
        <Button type="submit" disabled={!serviceId || !price || offer.isPending}>
          Save service
        </Button>
      </form>
    </section>
  );
}

export default function Availability() {
  return (
    <div className="wrap py-8 md:py-12">
      <PageHeader title="Hours and services" intro="Customers can only book inside the hours you set here." />
      <div className="grid items-start gap-8 lg:grid-cols-[1.5fr_1fr]">
        <Hours />
        <Services />
      </div>
    </div>
  );
}
