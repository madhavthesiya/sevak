import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { MapPin } from "lucide-react";
import { toast } from "sonner";
import { errorMessage } from "@/api/client";
import { fetchers, type Job } from "@/api/endpoints";
import { EmptyState, ErrorNote, PageHeader, RowSkeletons, StatusPill } from "@/components/common";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { billNumber, formatDateTime, formatMoney, indiaDate } from "@/lib/format";

type Move = "accept" | "decline" | "start" | "complete";
type JobAction = { move: Move; label: string; primary?: boolean };
const DONE: Record<Move, string> = {
  accept: "Job accepted.",
  decline: "Job declined.",
  start: "Job started.",
  complete: "Job completed.",
};

function useMove() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, move }: { id: number; move: Move }) => fetchers[move](id),
    onSuccess: (_, { move }) => {
      toast.success(DONE[move]);
      client.invalidateQueries({ queryKey: ["jobs"] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
}

function JobTicket({ job, actions }: { job: Job; actions?: JobAction[] }) {
  const move = useMove();
  return (
    <li className="grid gap-3 bg-card p-4 md:grid-cols-[1fr_auto] md:items-center md:gap-6 md:px-5" aria-label={`Job for ${job.customerName}`}>
      <div className="grid min-w-0 gap-1">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <span className="serial text-xs text-ink-soft">No. {billNumber(job.bookingId)}</span>
          <StatusPill status={job.status} />
        </div>
        <h3 className="text-2xl">{job.services ?? "Service"}</h3>
        <p>
          {job.customerName} · {formatDateTime(job.scheduledStart)}
        </p>
        <p className="flex items-center gap-1 text-sm text-ink-soft">
          <MapPin className="size-4 shrink-0" aria-hidden />
          {job.street ? `${job.street}, ${job.areaName}` : `${job.areaName}. Street shown after you accept.`}
        </p>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 md:grid md:justify-items-end">
        <span className="figures text-lg font-semibold">{formatMoney(job.totalAmount)}</span>
        {actions && (
          <div className="flex gap-2">
            {actions.map((a) => (
              <Button
                key={a.move}
                variant={a.primary ? "default" : "outline"}
                disabled={move.isPending}
                onClick={() => move.mutate({ id: job.bookingId, move: a.move })}
              >
                {a.label}
              </Button>
            ))}
          </div>
        )}
      </div>
    </li>
  );
}

function JobList({ status, empty, actions }: { status: "pending" | "completed"; empty: string; actions?: JobAction[] }) {
  const jobs = useQuery({ queryKey: ["jobs", status], queryFn: () => fetchers.jobs(status) });
  if (jobs.isPending) return <RowSkeletons rows={3} className="h-28" />;
  if (jobs.isError) return <ErrorNote error={jobs.error} onRetry={() => jobs.refetch()} />;
  if (jobs.data.data.length === 0) return <EmptyState title={empty} />;
  return (
    <ul className="grid gap-2">
      {jobs.data.data.map((job) => (
        <JobTicket key={job.bookingId} job={job} actions={actions} />
      ))}
    </ul>
  );
}

function Schedule() {
  const confirmed = useQuery({ queryKey: ["jobs", "confirmed"], queryFn: () => fetchers.jobs("confirmed") });
  const running = useQuery({ queryKey: ["jobs", "in_progress"], queryFn: () => fetchers.jobs("in_progress") });
  if (confirmed.isPending || running.isPending) return <RowSkeletons rows={3} className="h-28" />;
  if (confirmed.isError || running.isError) return <ErrorNote error={confirmed.error ?? running.error} />;

  const today = indiaDate();
  const jobs = [...running.data.data, ...confirmed.data.data];
  if (jobs.length === 0) return <EmptyState title="Nothing on your schedule">Jobs you accept will appear here.</EmptyState>;

  const actionsFor = (job: Job): JobAction[] =>
    job.status === "in_progress"
      ? [{ move: "complete", label: "Complete", primary: true }]
      : [{ move: "start", label: "Start", primary: true }];
  const todayJobs = jobs.filter((j) => indiaDate(j.scheduledStart) === today);
  const laterJobs = jobs.filter((j) => indiaDate(j.scheduledStart) !== today);

  return (
    <div className="grid gap-6">
      <section aria-label="Today">
        <h2 className="mb-2 text-2xl">Today</h2>
        {todayJobs.length === 0 ? (
          <p className="text-ink-soft">No jobs today.</p>
        ) : (
          <ul className="grid gap-2">
            {todayJobs.map((job) => (
              <JobTicket key={job.bookingId} job={job} actions={actionsFor(job)} />
            ))}
          </ul>
        )}
      </section>
      {laterJobs.length > 0 && (
        <section aria-label="Other days">
          <h2 className="mb-2 text-2xl">Other days</h2>
          <ul className="grid gap-2">
            {laterJobs.map((job) => (
              <JobTicket key={job.bookingId} job={job} actions={actionsFor(job)} />
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

export default function Jobs() {
  return (
    <div className="wrap py-8 md:py-12">
      <PageHeader title="Jobs" />
      <Tabs defaultValue="inbox" className="gap-5">
        <TabsList>
          <TabsTrigger value="inbox">Inbox</TabsTrigger>
          <TabsTrigger value="schedule">Schedule</TabsTrigger>
          <TabsTrigger value="done">Finished</TabsTrigger>
        </TabsList>
        <TabsContent value="inbox">
          <JobList
            status="pending"
            empty="Your inbox is clear"
            actions={[
              { move: "accept", label: "Accept", primary: true },
              { move: "decline", label: "Decline" },
            ]}
          />
        </TabsContent>
        <TabsContent value="schedule">
          <Schedule />
        </TabsContent>
        <TabsContent value="done">
          <JobList status="completed" empty="No finished jobs yet" />
        </TabsContent>
      </Tabs>
    </div>
  );
}
