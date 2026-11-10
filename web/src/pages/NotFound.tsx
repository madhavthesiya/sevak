import { EmptyState } from "@/components/common";

export default function NotFound() {
  return (
    <div className="wrap py-14">
      <EmptyState title="We couldn't find that page" action={{ to: "/", label: "Go to the home page" }}>
        The link may be old, or the page may have moved.
      </EmptyState>
    </div>
  );
}
