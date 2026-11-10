import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";
import { errorMessage } from "@/api/client";
import { fetchers } from "@/api/endpoints";
import { HOME_FOR_ROLE, useSession, type Role } from "@/auth/session";
import { FieldError } from "@/components/common";
import { TextField } from "@/components/form";
import { Button } from "@/components/ui/button";
import { safeNext } from "@/lib/navigation";
import { loginSchema, type LoginValues } from "@/lib/validation";

export default function Login() {
  const session = useSession();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = safeNext(params.get("next"));
  const [failure, setFailure] = useState<string>();
  const [demoBusy, setDemoBusy] = useState(false);
  const form = useForm<LoginValues>({ resolver: zodResolver(loginSchema) });
  const { errors, isSubmitting } = form.formState;

  if (session.role && !isSubmitting && !demoBusy) return <Navigate to={next ?? HOME_FOR_ROLE[session.role]} replace />;

  const finish = (role: Role) => navigate(next ?? HOME_FOR_ROLE[role], { replace: true });

  const submit = form.handleSubmit(async (values) => {
    setFailure(undefined);
    try {
      const { accessToken } = await fetchers.login(values);
      const me = await session.signIn(accessToken);
      finish(me.role as Role);
    } catch (error) {
      setFailure(errorMessage(error));
    }
  });

  async function demo(role: Role) {
    setDemoBusy(true);
    try {
      await session.signInAsDemo(role);
      finish(role);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setDemoBusy(false);
    }
  }

  return (
    <div className="wrap grid max-w-4xl gap-10 py-10 md:grid-cols-2 md:py-16">
      <form onSubmit={submit} noValidate className="grid content-start gap-4">
        <h1 className="text-5xl">Log in</h1>
        {params.get("expired") && <p role="status">Your session ended. Please log in again.</p>}
        <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...form.register("email")} />
        <TextField label="Password" type="password" autoComplete="current-password" error={errors.password?.message} {...form.register("password")} />
        <div role="alert">
          <FieldError message={failure} />
        </div>
        <Button type="submit" size="lg" disabled={isSubmitting}>
          {isSubmitting ? "Logging in…" : "Log in"}
        </Button>
        <p className="text-ink-soft">
          New here?{" "}
          <Link className="font-medium text-auto-green underline underline-offset-2" to="/register">
            Create an account
          </Link>
        </p>
      </form>

      <section className="grid content-start gap-3 bg-auto-yellow p-5 md:p-6">
        <h2 className="text-3xl">Just looking?</h2>
        <p>Use a demo account. No sign-up, and the data resets every night.</p>
        {(["customer", "provider", "admin"] as Role[]).map((role) => (
          <Button
            key={role}
            variant="outline"
            className="justify-start border-ink bg-transparent hover:bg-black/8"
            disabled={demoBusy}
            onClick={() => demo(role)}
          >
            Log in as {role}
          </Button>
        ))}
      </section>
    </div>
  );
}
