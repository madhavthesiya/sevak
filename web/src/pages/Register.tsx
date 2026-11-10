import { useMemo, useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router";
import { errorMessage } from "@/api/client";
import { fetchers } from "@/api/endpoints";
import { HOME_FOR_ROLE, useSession, type Role } from "@/auth/session";
import { FieldError, PageHeader } from "@/components/common";
import { SelectField, TextField } from "@/components/form";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { registerSchema, type RegisterValues } from "@/lib/validation";

export default function Register() {
  const { signIn } = useSession();
  const navigate = useNavigate();
  const [failure, setFailure] = useState<string>();
  const areas = useQuery({ queryKey: ["areas"], queryFn: fetchers.areas, staleTime: 300_000 });
  const cities = useMemo(() => {
    const seen = new Map<number, string>();
    areas.data?.forEach((a) => seen.set(a.cityId, a.cityName));
    return [...seen.entries()];
  }, [areas.data]);

  const form = useForm<RegisterValues>({ resolver: zodResolver(registerSchema), defaultValues: { role: "customer" } });
  const { errors, isSubmitting } = form.formState;
  const role = form.watch("role");

  const submit = form.handleSubmit(async (values) => {
    setFailure(undefined);
    try {
      const { accessToken } = await fetchers.register({
        ...values,
        cityId: values.role === "provider" ? values.cityId : undefined,
      });
      const me = await signIn(accessToken);
      navigate(HOME_FOR_ROLE[me.role as Role], { replace: true });
    } catch (error) {
      setFailure(errorMessage(error));
    }
  });

  return (
    <div className="wrap max-w-lg py-10 md:py-16">
      <PageHeader title="Create an account" />
      <form onSubmit={submit} noValidate className="grid gap-4">
        <fieldset className="grid grid-cols-2 gap-2">
          <legend className="mb-1.5 text-sm font-medium">I want to</legend>
          {(
            [
              ["customer", "Book help"],
              ["provider", "Offer my work"],
            ] as const
          ).map(([value, label]) => (
            <label
              key={value}
              className={cn(
                "flex h-11 cursor-pointer items-center justify-center rounded-md border border-input font-medium",
                "has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-ring",
                role === value ? "border-ink bg-ink text-white" : "bg-card",
              )}
            >
              <input type="radio" value={value} className="sr-only" {...form.register("role")} />
              {label}
            </label>
          ))}
        </fieldset>
        <TextField label="Full name" autoComplete="name" error={errors.fullName?.message} {...form.register("fullName")} />
        <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...form.register("email")} />
        <TextField label="Password" type="password" autoComplete="new-password" hint="At least 8 characters" error={errors.password?.message} {...form.register("password")} />
        {role === "provider" && (
          <>
            <SelectField
              label="City you work in"
              error={errors.cityId?.message}
              {...form.register("cityId", { setValueAs: (v) => (v === "" || v === undefined ? undefined : Number(v)) })}
            >
              <option value="">Choose a city</option>
              {cities.map(([id, name]) => (
                <option key={id} value={id}>
                  {name}
                </option>
              ))}
            </SelectField>
            <p className="text-sm text-ink-soft">An admin verifies each new provider before they appear in search.</p>
          </>
        )}
        <div role="alert">
          <FieldError message={failure} />
        </div>
        <Button type="submit" size="lg" disabled={isSubmitting}>
          {isSubmitting ? "Creating your account…" : "Create account"}
        </Button>
        <p className="text-ink-soft">
          Already registered?{" "}
          <Link className="font-medium text-auto-green underline underline-offset-2" to="/login">
            Log in
          </Link>
        </p>
      </form>
    </div>
  );
}
