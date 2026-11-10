import { useQuery } from "@tanstack/react-query";
import { fetchers } from "@/api/endpoints";
import { BarPanel } from "@/components/BarPanel";
import { EmptyState, ErrorNote, PageHeader, RowSkeletons } from "@/components/common";
import { formatMoney, formatMonth } from "@/lib/format";

export default function Earnings() {
  const monthly = useQuery({ queryKey: ["earnings", "monthly"], queryFn: fetchers.monthlyEarnings });
  const methods = useQuery({ queryKey: ["earnings", "methods"], queryFn: fetchers.earnings });

  if (monthly.isPending || methods.isPending) {
    return (
      <div className="wrap py-10">
        <RowSkeletons rows={2} className="h-64" />
      </div>
    );
  }
  if (monthly.isError || methods.isError) {
    return (
      <div className="wrap py-10">
        <ErrorNote error={monthly.error ?? methods.error} />
      </div>
    );
  }

  const total = methods.data.reduce((sum, m) => sum + m.totalEarned, 0);
  const payments = methods.data.reduce((sum, m) => sum + m.totalPayments, 0);
  const count = (n: number) => `${n} ${n === 1 ? "payment" : "payments"}`;

  return (
    <div className="wrap py-8 md:py-12">
      <PageHeader title="Earnings" />
      {payments === 0 ? (
        <EmptyState title="No earnings yet">When a customer pays for a job, it will show up here.</EmptyState>
      ) : (
        <div className="grid gap-4">
          <p className="bg-auto-yellow p-5">
            <span className="label-caps block !text-ink">All time</span>
            <span className="amount-hero figures">{formatMoney(total)}</span>
            <span className="block">from {count(payments)}</span>
          </p>
          <div className="grid gap-4 lg:grid-cols-2">
            <BarPanel
              title="By month"
              layout="columns"
              data={monthly.data.map((m) => ({ label: formatMonth(m.month), value: m.totalEarned, note: count(m.totalPayments) }))}
            />
            <BarPanel
              title="By payment method"
              layout="rows"
              data={methods.data.map((m) => ({ label: m.paymentMethod, value: m.totalEarned, note: count(m.totalPayments) }))}
            />
          </div>
        </div>
      )}
    </div>
  );
}
