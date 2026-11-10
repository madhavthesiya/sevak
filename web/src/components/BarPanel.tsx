import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatMoney, formatRupees } from "@/lib/format";

export type BarDatum = { label: string; value: number; note?: string };

const INK = "#4d5f56";
const GRID = "#dfe7e1";
const BAR = "#0a6b46";

function Readout({ active, payload }: { active?: boolean; payload?: { payload: BarDatum }[] }) {
  if (!active || !payload?.length) return null;
  const d = payload[0].payload;
  return (
    <div className="bg-ink px-3 py-2 text-sm text-white">
      <p className="font-semibold">{d.label}</p>
      <p className="figures">{formatMoney(d.value)}</p>
      {d.note && <p className="text-bottle-soft">{d.note}</p>}
    </div>
  );
}

/** One measure per chart, so there is one colour and no legend. The table below it gives exact values. */
export function BarPanel({
  title,
  data,
  layout,
  note,
}: {
  title: string;
  data: BarDatum[];
  layout: "columns" | "rows";
  note?: string;
}) {
  const rows = layout === "rows";
  const height = rows ? Math.max(180, data.length * 44 + 40) : 280;
  const axis = { fill: INK, fontSize: 13 };

  return (
    <figure className="m-0 grid min-w-0 gap-3 bg-card p-4 md:p-5">
      <figcaption>
        <h2 className="text-2xl">{title}</h2>
        {note && <p className="text-sm text-ink-soft">{note}</p>}
      </figcaption>
      <div style={{ height }} aria-hidden>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} layout={rows ? "vertical" : "horizontal"} margin={{ left: 0, right: 12, top: 4, bottom: 0 }} barCategoryGap="30%" accessibilityLayer={false}>
            <CartesianGrid stroke={GRID} vertical={rows} horizontal={!rows} />
            {rows ? (
              <>
                <XAxis type="number" tick={axis} tickFormatter={formatRupees} axisLine={false} tickLine={false} tickCount={4} />
                <YAxis type="category" dataKey="label" tick={axis} axisLine={false} tickLine={false} width={96} />
              </>
            ) : (
              <>
                <XAxis dataKey="label" tick={axis} axisLine={false} tickLine={false} />
                <YAxis tick={axis} tickFormatter={formatRupees} axisLine={false} tickLine={false} width={72} tickCount={5} />
              </>
            )}
            <Tooltip content={<Readout />} cursor={{ fill: "rgba(14,51,39,0.07)" }} isAnimationActive={false} />
            <Bar dataKey="value" fill={BAR} radius={rows ? [0, 4, 4, 0] : [4, 4, 0, 0]} maxBarSize={36} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <details>
        <summary className="cursor-pointer text-sm font-medium">Show as a table</summary>
        <table className="mt-2 w-full text-sm">
          <tbody>
            {data.map((d) => (
              <tr key={d.label} className="border-t">
                <th scope="row" className="py-1.5 text-left font-medium">
                  {d.label}
                  {d.note && <span className="block font-normal text-ink-soft">{d.note}</span>}
                </th>
                <td className="figures py-1.5 text-right">{formatMoney(d.value)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  );
}
