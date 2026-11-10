// Saves the running API's OpenAPI document in a stable form, so the generated types can be compared in CI.
import { writeFileSync } from "node:fs";

const base = process.env.VITE_API_URL ?? "http://localhost:8080";
const spec = await (await fetch(`${base}/api-docs`)).json();

const sorted = (object) => Object.fromEntries(Object.entries(object).sort(([a], [b]) => a.localeCompare(b)));
spec.info.version = "local";
spec.servers = [{ url: "http://localhost:8080" }];
spec.paths = sorted(spec.paths);
spec.components.schemas = sorted(spec.components.schemas);

writeFileSync(new URL("../openapi.json", import.meta.url), `${JSON.stringify(spec, null, 2)}\n`);
console.log(`Saved ${Object.keys(spec.paths).length} paths`);
