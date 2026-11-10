// One typed function per API call. Types are generated from the API's OpenAPI document, so renaming a
// field on the server breaks this file at compile time. springdoc marks most response fields optional;
// `Filled` restores what the database guarantees and leaves the genuinely nullable ones optional.
import { api, call } from "./client";
import type { components } from "./schema";

type S = components["schemas"];
type Filled<T, Nullable extends keyof T = never> = Required<Omit<T, Nullable>> & Pick<T, Nullable>;
export type Page<T> = { data: T[]; page: number; limit: number; total: number; totalPages: number };

export type Me = Filled<S["MeResponse"], "phone">;
export type Token = Filled<S["TokenResponse"]>;
export type ServiceItem = Filled<S["ServiceItem"], "description">;
export type Area = Filled<S["AreaOption"]>;
export type Offering = Filled<S["ProviderOffering"], "avgRating" | "experienceYears">;
export type TopProvider = Filled<S["TopProvider"], "avgRating" | "weightedRating" | "experienceYears">;
export type Basics = Filled<S["ProviderBasics"], "bio" | "experienceYears" | "avgRating" | "cityAverageRating">;
export type OfferedService = Filled<S["OfferedService"]>;
export type OfferedVariant = Filled<S["OfferedVariant"]>;
export type WeeklySlot = Filled<S["AvailabilitySlot"]>;
export type Profile = { provider: Basics; services: OfferedService[]; availability: WeeklySlot[]; variants: OfferedVariant[] };
export type Review = Filled<S["ProviderReview"], "comment">;
export type FreeSlot = Filled<S["FreeSlot"]>;
export type Quote = Filled<S["BookingQuote"], "couponCode">;
export type Address = Filled<S["SavedAddress"], "label" | "houseNo" | "landmark" | "pincode">;
export type BookingSummary = Filled<S["BookingSummary"], "services">;
export type BookingHeader = Filled<
  S["BookingHeader"],
  "couponCode" | "specialInstructions" | "houseNo" | "paymentStatus"
>;
export type BookingLine = Filled<S["BookingLine"], "variantName">;
export type StatusChange = Filled<S["StatusChange"], "oldStatus" | "remarks">;
export type BookingDetail = { booking: BookingHeader; items: BookingLine[]; timeline: StatusChange[] };
export type Job = Filled<S["JobSummary"], "street" | "paymentStatus" | "services">;
export type EarningsByMethod = Filled<S["EarningsByMethod"]>;
export type MonthlyEarnings = Filled<S["MonthlyEarnings"]>;
export type OwnSlot = Filled<S["OwnAvailabilitySlot"]>;
export type CategoryRevenue = Filled<S["CategoryRevenue"]>;
export type CityRevenue = Filled<S["CityRevenue"]>;
export type LeaderboardEntry = Filled<S["LeaderboardEntry"], "avgRating" | "weightedRating">;
export type ComplaintSummary = Filled<S["ComplaintSummary"]>;
export type Complaint = Filled<S["ComplaintBoardItem"], "description" | "assignedAdminName" | "resolutionNotes">;
export type Candidate = Filled<S["VerificationCandidate"], "bio" | "experienceYears" | "documents">;
export type QueryInfo = Filled<S["QueryInfo"], "measuredMs">;
export type ExplainResult = Filled<S["ExplainResult"]>;
type BookingItem = { serviceId: number; variantId?: number; quantity: number };
const P = (id: number) => ({ params: { path: { id } } });

export const fetchers = {
  // Accounts
  login: (body: { email: string; password: string }) => call(api.POST("/api/v1/auth/login", { body })) as Promise<Token>,
  register: (body: S["RegisterRequest"]) => call(api.POST("/api/v1/auth/register", { body })) as Promise<Token>,
  me: () => call(api.GET("/api/v1/me")) as Promise<Me>,

  // Public
  services: () => call(api.GET("/api/v1/services", { params: { query: { limit: 100 } } })) as Promise<Page<ServiceItem>>,
  areas: () => call(api.GET("/api/v1/areas")) as Promise<Area[]>,
  search: (query: { service?: string; city?: string; day?: string; page?: number; limit?: number }) =>
    call(api.GET("/api/v1/providers", { params: { query } })) as Promise<Page<Offering>>,
  top: (limit: number) => call(api.GET("/api/v1/providers/top", { params: { query: { limit } } })) as Promise<TopProvider[]>,
  profile: (id: number) => call(api.GET("/api/v1/providers/{id}", P(id))) as Promise<Profile>,
  reviews: (id: number, limit = 10) =>
    call(api.GET("/api/v1/providers/{id}/reviews", { params: { path: { id }, query: { limit } } })) as Promise<Page<Review>>,
  slots: (id: number, from: string, days: number, minutes: number) =>
    call(api.GET("/api/v1/providers/{id}/slots", { params: { path: { id }, query: { from, days, minutes } } })) as Promise<FreeSlot[]>,
  query: (key: string) => call(api.GET("/api/v1/meta/queries/{key}", { params: { path: { key } } })) as Promise<QueryInfo>,

  // Customer
  quote: (body: { providerId: number; couponCode?: string; items: BookingItem[] }) =>
    call(api.POST("/api/v1/bookings/quote", { body })) as Promise<Quote>,
  createBooking: (body: S["CreateBookingRequest"], idempotencyKey: string) =>
    call(api.POST("/api/v1/bookings", { body, params: { header: { "Idempotency-Key": idempotencyKey } } })) as Promise<
      Filled<S["BookingCreated"]>
    >,
  myBookings: () => call(api.GET("/api/v1/me/bookings", { params: { query: { limit: 100 } } })) as Promise<Page<BookingSummary>>,
  myBooking: (bookingId: number) =>
    call(api.GET("/api/v1/me/bookings/{bookingId}", { params: { path: { bookingId } } })) as Promise<BookingDetail>,
  addresses: () => call(api.GET("/api/v1/me/addresses")) as Promise<Address[]>,
  addAddress: (body: S["NewAddressRequest"]) => call(api.POST("/api/v1/me/addresses", { body })),
  removeAddress: (locationId: number) =>
    call(api.DELETE("/api/v1/me/addresses/{locationId}", { params: { path: { locationId } } })),
  cancel: (id: number, reason: string) => call(api.POST("/api/v1/bookings/{id}/cancel", { ...P(id), body: { reason } })),
  pay: (id: number, body: { paymentMethod: string; simulate?: string }) =>
    call(api.POST("/api/v1/bookings/{id}/payments", { ...P(id), body })),
  review: (id: number, body: { rating: number; comment?: string }) =>
    call(api.POST("/api/v1/bookings/{id}/review", { ...P(id), body })),

  // Provider
  ownProfile: () => call(api.GET("/api/v1/provider/profile")) as Promise<Profile>,
  jobs: (status: "pending" | "confirmed" | "in_progress" | "completed" | "cancelled") =>
    call(api.GET("/api/v1/provider/jobs", { params: { query: { status, limit: 50 } } })) as Promise<Page<Job>>,
  accept: (id: number) => call(api.POST("/api/v1/bookings/{id}/accept", P(id))),
  decline: (id: number) => call(api.POST("/api/v1/bookings/{id}/decline", P(id))),
  start: (id: number) => call(api.POST("/api/v1/bookings/{id}/start", P(id))),
  complete: (id: number) => call(api.POST("/api/v1/bookings/{id}/complete", P(id))),
  earnings: () => call(api.GET("/api/v1/provider/earnings", {})) as Promise<EarningsByMethod[]>,
  monthlyEarnings: () => call(api.GET("/api/v1/provider/earnings/monthly")) as Promise<MonthlyEarnings[]>,
  ownSlots: () => call(api.GET("/api/v1/provider/availability")) as Promise<OwnSlot[]>,
  addSlot: (body: S["NewAvailabilityRequest"]) => call(api.POST("/api/v1/provider/availability", { body })),
  removeSlot: (slotId: number) =>
    call(api.DELETE("/api/v1/provider/availability/{slotId}", { params: { path: { slotId } } })),
  ownServices: () => call(api.GET("/api/v1/provider/services")) as Promise<OfferedService[]>,
  offerService: (body: S["OwnServiceRequest"]) => call(api.POST("/api/v1/provider/services", { body })),
  stopOffering: (serviceId: number) =>
    call(api.DELETE("/api/v1/provider/services/{serviceId}", { params: { path: { serviceId } } })),

  // Admin
  revenue: () => call(api.GET("/api/v1/admin/revenue")) as Promise<CategoryRevenue[]>,
  cityRevenue: () => call(api.GET("/api/v1/admin/city-revenue")) as Promise<CityRevenue[]>,
  leaderboard: (fast: boolean) =>
    call(api.GET("/api/v1/admin/leaderboard", { params: { query: { fast } } })) as Promise<LeaderboardEntry[]>,
  refreshViews: () => call(api.POST("/api/v1/admin/refresh-views")) as Promise<Filled<S["RefreshResult"]>>,
  complaintBoard: () => call(api.GET("/api/v1/admin/complaints/board")) as Promise<Complaint[]>,
  complaintSummary: () => call(api.GET("/api/v1/admin/complaints")) as Promise<ComplaintSummary[]>,
  updateComplaint: (id: number, body: S["ComplaintUpdateRequest"]) =>
    call(api.PATCH("/api/v1/admin/complaints/{id}", { ...P(id), body })),
  candidates: (status: "pending" | "verified" | "rejected") =>
    call(api.GET("/api/v1/admin/providers", { params: { query: { status } } })) as Promise<Candidate[]>,
  verify: (id: number, status: "verified" | "rejected") =>
    call(api.PATCH("/api/v1/admin/providers/{id}/verification", { ...P(id), body: { status } })),
  explain: (key: string) =>
    call(api.POST("/api/v1/admin/queries/{key}/explain", { params: { path: { key } } })) as Promise<ExplainResult>,
};
