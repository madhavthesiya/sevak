import { useQuery } from "@tanstack/react-query";
import { fetchers } from "@/api/endpoints";
import { BarPanel } from "@/components/BarPanel";
import { EmptyState, ErrorNote, PageHeader, RowSkeletons } from "@/components/common";
import { formatMoney } from "@/lib/format";

export default function Revenue() {
  const byCategory = useQuery({ queryKey: ["admin", "revenue"], queryFn: fetchers.revenue });
  const byCity = useQuery({ queryKey: ["admin", "cityRevenue"], queryFn: fetchers.cityRevenue });

  if (byCategory.isPending || byCity.isPending) {
    return (
      <div className="wrap py-10">
        <RowSkeletons rows={2} className="h-64" />
      </div>
    );
  }
  if (byCategory.isError || byCity.isError) {
    return (
      <div className="wrap py-10">
        <ErrorNote error={byCategory.error ?? byCity.error} />
      </div>
    );
  }

  const total = byCategory.data.reduce((sum, c) => sum + c.totalRevenue, 0);
  const count = (n: number) => `${n} ${n === 1 ? "booking" : "bookings"}`;

  return (
    <div className="wrap py-8 md:py-12">
      <PageHeader title="Revenue" />
      {byCategory.data.length === 0 ? (
        <EmptyState title="No payments yet" />
      ) : (
        <div className="grid gap-4">
          <p className="bg-auto-yellow p-5">
            <span className="label-caps block !text-ink">Successful payments</span>
            <span className="amount-hero figures">{formatMoney(total)}</span>
          </p>
          <div className="grid gap-4 lg:grid-cols-2">
            <BarPanel
              title="By category"
              layout="rows"
              data={byCategory.data.map((c) => ({ label: c.categoryName, value: c.totalRevenue, note: count(c.totalBookings) }))}
            />
            <BarPanel
              title="By city"
              layout="rows"
              note="From a materialized view, refreshed every 15 minutes."
              data={byCity.data.map((c) => ({
                label: c.cityName,
                value: c.totalRevenue,
                note: `${count(c.totalBookings)}, average ${formatMoney(c.avgBookingValue)}`,
              }))}
            />
          </div>
        </div>
      )}
    </div>
  );
}
