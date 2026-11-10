import { useMemo } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { errorMessage } from "@/api/client";
import { fetchers, type Area } from "@/api/endpoints";
import { EmptyState, ErrorNote, PageHeader, RowSkeletons } from "@/components/common";
import { SelectField, TextField } from "@/components/form";
import { Button } from "@/components/ui/button";
import { addressSchema, placeholderCoordinates, type AddressValues } from "@/lib/validation";

export default function Addresses() {
  const client = useQueryClient();
  const addresses = useQuery({ queryKey: ["addresses"], queryFn: fetchers.addresses });
  const areas = useQuery({ queryKey: ["areas"], queryFn: fetchers.areas, staleTime: 300_000 });
  const byCity = useMemo(() => {
    const groups = new Map<string, Area[]>();
    areas.data?.forEach((a) => groups.set(a.cityName, [...(groups.get(a.cityName) ?? []), a]));
    return [...groups.entries()];
  }, [areas.data]);

  const form = useForm<AddressValues>({ resolver: zodResolver(addressSchema) });
  const { errors } = form.formState;

  const add = useMutation({
    mutationFn: (v: AddressValues) =>
      fetchers.addAddress({
        ...v,
        landmark: v.landmark?.trim() || undefined,
        ...placeholderCoordinates(v.areaId, v.street, v.houseNo),
      }),
    onSuccess: () => {
      toast.success("Address saved.");
      form.reset();
      client.invalidateQueries({ queryKey: ["addresses"] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  const remove = useMutation({
    mutationFn: fetchers.removeAddress,
    onSuccess: () => {
      toast.success("Address removed.");
      client.invalidateQueries({ queryKey: ["addresses"] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <div className="wrap py-8 md:py-12">
      <PageHeader title="Addresses" intro="Past bookings keep their address even if you remove it here." />
      <div className="grid items-start gap-8 lg:grid-cols-[1.3fr_1fr]">
        <section aria-label="Saved addresses">
          {addresses.isPending ? (
            <RowSkeletons rows={3} />
          ) : addresses.isError ? (
            <ErrorNote error={addresses.error} onRetry={() => addresses.refetch()} />
          ) : addresses.data.length === 0 ? (
            <EmptyState title="No addresses yet">Add your first one with the form on this page.</EmptyState>
          ) : (
            <ul className="grid gap-2">
              {addresses.data.map((a) => (
                <li key={a.locationId} className="flex items-center justify-between gap-3 bg-card p-4">
                  <div className="min-w-0">
                    <h2 className="text-2xl">{a.label ?? "Address"}</h2>
                    <p>{[a.houseNo, a.street].filter(Boolean).join(", ")}</p>
                    <p className="text-sm text-ink-soft">
                      {a.areaName}, {a.cityName} {a.pincode}
                    </p>
                  </div>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={remove.isPending}
                    aria-label={`Remove ${a.label ?? "address"}`}
                    onClick={() => remove.mutate(a.locationId)}
                  >
                    Remove
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </section>

        <form onSubmit={form.handleSubmit((v) => add.mutate(v))} noValidate className="grid gap-4 bg-card p-5 md:p-6">
          <h2 className="text-3xl">Add an address</h2>
          <TextField label="Name for this address" placeholder="Home, Office, Parents" error={errors.label?.message} {...form.register("label")} />
          <TextField label="Flat or house number" autoComplete="address-line1" error={errors.houseNo?.message} {...form.register("houseNo")} />
          <TextField label="Street" autoComplete="address-line2" error={errors.street?.message} {...form.register("street")} />
          <TextField label="Landmark (optional)" error={errors.landmark?.message} {...form.register("landmark")} />
          <SelectField
            label="Area"
            error={errors.areaId?.message}
            {...form.register("areaId", { setValueAs: (v) => (v === "" || v === undefined ? undefined : Number(v)) })}
          >
            <option value="">Choose an area</option>
            {byCity.map(([city, list]) => (
              <optgroup key={city} label={city}>
                {list.map((a) => (
                  <option key={a.areaId} value={a.areaId}>
                    {a.areaName} ({a.pincode})
                  </option>
                ))}
              </optgroup>
            ))}
          </SelectField>
          <Button type="submit" disabled={add.isPending}>
            {add.isPending ? "Saving…" : "Save address"}
          </Button>
        </form>
      </div>
    </div>
  );
}
