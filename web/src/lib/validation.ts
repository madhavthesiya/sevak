import { z } from "zod";

export const loginSchema = z.object({
  email: z.email("Enter a valid email address"),
  password: z.string().min(1, "Enter your password"),
});
export type LoginValues = z.infer<typeof loginSchema>;

export const registerSchema = z
  .object({
    fullName: z.string().trim().min(2, "Enter your full name").max(100),
    email: z.email("Enter a valid email address"),
    password: z.string().min(8, "Use at least 8 characters").max(72, "Use 72 characters or fewer"),
    role: z.enum(["customer", "provider"]),
    cityId: z.number().optional(),
    experienceYears: z.number().int().min(0).max(80).optional(),
  })
  .refine((v) => v.role !== "provider" || v.cityId !== undefined, {
    path: ["cityId"],
    message: "Choose the city you work in",
  });
export type RegisterValues = z.infer<typeof registerSchema>;

export const addressSchema = z.object({
  areaId: z.number({ error: "Choose an area" }),
  label: z.string().trim().min(1, "Give this address a name, such as Home").max(50),
  houseNo: z.string().trim().min(1, "Enter your flat or house number").max(50),
  street: z.string().trim().min(3, "Enter the street").max(255),
  landmark: z.string().trim().max(255).optional(),
});
export type AddressValues = z.infer<typeof addressSchema>;

export const bookingSchema = z.object({
  items: z
    .array(
      z.object({
        serviceId: z.number(),
        variantId: z.number().optional(),
        quantity: z.number().int().min(1).max(20),
      }),
    )
    .min(1, "Choose at least one service"),
  slot: z.string({ error: "Choose a time" }).min(1, "Choose a time"),
  locationId: z.number({ error: "Choose an address" }),
  specialInstructions: z.string().max(1000, "Keep instructions under 1,000 characters").optional(),
});
export type BookingValues = z.infer<typeof bookingSchema>;

export const hoursSchema = z
  .object({
    dayOfWeek: z.number().int().min(1).max(7),
    startTime: z.string().min(1, "Choose a start time"),
    endTime: z.string().min(1, "Choose an end time"),
  })
  .refine((v) => !v.startTime || !v.endTime || v.endTime > v.startTime, {
    path: ["endTime"],
    message: "The end time must be after the start time",
  });
export type HoursValues = z.infer<typeof hoursSchema>;

export const reviewSchema = z.object({
  rating: z.number().int().min(1, "Choose a rating").max(5),
  comment: z.string().max(2000).optional(),
});

/**
 * The API stores a unique latitude and longitude for each address, but this app has no maps service.
 * This makes a stable placeholder inside India, so one street always maps to one location row.
 */
export function placeholderCoordinates(areaId: number, street: string, houseNo: string) {
  let hash = 2166136261;
  for (const char of `${areaId}|${street.trim().toLowerCase()}|${houseNo.trim().toLowerCase()}`) {
    hash ^= char.charCodeAt(0);
    hash = Math.imul(hash, 16777619);
  }
  const unsigned = hash >>> 0;
  return {
    latitude: Number((10 + (unsigned % 1_500_000) / 100_000).toFixed(6)),
    longitude: Number((72 + ((unsigned >>> 8) % 1_300_000) / 100_000).toFixed(6)),
  };
}
