import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { errorMessage } from "@/api/client";
import { fetchers, type LeaderboardEntry } from "@/api/endpoints";
import { ErrorNote, PageHeader, RowSkeletons } from "@/components/common";
import { Button } from "@/components/ui/button";
import { formatRupees } from "@/lib/format";

function Board({ title, source, fast }: { title: string; source: string; fast: boolean }) {
  const board = useQuery({
    queryKey: ["admin", "leaderboard", fast],
    queryFn: async () => {
      const started = performance.now();
      const rows = await fetchers.leaderboard(fast);
      return { rows, ms: Math.round(performance.now() - started) };
    },
  });

  return (
    <section className="grid min-w-0 content-start gap-3 bg-card p-4 md:p-5" aria-label={title}>
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <h2 className="text-3xl">{title}</h2>
          <p className="text-sm text-ink-soft">{source}</p>
        </div>
        {board.data && <p className="figures text-sm">{board.data.ms} ms in this browser</p>}
      </div>
      {board.isPending ? (
        <RowSkeletons rows={5} className="h-9" />
      ) : board.isError ? (
        <ErrorNote error={board.error} onRetry={() => board.refetch()} />
      ) : (
        <Table rows={board.data.rows} />
      )}
    </section>
  );
}

function Table({ rows }: { rows: LeaderboardEntry[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-[0.95rem]">
        <thead>
          <tr className="label-caps text-left">
            <th scope="col" className="py-1 pr-2">Rank</th>
            <th scope="col" className="py-1 pr-2">Provider</th>
            <th scope="col" className="py-1 pr-2 text-right">Score</th>
            <th scope="col" className="py-1 pr-2 text-right">Jobs</th>
            <th scope="col" className="py-1 text-right">Revenue</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.providerId} className="border-t">
              <td className="figures py-2 pr-2">{r.rank}</td>
              <th scope="row" className="py-2 pr-2 text-left font-medium">
                {r.providerName}
                <span className="block text-sm font-normal text-ink-soft">{r.cityName}</span>
              </th>
              <td className="figures py-2 pr-2 text-right">{r.weightedRating?.toFixed(2) ?? "–"}</td>
              <td className="figures py-2 pr-2 text-right">{r.jobsCompleted}</td>
              <td className="figures py-2 text-right">{formatRupees(r.totalRevenue)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function Leaderboard() {
  const client = useQueryClient();
  const refresh = useMutation({
    mutationFn: fetchers.refreshViews,
    onSuccess: (result) => {
      toast.success(result.message);
      client.invalidateQueries({ queryKey: ["admin", "leaderboard"] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <div className="wrap py-8 md:py-12">
      <PageHeader
        title="Leaderboard"
        intro="The same ranking, read two ways. The stored copy is cheaper to read but only as fresh as its last refresh."
        actions={
          <Button variant="secondary" className="border border-ink" disabled={refresh.isPending} onClick={() => refresh.mutate()}>
            {refresh.isPending ? "Refreshing…" : "Refresh stored copy"}
          </Button>
        }
      />
      <div className="grid items-start gap-4 lg:grid-cols-2">
        <Board title="Live" source="Computed on every request" fast={false} />
        <Board title="Stored" source="Read from a materialized view" fast />
      </div>
    </div>
  );
}
