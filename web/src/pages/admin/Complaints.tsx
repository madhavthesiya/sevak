import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { errorMessage } from "@/api/client";
import { fetchers, type Complaint } from "@/api/endpoints";
import { EmptyState, ErrorNote, PageHeader, RowSkeletons } from "@/components/common";
import { Button } from "@/components/ui/button";
import { billNumber, formatDay } from "@/lib/format";

const COLUMNS = [
  { title: "Open", statuses: ["open"], next: { status: "in_progress", label: "Start work" } },
  { title: "In progress", statuses: ["in_progress"], next: { status: "resolved", label: "Mark resolved" } },
  { title: "Finished", statuses: ["resolved", "closed"], next: undefined },
] as const;

type NextStep = { status: "in_progress" | "resolved"; label: string };

function Card({ complaint: c, next }: { complaint: Complaint; next?: NextStep }) {
  const client = useQueryClient();
  const move = useMutation({
    mutationFn: (status: string) => fetchers.updateComplaint(c.complaintId, { status }),
    onSuccess: () => {
      toast.success("Complaint updated.");
      client.invalidateQueries({ queryKey: ["admin", "complaints"] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <li className="grid gap-2 bg-card p-4">
      <h3 className="text-xl">{c.subject}</h3>
      {c.description && <p className="text-[0.95rem]">{c.description}</p>}
      <p className="text-sm text-ink-soft">
        {c.raisedByName}, {c.cityName} · {formatDay(c.createdAt)} · <span className="serial">No. {billNumber(c.bookingId)}</span>
      </p>
      {c.assignedAdminName && <p className="text-sm">With {c.assignedAdminName}</p>}
      {c.resolutionNotes && <p className="text-sm">{c.resolutionNotes}</p>}
      {next && (
        <Button size="sm" variant="outline" className="justify-self-start" disabled={move.isPending} onClick={() => move.mutate(next.status)}>
          {next.label}
        </Button>
      )}
    </li>
  );
}

export default function Complaints() {
  const board = useQuery({ queryKey: ["admin", "complaints", "board"], queryFn: fetchers.complaintBoard });
  const summary = useQuery({ queryKey: ["admin", "complaints", "summary"], queryFn: fetchers.complaintSummary });

  return (
    <div className="wrap py-8 md:py-12">
      <PageHeader title="Complaints" />
      {summary.data && summary.data.length > 0 && (
        <ul className="mb-5 flex flex-wrap gap-x-8 gap-y-2" aria-label="Summary by status">
          {summary.data.map((s) => (
            <li key={s.complaintStatus}>
              <span className="figures text-lg font-semibold">{s.count}</span>{" "}
              <span className="capitalize">{s.complaintStatus.replace("_", " ")}</span>
              <span className="text-sm text-ink-soft"> in {s.affectedCities}</span>
            </li>
          ))}
        </ul>
      )}
      {board.isPending ? (
        <RowSkeletons rows={3} className="h-32" />
      ) : board.isError ? (
        <ErrorNote error={board.error} onRetry={() => board.refetch()} />
      ) : board.data.length === 0 ? (
        <EmptyState title="No complaints" />
      ) : (
        <div className="grid items-start gap-5 md:grid-cols-3">
          {COLUMNS.map((column) => {
            const cards = board.data.filter((c) => (column.statuses as readonly string[]).includes(c.status));
            return (
              <section key={column.title} className="grid min-w-0 gap-2" aria-label={column.title}>
                <h2 className="flex items-baseline justify-between text-2xl">
                  {column.title} <span className="figures text-base">{cards.length}</span>
                </h2>
                {cards.length === 0 ? (
                  <p className="text-ink-soft">Nothing here.</p>
                ) : (
                  <ul className="grid gap-2">
                    {cards.map((c) => (
                      <Card key={c.complaintId} complaint={c} next={column.next} />
                    ))}
                  </ul>
                )}
              </section>
            );
          })}
        </div>
      )}
    </div>
  );
}
