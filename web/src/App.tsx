import { lazy, Suspense, type ReactNode } from "react";
import { Navigate, Route, Routes } from "react-router";
import { RequireRole, type Role } from "@/auth/session";
import { AppShell } from "@/components/AppShell";
import { RowSkeletons } from "@/components/common";
import { Home } from "@/pages/Home";

// Each screen is its own chunk, so a visitor only downloads what they open.
const Login = lazy(() => import("@/pages/Login"));
const Register = lazy(() => import("@/pages/Register"));
const Search = lazy(() => import("@/pages/Search"));
const ProviderProfile = lazy(() => import("@/pages/ProviderProfile"));
const NotFound = lazy(() => import("@/pages/NotFound"));
const Book = lazy(() => import("@/pages/customer/Book"));
const Bookings = lazy(() => import("@/pages/customer/Bookings"));
const BookingDetail = lazy(() => import("@/pages/customer/BookingDetail"));
const Addresses = lazy(() => import("@/pages/customer/Addresses"));
const Jobs = lazy(() => import("@/pages/provider/Jobs"));
const Earnings = lazy(() => import("@/pages/provider/Earnings"));
const Reviews = lazy(() => import("@/pages/provider/Reviews"));
const Availability = lazy(() => import("@/pages/provider/Availability"));
const Revenue = lazy(() => import("@/pages/admin/Revenue"));
const Leaderboard = lazy(() => import("@/pages/admin/Leaderboard"));
const Complaints = lazy(() => import("@/pages/admin/Complaints"));
const Verification = lazy(() => import("@/pages/admin/Verification"));

function Screen({ children, role }: { children: ReactNode; role?: Role }) {
  const page = (
    <Suspense
      fallback={
        <div className="wrap py-10">
          <RowSkeletons />
        </div>
      }
    >
      {children}
    </Suspense>
  );
  return role ? <RequireRole role={role}>{page}</RequireRole> : page;
}

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<Home />} />
        <Route path="login" element={<Screen><Login /></Screen>} />
        <Route path="register" element={<Screen><Register /></Screen>} />
        <Route path="search" element={<Screen><Search /></Screen>} />
        <Route path="providers/:id" element={<Screen><ProviderProfile /></Screen>} />

        <Route path="book/:providerId" element={<Screen role="customer"><Book /></Screen>} />
        <Route path="bookings" element={<Screen role="customer"><Bookings /></Screen>} />
        <Route path="bookings/:id" element={<Screen role="customer"><BookingDetail /></Screen>} />
        <Route path="addresses" element={<Screen role="customer"><Addresses /></Screen>} />

        <Route path="provider">
          <Route index element={<Navigate to="jobs" replace />} />
          <Route path="jobs" element={<Screen role="provider"><Jobs /></Screen>} />
          <Route path="earnings" element={<Screen role="provider"><Earnings /></Screen>} />
          <Route path="reviews" element={<Screen role="provider"><Reviews /></Screen>} />
          <Route path="availability" element={<Screen role="provider"><Availability /></Screen>} />
        </Route>

        <Route path="admin">
          <Route index element={<Navigate to="revenue" replace />} />
          <Route path="revenue" element={<Screen role="admin"><Revenue /></Screen>} />
          <Route path="leaderboard" element={<Screen role="admin"><Leaderboard /></Screen>} />
          <Route path="complaints" element={<Screen role="admin"><Complaints /></Screen>} />
          <Route path="verification" element={<Screen role="admin"><Verification /></Screen>} />
        </Route>

        <Route path="*" element={<Screen><NotFound /></Screen>} />
      </Route>
    </Routes>
  );
}
