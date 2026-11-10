import { useState } from "react";
import { Code2, Menu } from "lucide-react";
import { Link, NavLink, Outlet } from "react-router";
import { useSession, type Role } from "@/auth/session";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { cn } from "@/lib/utils";
import { useSqlDrawer } from "@/sql/SqlDrawer";

const NAV: Record<Role | "guest", { to: string; label: string }[]> = {
  guest: [{ to: "/search", label: "Find a pro" }],
  customer: [
    { to: "/search", label: "Find a pro" },
    { to: "/bookings", label: "My bookings" },
    { to: "/addresses", label: "Addresses" },
  ],
  provider: [
    { to: "/provider/jobs", label: "Jobs" },
    { to: "/provider/earnings", label: "Earnings" },
    { to: "/provider/reviews", label: "Reviews" },
    { to: "/provider/availability", label: "Hours & services" },
  ],
  admin: [
    { to: "/admin/revenue", label: "Revenue" },
    { to: "/admin/leaderboard", label: "Leaderboard" },
    { to: "/admin/complaints", label: "Complaints" },
    { to: "/admin/verification", label: "Verification" },
  ],
};

export function Wordmark() {
  return (
    <Link to="/" className="flex items-baseline gap-2 text-ink" aria-label="SEVAK home">
      <span className="font-display text-[1.9rem] leading-none font-extrabold tracking-tight" style={{ fontStretch: "75%" }}>
        SEVAK
      </span>
    </Link>
  );
}

export function AppShell() {
  const session = useSession();
  const { open, count } = useSqlDrawer();
  const [menuOpen, setMenuOpen] = useState(false);
  const links = NAV[session.role ?? "guest"];

  const navClass = ({ isActive }: { isActive: boolean }) =>
    cn(
      "px-3 py-2 text-[0.95rem] font-medium",
      isActive ? "bg-ink text-white" : "text-ink hover:bg-black/8",
    );

  return (
    <div className="flex min-h-dvh flex-col">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:bg-white focus:p-3"
      >
        Skip to content
      </a>
      <p className="bg-bottle py-1 text-center text-[0.8rem] text-white">
        Demo data resets nightly. Bookings and payments here are simulated.
      </p>

      <header className="sticky top-0 z-30 border-b border-black/10 bg-auto-yellow">
        <div className="wrap flex h-16 items-center gap-3">
          <Wordmark />
          <nav aria-label="Main" className="ml-4 hidden items-center gap-1 md:flex">
            {links.map((link) => (
              <NavLink key={link.to} to={link.to} className={navClass}>
                {link.label}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              className="border-ink bg-transparent hover:bg-black/8"
              disabled={count === 0}
              onClick={() => open()}
              aria-label="Show the SQL behind this screen"
            >
              <Code2 aria-hidden /> SQL
            </Button>
            <div className="hidden items-center gap-2 md:flex">
              {session.me ? (
                <>
                  <span className="max-w-40 truncate text-sm font-medium">{session.me.fullName}</span>
                  <Button variant="ghost" size="sm" className="hover:bg-black/8" onClick={session.signOut}>
                    Log out
                  </Button>
                </>
              ) : (
                <Button asChild size="sm" className="bg-ink hover:bg-bottle">
                  <Link to="/login">Log in</Link>
                </Button>
              )}
            </div>

            <Sheet open={menuOpen} onOpenChange={setMenuOpen}>
              <SheetTrigger asChild>
                <Button variant="ghost" size="icon" className="hover:bg-black/8 md:hidden" aria-label="Open menu">
                  <Menu aria-hidden />
                </Button>
              </SheetTrigger>
              <SheetContent side="right" className="w-72 p-5">
                <SheetTitle className="font-display text-2xl">Menu</SheetTitle>
                <SheetDescription className="sr-only">Pages you can open</SheetDescription>
                <nav aria-label="Mobile" className="mt-2 grid gap-1">
                  {links.map((link) => (
                    <NavLink key={link.to} to={link.to} className={navClass} onClick={() => setMenuOpen(false)}>
                      {link.label}
                    </NavLink>
                  ))}
                </nav>
                <div className="mt-auto border-t pt-4">
                  {session.me ? (
                    <div className="grid gap-2">
                      <p className="text-sm font-medium">{session.me.fullName}</p>
                      <Button variant="outline" onClick={session.signOut}>
                        Log out
                      </Button>
                    </div>
                  ) : (
                    <Button asChild className="w-full">
                      <Link to="/login" onClick={() => setMenuOpen(false)}>
                        Log in
                      </Link>
                    </Button>
                  )}
                </div>
              </SheetContent>
            </Sheet>
          </div>
        </div>
      </header>

      <main id="main" className="min-h-screen flex-1">
        <Outlet />
      </main>

      <footer className="bg-bottle py-6 text-sm text-bottle-soft">
        <div className="wrap flex flex-wrap items-center justify-between gap-2">
          <span>SEVAK is a portfolio project, built on PostgreSQL and Spring Boot.</span>
          <a className="text-white underline underline-offset-2" href="https://github.com/madhavthesiya/sevak">
            Source on GitHub
          </a>
        </div>
      </footer>
    </div>
  );
}
