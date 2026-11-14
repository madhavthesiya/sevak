// Builds the conceptual (Chen / EER) diagram of SEVAK from the model in CONCEPTUAL_MODEL.md.
//
//   node docs/schema/conceptual-er.mjs          writes conceptual-er.svg and conceptual-er.drawio
//   node docs/schema/conceptual-er.mjs --png    also renders conceptual-er.png (needs web/node_modules)
//
// The model (entities, attributes, relationships, cardinality, participation) was checked against the
// schema that the migrations build. Only the layout is hand-placed: every connector is right-angled,
// and the long links run in lanes around the outside instead of through the diagram.
import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const VERSION = "v3.0";
const DATE = "15 Nov 2026";
const W = 5000;
const H = 2760;
const FONT = "Helvetica, Arial, sans-serif";
const INK = "#1E293B";
const MUTED = "#475569";
const CARD = "#B91C1C";

const DOMAINS = {
  identity: { title: "Identity", colour: "#2563EB", fill: "#EFF6FF" },
  geography: { title: "Geography", colour: "#059669", fill: "#ECFDF5" },
  catalog: { title: "Catalog", colour: "#B45309", fill: "#FFFBEB" },
  booking: { title: "Booking and money", colour: "#C2410C", fill: "#FFF7ED" },
  feedback: { title: "Feedback and provider operations", colour: "#7C3AED", fill: "#F5F3FF" },
};

const textWidth = (s, size) => s.length * size * 0.56;

// ── Entities ─────────────────────────────────────────────────────────────────
// Attribute markers: *key  ~partial key  /derived  +multivalued.
// attrs: { side, per } for an automatic row/column, or { rows: [[x0, y, n], ...] } for explicit rows,
// where each row starts at x0 and takes the next n attributes. anchor: where attribute lines meet the box.
const ENTITIES = [
  { id: "customer", label: "Customer", domain: "identity", x: 450, y: 360, a: "*customer_id", attrs: { side: "left", per: 1 } },
  { id: "slot", label: "AvailabilitySlot", domain: "feedback", x: 1150, y: 360, a: "*slot_id day_of_week start_time end_time", attrs: { side: "up", per: 4 } },
  { id: "provider", label: "ServiceProvider", domain: "identity", x: 1850, y: 360, a: "*provider_id bio experience_years verification_status is_active /avg_rating /review_count", attrs: { rows: [[1960, 292, 3], [1960, 248, 2], [1960, 204, 2]] }, anchor: [1925, 336] },
  { id: "document", label: "ProviderDocument", domain: "feedback", x: 2850, y: 360, a: "*document_id document_type file_url description status uploaded_at", attrs: { side: "up", per: 3 } },
  { id: "admin", label: "Admin", domain: "identity", x: 3650, y: 360, a: "*admin_id admin_role department +permission", attrs: { side: "up", per: 4 } },
  { id: "user", label: "User", domain: "identity", x: 2850, y: 800, a: "*user_id email password_hash status full_name phone created_at", attrs: { cols: [[2600, 728, 4, "left"], [3110, 748, 3, "right"]] } },
  { id: "log", label: "BookingStatusLog", domain: "booking", x: 2250, y: 1180, a: "*log_id old_status new_status changed_at remarks", attrs: { side: "right", per: 5 } },
  { id: "cancellation", label: "Cancellation", domain: "booking", x: 2850, y: 1180, a: "*cancel_id reason cancelled_at", attrs: { side: "right", per: 3 } },
  { id: "complaint", label: "Complaint", domain: "feedback", x: 3450, y: 1180, a: "*complaint_id subject description status created_at", attrs: { side: "left", per: 5 } },
  { id: "booking", label: "Booking", domain: "booking", x: 2850, y: 1600, w: 440, h: 90, a: "*booking_id status scheduled_start scheduled_end special_instructions discount /subtotal /total_amount created_at updated_at", attrs: { rows: [[2040, 1470, 5], [2040, 1515, 5]] }, anchor: [2650, 1555] },
  { id: "item", label: "BookingItem", domain: "booking", x: 3800, y: 1350, weak: true, a: "~item_no quantity unit_price", attrs: { side: "left", per: 3 } },
  { id: "payment", label: "Payment", domain: "booking", x: 2900, y: 2100, a: "*payment_id payment_method status amount gateway_ref paid_at created_at failure_reason", attrs: { rows: [[2200, 2190, 4], [2200, 2235, 4]] }, anchor: [2850, 2124] },
  { id: "refund", label: "Refund", domain: "booking", x: 2900, y: 2590, a: "*refund_id amount status requested_at processed_at", attrs: { side: "left", per: 5 } },
  { id: "coupon", label: "Coupon", domain: "booking", x: 1300, y: 2000, a: "*coupon_id code discount_type discount_value min_order max_discount usage_limit valid_from valid_to /used_count", attrs: { side: "down", per: 5 } },
  { id: "preview", label: "ProviderReview", domain: "feedback", x: 3850, y: 2050, a: "*review_id rating comment created_at updated_at", attrs: { side: "right", per: 5 } },
  { id: "sreview", label: "ServiceReview", domain: "feedback", x: 4650, y: 960, a: "*review_id rating comment created_at updated_at", attrs: { side: "right", per: 5 } },
  { id: "category", label: "Category", domain: "catalog", x: 4300, y: 360, a: "*category_id category_name description", attrs: { side: "up", per: 3 } },
  { id: "service", label: "Service", domain: "catalog", x: 4300, y: 700, w: 240, a: "*service_id service_name base_price description", attrs: { rows: [[4440, 612, 4]] }, anchor: [4410, 676] },
  { id: "variant", label: "ServiceVariant", domain: "catalog", x: 4230, y: 1100, a: "*variant_id variant_name price duration", attrs: { side: "down", per: 2 } },
  { id: "location", label: "Location", domain: "geography", x: 400, y: 1160, a: "*location_id street landmark latitude longitude", attrs: { side: "left", per: 5 } },
  { id: "area", label: "Area", domain: "geography", x: 400, y: 1700, a: "*area_id area_name pincode", attrs: { side: "right", per: 3 } },
  { id: "city", label: "City", domain: "geography", x: 400, y: 2200, a: "*city_id city_name state", attrs: { side: "right", per: 3 } },
];

// ── Relationships ────────────────────────────────────────────────────────────
// legs: [entity id, cardinality, total participation?, waypoints from the diamond, attach point on the entity]
const RELATIONSHIPS = [
  { name: "Saves", x: 400, y: 760, legs: [["customer", "M", false, [], [400, 384]], ["location", "N"]], attrs: ["label", "house_no", "is_active"], attrSide: "right" },
  { name: "Works", x: 1500, y: 360, legs: [["provider", "1"], ["slot", "N", true]] },
  { name: "Uploads", x: 2350, y: 360, legs: [["provider", "1"], ["document", "N", true]] },
  { name: "Verifies", x: 3250, y: 360, legs: [["admin", "1"], ["document", "N"]], attrs: ["verified_at"], attrSide: "down" },
  { name: "Offers", x: 4000, y: 700, legs: [["provider", "M", false, [[4000, 110], [1880, 110]], [1880, 336]], ["service", "N"]], attrs: ["price", "is_active"], attrSide: "down" },
  { name: "Belongs to", x: 4300, y: 530, legs: [["service", "N", true], ["category", "1"]] },
  { name: "Has", x: 4230, y: 900, legs: [["service", "1", false, [], [4230, 724]], ["variant", "N", true]] },
  { name: "For service", x: 4050, y: 1350, legs: [["item", "N", true], ["service", "1", false, [[4380, 1350]], [4380, 724]]] },
  { name: "Chooses", x: 4000, y: 1100, legs: [["item", "N", false, [[3800, 1100]], [3800, 1326]], ["variant", "1"]] },
  { name: "About", x: 4650, y: 700, legs: [["service", "1"], ["sreview", "N", true]] },
  { name: "Rated in", x: 4650, y: 1500, legs: [["booking", "1", false, [[4650, 1880], [3200, 1880], [3200, 1625]], [3070, 1625]], ["sreview", "N", true]] },
  { name: "Contains", x: 3350, y: 1575, identifying: true, legs: [["booking", "1", true, [], [3070, 1575]], ["item", "N", true, [[3800, 1575]], [3800, 1374]]] },
  { name: "Reviewed in", x: 3450, y: 2050, legs: [["booking", "1", false, [[3040, 2050]], [3040, 1645]], ["preview", "1", true]] },
  { name: "Paid by", x: 2900, y: 1850, legs: [["booking", "1", false, [], [2900, 1645]], ["payment", "N", true]] },
  { name: "Refunds", x: 2900, y: 2350, legs: [["payment", "1"], ["refund", "N", true]] },
  { name: "Cancelled by", x: 2850, y: 1360, legs: [["booking", "1", false, [], [2850, 1555]], ["cancellation", "1", true]] },
  { name: "Logs", x: 2250, y: 1360, legs: [["log", "N", true], ["booking", "1", false, [[2750, 1360]], [2750, 1555]]] },
  { name: "Concerns", x: 3450, y: 1360, legs: [["complaint", "N", true], ["booking", "1", false, [[2950, 1360]], [2950, 1555]]] },
  { name: "Changed by", x: 2250, y: 1000, legs: [["log", "N"], ["user", "1", false, [[2250, 890], [2790, 890]], [2790, 824]]] },
  { name: "Cancels", x: 2850, y: 1000, legs: [["user", "1"], ["cancellation", "N"]] },
  { name: "Raised by", x: 3390, y: 1000, legs: [["complaint", "N", true, [], [3390, 1156]], ["user", "1", false, [[3390, 890], [2910, 890]], [2910, 824]]] },
  { name: "Resolves", x: 3880, y: 960, legs: [["admin", "1", false, [[3880, 360]], [3725, 360]], ["complaint", "N", false, [[3880, 1060], [3515, 1060]], [3515, 1156]]], attrs: ["resolved_at", "resolution_notes"], attrSide: "right" },
  { name: "Happens at", x: 1300, y: 1625, legs: [["booking", "N", true, [], [2630, 1625]], ["@saves", "1", false, [[720, 1625], [720, 1000]], "box-right:1000"]] },
  { name: "Fulfils", x: 1790, y: 1100, legs: [["provider", "1", false, [], [1790, 384]], ["booking", "N", true, [[1790, 1580]], [2630, 1580]]] },
  { name: "Serves", x: 230, y: 1700, legs: [["provider", "M", false, [[100, 1700], [100, 190], [1790, 190]], [1790, 336]], ["area", "N"]] },
  { name: "Based in", x: 230, y: 2200, legs: [["provider", "N", true, [[60, 2200], [60, 150], [1820, 150]], [1820, 336]], ["city", "1"]] },
  { name: "In area", x: 400, y: 1450, legs: [["location", "N", true], ["area", "1"]] },
  { name: "In city", x: 400, y: 1950, legs: [["area", "N", true], ["city", "1"]] },
  { name: "Uses", x: 2000, y: 2000, legs: [["booking", "N", false, [[2760, 2000]], [2760, 1645]], ["coupon", "1"]] },
  { name: "Redeems", x: 1300, y: 1800, legs: [["customer", "1", false, [[950, 1800], [950, 360]], [525, 360]], ["coupon", "1"], ["booking", "1", false, [[2700, 1800]], [2700, 1645]]], attrs: ["redeemed_at"], attrAt: [[1480, 1738]] },
];

// ISA drawn upward: the subclasses sit above, the triangle's apex points down to User.
const ISA = { x: 2850, bar: 480, top: 520, apex: 572, parent: "user", children: ["customer", "provider", "admin"], childX: { customer: 500 } };

// ── Geometry ─────────────────────────────────────────────────────────────────
const byId = Object.fromEntries(ENTITIES.map((e) => [e.id, e]));
for (const e of ENTITIES) {
  e.w = e.w ?? Math.max(150, textWidth(e.label, 16) + 40);
  e.h = e.h ?? 48;
}

function attrShape(raw) {
  const kind = { "*": "key", "~": "partial", "/": "derived", "+": "multi" }[raw[0]] ?? "plain";
  const name = kind === "plain" ? raw : raw.slice(1);
  const label = kind === "multi" ? `{${name}}` : name;
  return { kind, label, w: textWidth(label, 12) + 30, h: 30 };
}

function placeAttributes(owner, raws, spec) {
  const shapes = raws.map(attrShape);
  if (spec.at) {
    shapes.forEach((a, i) => Object.assign(a, { x: spec.at[i][0], y: spec.at[i][1] }));
    return shapes;
  }
  let i = 0;
  if (spec.rows) {
    for (const [x0, y, count] of spec.rows) {
      let x = x0;
      for (const a of shapes.slice(i, i + count)) {
        Object.assign(a, { x: x + a.w / 2, y });
        x += a.w + 12;
      }
      i += count;
    }
    return shapes;
  }
  if (spec.cols) {
    for (const [x, y0, count] of spec.cols) {
      shapes.slice(i, i + count).forEach((a, k) => Object.assign(a, { x, y: y0 + k * 40 }));
      i += count;
    }
    return shapes;
  }
  const { side, per } = spec;
  const groups = [];
  for (let k = 0; k < shapes.length; k += per) groups.push(shapes.slice(k, k + per));
  const colW = Math.max(...shapes.map((a) => a.w));
  groups.forEach((group, k) => {
    if (side === "up" || side === "down") {
      const total = group.reduce((s, a) => s + a.w, 0) + 12 * (group.length - 1);
      let x = owner.x - total / 2;
      const dy = owner.h / 2 + 44 + k * 44;
      for (const a of group) {
        Object.assign(a, { x: x + a.w / 2, y: side === "up" ? owner.y - dy : owner.y + dy });
        x += a.w + 12;
      }
    } else {
      const top = owner.y - ((group.length - 1) * 40) / 2;
      const dx = owner.w / 2 + 34 + colW / 2 + k * (colW + 16);
      group.forEach((a, j) => Object.assign(a, { x: side === "left" ? owner.x - dx : owner.x + dx, y: top + j * 40 }));
    }
  });
  return shapes;
}

for (const e of ENTITIES) e.attrShapes = placeAttributes(e, e.a.split(" "), e.attrs);
for (const r of RELATIONSHIPS) {
  r.w = Math.max(118, textWidth(r.name, 13) + 56);
  r.h = 62;
  r.attrShapes = r.attrs ? placeAttributes(r, r.attrs, r.attrAt ? { at: r.attrAt } : { side: r.attrSide, per: r.attrs.length }) : [];
}

const AGGREGATION = { label: "Saves (aggregation)" };
{
  const saves = RELATIONSHIPS.find((r) => r.name === "Saves");
  const boxes = [byId.customer, byId.location, saves]
    .flatMap((s) => [s, ...s.attrShapes])
    .map((s) => [s.x - s.w / 2, s.y - s.h / 2, s.x + s.w / 2, s.y + s.h / 2]);
  const x1 = Math.min(...boxes.map((b) => b[0])) - 24;
  const y1 = Math.min(...boxes.map((b) => b[1])) - 40;
  const x2 = Math.max(...boxes.map((b) => b[2])) + 24;
  const y2 = Math.max(...boxes.map((b) => b[3])) + 24;
  AGGREGATION.box = { x: (x1 + x2) / 2, y: (y1 + y2) / 2, w: x2 - x1, h: y2 - y1, x1, y1, x2, y2 };
}

function exitPoint(s, tx, ty, kind = "rect") {
  const dx = tx - s.x;
  const dy = ty - s.y;
  if (!dx && !dy) return [s.x, s.y];
  let t;
  if (kind === "diamond") t = 1 / (Math.abs(dx) / (s.w / 2) + Math.abs(dy) / (s.h / 2));
  else if (kind === "ellipse") t = 1 / Math.sqrt((dx / (s.w / 2)) ** 2 + (dy / (s.h / 2)) ** 2);
  else t = Math.min(Math.abs(s.w / 2 / (dx || 1e-9)), Math.abs(s.h / 2 / (dy || 1e-9)));
  return [s.x + dx * t, s.y + dy * t];
}

/** The polyline of one leg, from the diamond's outline to the entity's outline. */
function legPath(r, [id, , , via = [], at]) {
  const target = id === "@saves" ? AGGREGATION.box : byId[id];
  let end;
  if (typeof at === "string") end = [AGGREGATION.box.x2, Number(at.split(":")[1])];
  else if (at) end = at;
  else end = exitPoint(target, via.length ? via.at(-1)[0] : r.x, via.length ? via.at(-1)[1] : r.y);
  const first = via[0] ?? end;
  const start = exitPoint(r, first[0], first[1], "diamond");
  return [start, ...via, end];
}

// ── SVG ──────────────────────────────────────────────────────────────────────
const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
const n = (v) => Math.round(v * 10) / 10;

function text(x, y, s, { size = 12, weight = 400, anchor = "middle", fill = INK, deco = "" } = {}) {
  return `<text x="${n(x)}" y="${n(y)}" font-family="${FONT}" font-size="${size}" font-weight="${weight}" text-anchor="${anchor}" dominant-baseline="central" fill="${fill}"${deco ? ` text-decoration="${deco}"` : ""}>${esc(s)}</text>`;
}

function polyline(points, double) {
  const d = points.map((p) => `${n(p[0])},${n(p[1])}`).join(" ");
  if (!double) return `<polyline points="${d}" fill="none" stroke="${INK}" stroke-width="1.5" stroke-linejoin="round"/>`;
  // A double line: a wide dark stroke with a white core.
  return `<polyline points="${d}" fill="none" stroke="${INK}" stroke-width="6" stroke-linejoin="miter"/><polyline points="${d}" fill="none" stroke="white" stroke-width="2.6" stroke-linejoin="miter"/>`;
}

function ovalSvg(a, colour) {
  const dash = a.kind === "derived" ? ` stroke-dasharray="5 3"` : "";
  let out = `<ellipse cx="${n(a.x)}" cy="${n(a.y)}" rx="${n(a.w / 2)}" ry="${a.h / 2}" fill="white" stroke="${colour}" stroke-width="1.4"${dash}/>`;
  if (a.kind === "multi") out += `<ellipse cx="${n(a.x)}" cy="${n(a.y)}" rx="${n(a.w / 2 - 4)}" ry="${a.h / 2 - 4}" fill="none" stroke="${colour}" stroke-width="1.1"/>`;
  out += text(a.x, a.y, a.label, { size: 12, weight: a.kind === "key" || a.kind === "partial" ? 700 : 400, deco: a.kind === "key" ? "underline" : "" });
  if (a.kind === "partial") {
    const half = textWidth(a.label, 12) / 2 + 2;
    out += `<line x1="${n(a.x - half)}" y1="${n(a.y + 9)}" x2="${n(a.x + half)}" y2="${n(a.y + 9)}" stroke="${INK}" stroke-width="1.2" stroke-dasharray="3 2"/>`;
  }
  return out;
}

function attrLines(owner, kind, colour) {
  return owner.attrShapes
    .map((a) => {
      const [tx, ty] = owner.anchor ?? [owner.x, owner.y];
      const [x1, y1] = exitPoint(a, tx, ty, "ellipse");
      const [x2, y2] = owner.anchor ?? exitPoint(owner, a.x, a.y, kind);
      return `<line x1="${n(x1)}" y1="${n(y1)}" x2="${n(x2)}" y2="${n(y2)}" stroke="${colour}" stroke-opacity="0.75" stroke-width="1.1"/>`;
    })
    .join("");
}

function cardinality(path, card) {
  const [sx, sy] = path.at(-1);
  const [px, py] = path.at(-2);
  const len = Math.hypot(px - sx, py - sy) || 1;
  const ux = (px - sx) / len;
  const uy = (py - sy) / len;
  const x = sx + ux * 18 - uy * 13;
  const y = sy + uy * 18 + ux * 13;
  return `<circle cx="${n(x)}" cy="${n(y)}" r="10" fill="white"/><text x="${n(x)}" y="${n(y)}" font-family="${FONT}" font-size="15" font-weight="700" text-anchor="middle" dominant-baseline="central" fill="${CARD}">${card}</text>`;
}

function buildSvg() {
  const back = [];
  const lines = [];
  const shapes = [];
  const labels = [];

  const b = AGGREGATION.box;
  back.push(`<rect x="${n(b.x1)}" y="${n(b.y1)}" width="${n(b.w)}" height="${n(b.h)}" rx="10" fill="${DOMAINS.geography.fill}" fill-opacity="0.55" stroke="${INK}" stroke-width="1.8" stroke-dasharray="10 6"/>`);
  back.push(text(b.x1 + 14, b.y1 + 18, AGGREGATION.label, { size: 13, weight: 700, anchor: "start" }));

  // ISA: subclasses above, a bar, the triangle, then a double line (total) down to User.
  const parent = byId[ISA.parent];
  const cx = (c) => ISA.childX[c] ?? byId[c].x;
  const xs = ISA.children.map(cx);
  for (const c of ISA.children) lines.push(polyline([[cx(c), byId[c].y + byId[c].h / 2], [cx(c), ISA.bar]]));
  lines.push(polyline([[Math.min(...xs), ISA.bar], [Math.max(...xs), ISA.bar]]));
  lines.push(polyline([[ISA.x, ISA.bar], [ISA.x, ISA.top]]));
  lines.push(polyline([[ISA.x, ISA.apex], [ISA.x, parent.y - parent.h / 2]], true));
  shapes.push(`<polygon points="${ISA.x - 34},${ISA.top} ${ISA.x + 34},${ISA.top} ${ISA.x},${ISA.apex}" fill="white" stroke="${INK}" stroke-width="1.6"/>`);
  labels.push(text(ISA.x, ISA.top + 15, "d", { size: 15, weight: 700 }));
  labels.push(text(ISA.x + 46, ISA.top + 18, "ISA · disjoint, total", { size: 12, anchor: "start", fill: MUTED }));

  for (const r of RELATIONSHIPS) {
    for (const leg of r.legs) {
      const path = legPath(r, leg);
      lines.push(polyline(path, leg[2]));
      labels.push(cardinality(path, leg[1]));
    }
    lines.push(attrLines(r, "diamond", MUTED));
    shapes.push(`<polygon points="${r.x},${r.y - r.h / 2} ${r.x + r.w / 2},${r.y} ${r.x},${r.y + r.h / 2} ${r.x - r.w / 2},${r.y}" fill="#F8FAFC" stroke="${INK}" stroke-width="1.6"/>`);
    if (r.identifying) shapes.push(`<polygon points="${r.x},${r.y - r.h / 2 + 7} ${r.x + r.w / 2 - 13},${r.y} ${r.x},${r.y + r.h / 2 - 7} ${r.x - r.w / 2 + 13},${r.y}" fill="none" stroke="${INK}" stroke-width="1.3"/>`);
    labels.push(text(r.x, r.y, r.name, { size: 13, weight: 600 }));
    for (const a of r.attrShapes) shapes.push(ovalSvg(a, MUTED));
  }

  for (const e of ENTITIES) {
    const d = DOMAINS[e.domain];
    lines.push(attrLines(e, "rect", d.colour));
    shapes.push(`<rect x="${n(e.x - e.w / 2)}" y="${n(e.y - e.h / 2)}" width="${n(e.w)}" height="${e.h}" rx="3" fill="${d.fill}" stroke="${d.colour}" stroke-width="2.2"/>`);
    if (e.weak) shapes.push(`<rect x="${n(e.x - e.w / 2 + 5)}" y="${n(e.y - e.h / 2 + 5)}" width="${n(e.w - 10)}" height="${e.h - 10}" rx="2" fill="none" stroke="${d.colour}" stroke-width="1.6"/>`);
    labels.push(text(e.x, e.y, e.label, { size: 16, weight: 700 }));
    for (const a of e.attrShapes) shapes.push(ovalSvg(a, d.colour));
  }

  const head = [
    text(150, 48, "SEVAK · Conceptual ER model (Chen / EER)", { size: 32, weight: 700, anchor: "start" }),
    text(150, 84, `22 entities and 30 relationships, checked against the 29-table schema built by the Flyway migrations   ·   ${VERSION}   ·   ${DATE}`, { size: 15, anchor: "start", fill: MUTED }),
  ];

  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}" height="${H}" role="img" aria-label="SEVAK conceptual ER diagram: 22 entities and 30 relationships around Booking, in Chen and EER notation">
<rect width="${W}" height="${H}" fill="white"/>
${head.join("\n")}
${back.join("\n")}
${lines.join("\n")}
${shapes.join("\n")}
${labels.join("\n")}
${legendSvg()}
</svg>
`;
}

function legendSvg() {
  const x = 4180;
  const y = 2150;
  const sym = (k, cx, cy) =>
    ({
      entity: `<rect x="${cx - 42}" y="${cy - 13}" width="84" height="26" rx="2" fill="white" stroke="${INK}" stroke-width="1.8"/>`,
      weak: `<rect x="${cx - 42}" y="${cy - 13}" width="84" height="26" fill="white" stroke="${INK}" stroke-width="1.8"/><rect x="${cx - 38}" y="${cy - 9}" width="76" height="18" fill="none" stroke="${INK}" stroke-width="1.2"/>`,
      rel: `<polygon points="${cx},${cy - 15} ${cx + 42},${cy} ${cx},${cy + 15} ${cx - 42},${cy}" fill="#F8FAFC" stroke="${INK}" stroke-width="1.5"/>`,
      idrel: `<polygon points="${cx},${cy - 15} ${cx + 42},${cy} ${cx},${cy + 15} ${cx - 42},${cy}" fill="#F8FAFC" stroke="${INK}" stroke-width="1.5"/><polygon points="${cx},${cy - 9} ${cx + 30},${cy} ${cx},${cy + 9} ${cx - 30},${cy}" fill="none" stroke="${INK}" stroke-width="1.1"/>`,
      key: ovalSvg({ kind: "key", label: "booking_id", x: cx, y: cy, w: 92, h: 26 }, INK),
      partial: ovalSvg({ kind: "partial", label: "item_no", x: cx, y: cy, w: 84, h: 26 }, INK),
      derived: ovalSvg({ kind: "derived", label: "avg_rating", x: cx, y: cy, w: 92, h: 26 }, INK),
      multi: ovalSvg({ kind: "multi", label: "{permission}", x: cx, y: cy, w: 96, h: 26 }, INK),
      total: polyline([[cx - 42, cy], [cx + 42, cy]], true),
      partialp: polyline([[cx - 42, cy], [cx + 42, cy]]),
      isa: `<polygon points="${cx - 17},${cy - 12} ${cx + 17},${cy - 12} ${cx},${cy + 13}" fill="white" stroke="${INK}" stroke-width="1.4"/>${text(cx, cy - 4, "d", { size: 11, weight: 700 })}`,
      agg: `<rect x="${cx - 42}" y="${cy - 13}" width="84" height="26" rx="4" fill="none" stroke="${INK}" stroke-width="1.6" stroke-dasharray="8 4"/>`,
      card: text(cx, cy, "1   N   M", { size: 15, weight: 700, fill: CARD }),
    })[k];
  const left = [
    ["entity", "Entity"],
    ["weak", "Weak entity"],
    ["rel", "Relationship"],
    ["idrel", "Identifying relationship"],
    ["isa", "ISA: disjoint (d), total"],
    ["agg", "Aggregation"],
    ["card", "Cardinality"],
  ];
  const right = [
    ["key", "Key (underlined)"],
    ["partial", "Partial key (dashed)"],
    ["derived", "Derived attribute"],
    ["multi", "Multivalued attribute"],
    ["total", "Total participation"],
    ["partialp", "Partial participation"],
  ];
  const out = [
    `<rect x="${x}" y="${y}" width="780" height="560" rx="14" fill="white" stroke="#CBD5E1" stroke-width="1.5"/>`,
    text(x + 24, y + 30, "LEGEND", { size: 15, weight: 700, anchor: "start" }),
  ];
  left.forEach(([k, label], i) => out.push(sym(k, x + 74, y + 72 + i * 40), text(x + 130, y + 72 + i * 40, label, { size: 13, anchor: "start" })));
  right.forEach(([k, label], i) => out.push(sym(k, x + 454, y + 72 + i * 40), text(x + 510, y + 72 + i * 40, label, { size: 13, anchor: "start" })));
  const dy = y + 72 + 7 * 40 + 10;
  out.push(text(x + 24, dy, "DOMAINS", { size: 13, weight: 700, anchor: "start" }));
  Object.values(DOMAINS).forEach((d, i) => {
    const cx = x + 24 + (i % 2) * 380;
    const cy = dy + 34 + Math.floor(i / 2) * 34;
    out.push(`<rect x="${cx}" y="${cy - 11}" width="34" height="22" rx="3" fill="${d.fill}" stroke="${d.colour}" stroke-width="2"/>`, text(cx + 46, cy, d.title, { size: 13, anchor: "start" }));
  });
  return out.join("\n");
}

// ── draw.io ──────────────────────────────────────────────────────────────────
function buildDrawio() {
  const cells = [];
  let next = 2;
  const id = () => `c${next++}`;
  const xmlEsc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  const base = "whiteSpace=wrap;html=1;align=center;fontFamily=Helvetica;";
  const vertex = (value, style, x, y, w, h) => {
    const c = id();
    cells.push(`<mxCell id="${c}" value="${xmlEsc(value)}" style="${style}" vertex="1" parent="1"><mxGeometry x="${n(x)}" y="${n(y)}" width="${n(w)}" height="${n(h)}" as="geometry"/></mxCell>`);
    return c;
  };
  const edge = (src, dst, { total = false, label = "", points = [], ortho = true } = {}) => {
    const c = id();
    const style = `endArrow=none;html=1;rounded=0;strokeColor=${INK};${ortho ? "edgeStyle=orthogonalEdgeStyle;" : ""}${total ? "shape=link;width=4;" : ""}`;
    const pts = points.length ? `<Array as="points">${points.map(([x, y]) => `<mxPoint x="${n(x)}" y="${n(y)}"/>`).join("")}</Array>` : "";
    cells.push(`<mxCell id="${c}" value="" style="${style}" edge="1" parent="1" source="${src}" target="${dst}"><mxGeometry relative="1" as="geometry">${pts}</mxGeometry></mxCell>`);
    if (label) cells.push(`<mxCell id="${id()}" value="${label}" style="edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontColor=${CARD};fontStyle=1;fontSize=15;" vertex="1" connectable="0" parent="${c}"><mxGeometry x="0.82" relative="1" as="geometry"><mxPoint as="offset"/></mxGeometry></mxCell>`);
  };
  const attrStyle = (a, colour) =>
    `ellipse;${base}fontSize=12;strokeColor=${colour};${{ key: "fontStyle=5;", derived: "dashed=1;", multi: "shape=doubleEllipse;margin=3;", partial: "fontStyle=1;", plain: "" }[a.kind]}`;
  const attrLabel = (a) => (a.kind === "partial" ? `<span style="border-bottom:1px dashed;">${a.label}</span>` : a.label);

  cells.push(`<mxCell id="t1" value="SEVAK · Conceptual ER model (Chen / EER) · ${VERSION} · ${DATE}" style="text;html=1;fontSize=30;fontStyle=1;align=left;verticalAlign=middle;" vertex="1" parent="1"><mxGeometry x="150" y="24" width="1900" height="48" as="geometry"/></mxCell>`);
  const b = AGGREGATION.box;
  const ids = { "@saves": vertex(AGGREGATION.label, `rounded=1;arcSize=2;${base}fillColor=${DOMAINS.geography.fill};dashed=1;dashPattern=10 6;strokeWidth=2;verticalAlign=top;align=left;spacingLeft=12;fontStyle=1;`, b.x1, b.y1, b.w, b.h) };
  for (const e of ENTITIES) {
    const d = DOMAINS[e.domain];
    ids[e.id] = vertex(e.label, `${e.weak ? "shape=ext;margin=3;double=1;" : ""}${base}fontSize=16;fontStyle=1;fillColor=${d.fill};strokeColor=${d.colour};strokeWidth=2;`, e.x - e.w / 2, e.y - e.h / 2, e.w, e.h);
    for (const a of e.attrShapes) edge(vertex(attrLabel(a), attrStyle(a, d.colour), a.x - a.w / 2, a.y - a.h / 2, a.w, a.h), ids[e.id], { ortho: false });
  }
  const isa = vertex("d", `triangle;direction=south;${base}fontStyle=1;fontSize=15;`, ISA.x - 34, ISA.top, 68, ISA.apex - ISA.top);
  edge(isa, ids[ISA.parent], { total: true });
  for (const c of ISA.children) edge(ids[c], isa, { points: [[ISA.childX[c] ?? byId[c].x, ISA.bar], [ISA.x, ISA.bar]] });
  for (const r of RELATIONSHIPS) {
    const rid = vertex(r.name, `shape=rhombus;perimeter=rhombusPerimeter;${r.identifying ? "double=1;" : ""}${base}fontSize=13;fontStyle=1;fillColor=#F8FAFC;`, r.x - r.w / 2, r.y - r.h / 2, r.w, r.h);
    for (const [eid, card, total, via = []] of r.legs) edge(rid, ids[eid], { total, label: card, points: via });
    for (const a of r.attrShapes) edge(vertex(attrLabel(a), attrStyle(a, MUTED), a.x - a.w / 2, a.y - a.h / 2, a.w, a.h), rid, { ortho: false });
  }
  return `<mxfile host="sevak"><diagram id="sevak-er" name="Conceptual ER"><mxGraphModel dx="${W}" dy="${H}" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="${W}" pageHeight="${H}" math="0" shadow="0"><root><mxCell id="0"/><mxCell id="1" parent="0"/>${cells.join("")}</root></mxGraphModel></diagram></mxfile>\n`;
}

// ── Output ───────────────────────────────────────────────────────────────────
const svg = buildSvg();
writeFileSync(join(HERE, "conceptual-er.svg"), svg);
writeFileSync(join(HERE, "conceptual-er.drawio"), buildDrawio());
console.log("wrote conceptual-er.svg and conceptual-er.drawio");

if (process.argv.includes("--png")) {
  const { chromium } = await import(pathToFileURL(join(HERE, "../../web/node_modules/playwright/index.mjs")).href);
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
  await page.setContent(`<html><body style="margin:0">${svg}</body></html>`);
  await page.screenshot({ path: join(HERE, "conceptual-er.png"), clip: { x: 0, y: 0, width: W, height: H } });
  await browser.close();
  console.log("wrote conceptual-er.png");
}
