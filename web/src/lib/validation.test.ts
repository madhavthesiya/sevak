import { addressSchema, bookingSchema, hoursSchema, loginSchema, placeholderCoordinates, registerSchema } from "./validation";

const firstError = (result: { success: boolean; error?: { issues: { message: string }[] } }) =>
  result.error?.issues[0]?.message;

describe("login", () => {
  it("rejects a malformed email", () => {
    expect(firstError(loginSchema.safeParse({ email: "ravi", password: "x" }))).toBe("Enter a valid email address");
  });
});

describe("register", () => {
  const base = { fullName: "Ravi Kumar", email: "r@example.com", password: "long-enough" };
  it("lets a customer skip the city", () => {
    expect(registerSchema.safeParse({ ...base, role: "customer" }).success).toBe(true);
  });
  it("makes a provider choose a city", () => {
    expect(firstError(registerSchema.safeParse({ ...base, role: "provider" }))).toBe("Choose the city you work in");
  });
  it("needs eight characters in a password", () => {
    expect(firstError(registerSchema.safeParse({ ...base, password: "short", role: "customer" }))).toBe(
      "Use at least 8 characters",
    );
  });
});

describe("address", () => {
  const good = { areaId: 1, label: "Home", houseNo: "Flat 1", street: "12, Nehru Road" };
  it("accepts a complete address", () => {
    expect(addressSchema.safeParse(good).success).toBe(true);
  });
  it("asks for a label", () => {
    expect(firstError(addressSchema.safeParse({ ...good, label: "  " }))).toContain("Give this address a name");
  });
});

describe("booking", () => {
  const good = {
    items: [{ serviceId: 1, quantity: 1 }],
    slot: "2026-12-01T04:30:00Z",
    locationId: 1,
  };
  it("accepts a complete booking", () => {
    expect(bookingSchema.safeParse(good).success).toBe(true);
  });
  it("needs a service", () => {
    expect(firstError(bookingSchema.safeParse({ ...good, items: [] }))).toBe("Choose at least one service");
  });
  it("needs a time and an address", () => {
    expect(firstError(bookingSchema.safeParse({ ...good, slot: undefined }))).toBe("Choose a time");
    expect(firstError(bookingSchema.safeParse({ ...good, locationId: undefined }))).toBe("Choose an address");
  });
  it("limits quantity to what the API allows", () => {
    expect(bookingSchema.safeParse({ ...good, items: [{ serviceId: 1, quantity: 21 }] }).success).toBe(false);
  });
});

describe("working hours", () => {
  it("rejects an end before the start", () => {
    expect(firstError(hoursSchema.safeParse({ dayOfWeek: 1, startTime: "15:00", endTime: "09:00" }))).toContain(
      "after the start",
    );
  });
});

describe("placeholder coordinates", () => {
  it("gives one street one location, however it is typed", () => {
    expect(placeholderCoordinates(1, "12, Nehru Road", "A1")).toEqual(placeholderCoordinates(1, " 12, nehru road ", "a1"));
  });
  it("separates different flats", () => {
    expect(placeholderCoordinates(1, "12, Nehru Road", "A1")).not.toEqual(placeholderCoordinates(1, "12, Nehru Road", "A2"));
  });
  it("stays inside the range the API accepts", () => {
    const { latitude, longitude } = placeholderCoordinates(9, "anything at all", "7");
    expect(Math.abs(latitude)).toBeLessThanOrEqual(90);
    expect(Math.abs(longitude)).toBeLessThanOrEqual(180);
  });
});
