// Production smoke test: checks a deployed SEVAK API (and optionally the web app) end to end.
//
//   node scripts/smoke-test.mjs https://<api-host> [https://<web-host>]
//
// It creates one real booking with the demo accounts and takes it from request to review, so run it
// on the demo deployment only. The nightly demo reset removes it. Needs Node 18 or later (built-in fetch).

const [api, web] = process.argv.slice(2).map((url) => url?.replace(/\/+$/, ""));
if (!api) {
  console.error("Usage: node scripts/smoke-test.mjs <api-url> [web-url]");
  process.exit(2);
}

const DEMO_PASSWORD = "Demo@1234"; // public: the README lists the demo accounts
const results = [];

async function check(name, fn) {
  const started = Date.now();
  try {
    const detail = await fn();
    results.push({ name, ok: true });
    console.log(`PASS  ${name}${detail ? `: ${detail}` : ""} (${Date.now() - started} ms)`);
  } catch (error) {
    results.push({ name, ok: false });
    console.log(`FAIL  ${name}: ${error.message}`);
  }
}

async function call(method, path, { token, body, origin } = {}) {
  const headers = { Accept: "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (origin) headers.Origin = origin;
  const response = await fetch(api + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    // not JSON
  }
  return { status: response.status, json, headers: response.headers };
}

function expectStatus(result, expected) {
  if (result.status !== expected) {
    const detail = result.json?.detail ?? result.json?.title ?? "";
    throw new Error(`expected HTTP ${expected}, got ${result.status} ${detail}`.trim());
  }
  return result.json;
}

async function login(email) {
  const json = expectStatus(await call("POST", "/api/v1/auth/login", { body: { email, password: DEMO_PASSWORD } }), 200);
  return json.accessToken;
}

/** A free instance sleeps when idle; the first request wakes it, which can take a couple of minutes. */
async function waitUntilReady() {
  const deadline = Date.now() + 4 * 60 * 1000;
  let last = "";
  while (Date.now() < deadline) {
    try {
      const result = await call("GET", "/actuator/health/readiness");
      if (result.status === 200 && result.json?.status === "UP") return "UP";
      last = `HTTP ${result.status}`;
    } catch (error) {
      last = error.message;
    }
    await new Promise((resolve) => setTimeout(resolve, 5000));
  }
  throw new Error(`not ready after 4 minutes (${last})`);
}

console.log(`Smoke test of ${api}${web ? ` and ${web}` : ""}\n`);

await check("Health: readiness is UP (database reachable)", waitUntilReady);
await check("Liveness is UP", async () => expectStatus(await call("GET", "/actuator/health/liveness"), 200).status);

let customer;
let provider;
let admin;
await check("Demo login: customer", async () => void (customer = await login("aarav.sharma@gmail.com")));
await check("Demo login: provider", async () => void (provider = await login("ravi.electrician@gmail.com")));
await check("Demo login: admin", async () => void (admin = await login("admin.raj@sevak.com")));

await check("Public: service catalogue", async () => {
  const json = expectStatus(await call("GET", "/api/v1/services"), 200);
  return `${json.total} services`;
});
await check("Public: provider search", async () => {
  const json = expectStatus(await call("GET", "/api/v1/providers?service=fan"), 200);
  return `${json.total} offerings`;
});
await check("Public: reviews", async () => {
  const json = expectStatus(await call("GET", "/api/v1/providers/1/reviews"), 200);
  return `${json.total} reviews`;
});

let bookingId;
await check("Booking: customer books the first free slot", async () => {
  const today = new Date().toISOString().slice(0, 10);
  const slots = expectStatus(await call("GET", `/api/v1/providers/1/slots?from=${today}&days=14&minutes=30`), 200);
  if (!slots.length) throw new Error("provider 1 has no free slot in the next 14 days");
  const body = {
    providerId: 1,
    locationId: 1,
    scheduledStart: slots[0].start,
    items: [{ serviceId: 1, variantId: 1, quantity: 1 }],
    specialInstructions: "Smoke test",
  };
  const json = expectStatus(await call("POST", "/api/v1/bookings", { token: customer, body }), 201);
  bookingId = json.bookingId;
  return `booking ${bookingId} at ${slots[0].start}`;
});
for (const step of ["accept", "start", "complete"]) {
  await check(`Booking: provider can ${step}`, async () => {
    if (!bookingId) throw new Error("no booking");
    return expectStatus(await call("POST", `/api/v1/bookings/${bookingId}/${step}`, { token: provider }), 200).status;
  });
}
await check("Booking: customer pays", async () => {
  if (!bookingId) throw new Error("no booking");
  const json = expectStatus(
    await call("POST", `/api/v1/bookings/${bookingId}/payments`, { token: customer, body: { paymentMethod: "UPI" } }),
    201,
  );
  return json.status;
});
await check("Booking: customer reviews", async () => {
  if (!bookingId) throw new Error("no booking");
  expectStatus(
    await call("POST", `/api/v1/bookings/${bookingId}/review`, {
      token: customer,
      body: { rating: 5, comment: "Smoke test" },
    }),
    201,
  );
});

await check("Admin: revenue chart loads", async () => {
  const json = expectStatus(await call("GET", "/api/v1/admin/revenue", { token: admin }), 200);
  return `${json.length} categories`;
});
await check("Admin: live leaderboard loads", async () => {
  const json = expectStatus(await call("GET", "/api/v1/admin/leaderboard?fast=false", { token: admin }), 200);
  return `${json.length} providers`;
});
await check("Admin: bookings list loads", async () => {
  expectStatus(await call("GET", "/api/v1/admin/bookings?limit=5", { token: admin }), 200);
});

await check("Security: no token returns 401", async () => {
  expectStatus(await call("GET", "/api/v1/me"), 401);
});
await check("Security: a customer can't open admin pages (403)", async () => {
  expectStatus(await call("GET", "/api/v1/admin/revenue", { token: customer }), 403);
});
await check("Security: an unknown URL returns 404", async () => {
  expectStatus(await call("GET", "/api/v1/no-such-thing", { token: admin }), 404);
});

if (web) {
  await check("CORS: the API allows the web app's origin", async () => {
    const response = await fetch(api + "/api/v1/services", {
      method: "OPTIONS",
      headers: { Origin: web, "Access-Control-Request-Method": "GET" },
    });
    const allowed = response.headers.get("access-control-allow-origin");
    if (allowed !== web) throw new Error(`Access-Control-Allow-Origin is ${allowed ?? "missing"}`);
    return allowed;
  });
  await check("Web: home page loads", async () => {
    const response = await fetch(web + "/");
    if (response.status !== 200) throw new Error(`HTTP ${response.status}`);
  });
  await check("Web: a deep link serves the app (no 404)", async () => {
    const response = await fetch(web + "/providers/1");
    const html = await response.text();
    if (response.status !== 200 || !html.includes('id="root"')) throw new Error(`HTTP ${response.status}`);
  });
}

const failed = results.filter((r) => !r.ok).length;
console.log(`\n${results.length - failed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
