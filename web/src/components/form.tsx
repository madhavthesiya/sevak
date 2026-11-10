import { useId, type ComponentProps, type ReactNode } from "react";
import { FieldError } from "@/components/common";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

type Common = { label: string; error?: string; hint?: string };

function Wrapper({ id, label, error, hint, children }: Common & { id: string; children: ReactNode }) {
  return (
    <div className="grid min-w-0 content-start gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {hint && !error && <p className="text-sm text-ink-soft">{hint}</p>}
      <FieldError id={`${id}-error`} message={error} />
    </div>
  );
}

export function TextField({ label, error, hint, ...props }: Common & ComponentProps<typeof Input>) {
  const id = useId();
  return (
    <Wrapper id={id} label={label} error={error} hint={hint}>
      <Input id={id} aria-invalid={!!error} aria-describedby={error ? `${id}-error` : undefined} {...props} />
    </Wrapper>
  );
}

export function TextAreaField({ label, error, hint, ...props }: Common & ComponentProps<typeof Textarea>) {
  const id = useId();
  return (
    <Wrapper id={id} label={label} error={error} hint={hint}>
      <Textarea id={id} aria-invalid={!!error} {...props} />
    </Wrapper>
  );
}

/** A native select: phones show their own picker, which beats any custom dropdown there. */
export function SelectField({ label, error, hint, className, children, ...props }: Common & ComponentProps<"select">) {
  const id = useId();
  return (
    <Wrapper id={id} label={label} error={error} hint={hint}>
      <select
        id={id}
        aria-invalid={!!error}
        className={cn("h-11 w-full min-w-0 rounded-md border border-input bg-card px-3 text-base", className)}
        {...props}
      >
        {children}
      </select>
    </Wrapper>
  );
}
