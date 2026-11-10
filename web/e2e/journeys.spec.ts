import { expect, test, type Page } from "@playwright/test";

const API = process.env.VITE_API_URL ?? "http://localhost:8080";

async function demoLogin(page: Page, role: "customer" | "provider" | "admin") {
  await page.goto("/");
  await page.getByRole("button", { name: `Log in as ${role}` }).click();
}

test.describe.serial("a booking from both sides", () => {
  let bookingId = "";

  test("customer logs in, books, and sees it in My Bookings", async ({ page }) => {
    await demoLogin(page, "customer");
    await expect(page).toHaveURL(/\/bookings$/);

    await page.goto("/search?service=Fan");
    await page.getByRole("link", { name: "Book Fan Installation with Ravi Kumar" }).click();
    await expect(page.getByRole("heading", { name: "Book Ravi Kumar" })).toBeVisible();

    // Only free times are offered, so the first one must be bookable.
    await page.getByRole("group", { name: "Start time" }).getByRole("button").first().click();
    await page.getByRole("radiogroup", { name: "Address" }).locator("label").first().click();
    await expect(page.getByTestId("total")).toHaveText(/₹/);

    await page.getByRole("button", { name: "Confirm booking" }).click();
    await expect(page).toHaveURL(/\/bookings\/\d+$/);
    bookingId = page.url().split("/").pop()!;
    await expect(page.getByRole("heading", { name: "Ravi Kumar" })).toBeVisible();
    await expect(page.getByText("Waiting for reply").first()).toBeVisible();

    await page.getByRole("link", { name: "My bookings" }).first().click();
    await expect(page.locator(`a[href="/bookings/${bookingId}"]`)).toBeVisible();
  });

  test("provider logs in and accepts the job", async ({ page }) => {
    await demoLogin(page, "provider");
    await expect(page).toHaveURL(/\/provider\/jobs$/);

    const job = page.getByRole("listitem", { name: "Job for Aarav Sharma" }).filter({ hasText: `No. ${bookingId.padStart(5, "0")}` });
    await job.getByRole("button", { name: "Accept" }).click();
    await expect(page.getByText("Job accepted.")).toBeVisible();

    await page.getByRole("tab", { name: "Schedule" }).click();
    await expect(page.getByText(`No. ${bookingId.padStart(5, "0")}`)).toBeVisible();
  });
});

test("a coupon changes the live bill, and a bad one explains itself", async ({ page, request }) => {
  const email = `e2e.${Date.now()}@example.com`;
  const registered = await request.post(`${API}/api/v1/auth/register`, {
    data: { email, password: "a-long-password", fullName: "Test Customer", role: "customer" },
  });
  expect(registered.ok()).toBeTruthy();
  const { accessToken } = await registered.json();
  const added = await request.post(`${API}/api/v1/me/addresses`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: { areaId: 1, street: "1, Test Road", latitude: 20 + Math.random(), longitude: 75 + Math.random(), label: "Home", houseNo: "1" },
  });
  expect(added.ok()).toBeTruthy();
  await page.addInitScript((token) => localStorage.setItem("sevak.token", token), accessToken);

  await page.goto("/book/2?service=3");
  await expect(page.getByTestId("total")).toHaveText("₹1,500.00");

  await page.getByLabel("Coupon code").fill("NOTREAL");
  await page.getByRole("button", { name: "Apply" }).click();
  await expect(page.getByText(/invalid, used up/)).toBeVisible();
  await expect(page.getByTestId("total")).toHaveText("₹1,500.00");

  await page.getByLabel("Coupon code").fill("NEWUSER25");
  await page.getByRole("button", { name: "Apply" }).click();
  await expect(page.getByTestId("discount")).toHaveText("−₹375.00");
  await expect(page.getByTestId("total")).toHaveText("₹1,125.00");
});

test("overlapping working hours get a friendly message", async ({ page }) => {
  await demoLogin(page, "provider");
  await page.getByRole("link", { name: "Hours & services" }).click();
  await expect(page.getByRole("heading", { name: "Working hours" })).toBeVisible();

  await page.getByLabel("From", { exact: true }).fill("12:00");
  await page.getByLabel("To", { exact: true }).fill("15:00");
  await page.getByRole("button", { name: "Add hours" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "overlap" })).toBeVisible();
});

test("the SQL drawer shows the query behind a screen", async ({ page }) => {
  await page.goto("/search");
  await expect(page.getByText(/best rated first/)).toBeVisible();
  await page.getByRole("button", { name: "Show the SQL behind this screen" }).click();
  const drawer = page.getByRole("dialog");
  await expect(drawer.getByRole("heading", { name: "The SQL behind this screen" })).toBeVisible();
  await expect(drawer.getByLabel("SQL query")).toContainText("provider_services");
  await expect(drawer.getByRole("button", { name: "Run EXPLAIN" })).toHaveCount(0);
});

test("an admin sees the charts and can run EXPLAIN", async ({ page }) => {
  await demoLogin(page, "admin");
  await expect(page).toHaveURL(/\/admin\/revenue$/);
  await expect(page.getByRole("heading", { name: "By category" })).toBeVisible();
  await expect(page.locator(".recharts-bar-rectangle").first()).toBeVisible();

  await page.getByRole("button", { name: "Show the SQL behind this screen" }).click();
  const drawer = page.getByRole("dialog");
  await drawer.getByRole("button", { name: "Run EXPLAIN" }).click();
  await expect(drawer.getByLabel("Query plan")).toContainText("Execution Time");
});

test("guests are sent to log in, and one role can't open another's screens", async ({ page }) => {
  await page.goto("/admin/revenue");
  await expect(page).toHaveURL(/\/login\?next=/);

  await demoLogin(page, "customer");
  await expect(page).toHaveURL(/\/bookings$/);
  await page.goto("/admin/revenue");
  await expect(page).toHaveURL(/\/bookings$/);
});

test.describe("at phone width", () => {
  test.use({ viewport: { width: 375, height: 740 } });

  for (const path of ["/", "/search", "/providers/1", "/login", "/register"]) {
    test(`${path} does not scroll sideways`, async ({ page }) => {
      await page.goto(path);
      await page.waitForLoadState("networkidle");
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      expect(overflow).toBeLessThanOrEqual(0);
    });
  }

  test("a customer can finish a whole booking on a phone", async ({ page }) => {
    await demoLogin(page, "customer");
    await expect(page).toHaveURL(/\/bookings$/);
    await page.goto("/search?service=Kitchen");
    await page.getByRole("link", { name: "Book Kitchen Cleaning with Sunita Devi" }).click();
    await page.getByRole("group", { name: "Start time" }).getByRole("button").first().click();
    await page.getByRole("radiogroup", { name: "Address" }).locator("label").first().click();
    await page.getByRole("button", { name: "Confirm booking" }).click();
    await expect(page).toHaveURL(/\/bookings\/\d+$/);
    await expect(page.getByText("Waiting for reply").first()).toBeVisible();
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(0);
  });

  test("the menu opens and the demo login works", async ({ page }) => {
    await demoLogin(page, "customer");
    await expect(page).toHaveURL(/\/bookings$/);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(0);
    await page.getByRole("button", { name: "Open menu" }).click();
    await expect(page.getByRole("navigation", { name: "Mobile" }).getByRole("link", { name: "Addresses" })).toBeVisible();
  });
});
