import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { errorMessage } from "@/api/client";
import { fetchers } from "@/api/endpoints";
import { EmptyState, ErrorNote, Monogram, PageHeader, RowSkeletons } from "@/components/common";
import { Button } from "@/components/ui/button";

export default function Verification() {
  const client = useQueryClient();
  const queue = useQuery({ queryKey: ["admin", "candidates"], queryFn: () => fetchers.candidates("pending") });
  const decide = useMutation({
    mutationFn: ({ id, status }: { id: number; status: "verified" | "rejected" }) => fetchers.verify(id, status),
    onSuccess: (_, { status }) => {
      toast.success(status === "verified" ? "Provider verified." : "Provider rejected.");
      client.invalidateQueries({ queryKey: ["admin", "candidates"] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <div className="wrap py-8 md:py-12">
      <PageHeader title="Verification queue" intro="Providers appear in search only after you verify them." />
      {queue.isPending ? (
        <RowSkeletons rows={2} className="h-28" />
      ) : queue.isError ? (
        <ErrorNote error={queue.error} onRetry={() => queue.refetch()} />
      ) : queue.data.length === 0 ? (
        <EmptyState title="The queue is clear">New provider sign-ups will wait here for a decision.</EmptyState>
      ) : (
        <ul className="grid gap-2">
          {queue.data.map((c) => (
            <li key={c.providerId} className="grid gap-3 bg-card p-4 md:grid-cols-[auto_1fr_auto] md:items-center md:gap-5">
              <Monogram name={c.providerName} />
              <div className="grid min-w-0 gap-0.5">
                <h2 className="text-2xl">{c.providerName}</h2>
                <p className="text-sm text-ink-soft">
                  {c.cityName}
                  {c.experienceYears != null && ` · ${c.experienceYears} years' experience`}
                </p>
                {c.bio && <p>{c.bio}</p>}
                <p className="text-sm">{c.documents ? `Documents: ${c.documents}` : "No documents submitted"}</p>
              </div>
              <div className="flex gap-2">
                <Button disabled={decide.isPending} aria-label={`Verify ${c.providerName}`} onClick={() => decide.mutate({ id: c.providerId, status: "verified" })}>
                  Verify
                </Button>
                <Button variant="outline" disabled={decide.isPending} aria-label={`Reject ${c.providerName}`} onClick={() => decide.mutate({ id: c.providerId, status: "rejected" })}>
                  Reject
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
