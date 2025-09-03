import { cn } from "@/lib/utils";

export type FormVersionRow = {
  environment: string;
  effectiveDate: string; // ISO or display string
  version: string;
  status: string;
};

export interface FormVersionTableProps {
  rows: FormVersionRow[];
  className?: string;
  onAction?: (row: FormVersionRow) => void;
}

const StatusBadge = ({ status }: { status: string }) => {
  const color =
    status.toLowerCase() === "active"
      ? "bg-emerald-100 text-emerald-700 border-emerald-200"
      : status.toLowerCase() === "draft"
        ? "bg-amber-100 text-amber-800 border-amber-200"
        : status.toLowerCase() === "deprecated"
          ? "bg-rose-100 text-rose-700 border-rose-200"
          : "bg-slate-100 text-slate-700 border-slate-200";
  return (
    <span
      className={cn(
        "inline-flex items-center rounded px-2 py-0.5 text-xs border",
        color,
      )}
    >
      {status}
    </span>
  );
};

const FormVersionTable = ({
  rows,
  className,
  onAction,
}: FormVersionTableProps) => {
  return (
    <div
      className={cn(
        "w-full overflow-hidden rounded-md border border-border bg-background text-foreground shadow-[0_0_0_1px_rgba(0,0,0,0.06),0_8px_24px_rgba(0,0,0,0.08),0_2px_8px_rgba(0,0,0,0.06)] dark:shadow-[0_0_0_1px_rgba(255,255,255,0.08),0_8px_24px_rgba(0,0,0,0.6),0_2px_8px_rgba(0,0,0,0.5)]",
        className,
      )}
      role="region"
      aria-label="Form versions"
    >
      <table className="w-full table-auto border-collapse">
        <thead>
          <tr className="bg-muted/60">
            <th className="text-left px-4 py-2 text-sm font-semibold">
              Environment name
            </th>
            <th className="text-left px-4 py-2 text-sm font-semibold">
              Effective Date
            </th>
            <th className="text-left px-4 py-2 text-sm font-semibold">
              Version
            </th>
            <th className="text-left px-4 py-2 text-sm font-semibold">
              Status
            </th>
            <th className="text-left px-4 py-2 text-sm font-semibold">
              Action
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={`${row.environment}-${row.version}`}
              className="border-t border-border/60"
            >
              <td className="px-4 py-2 text-sm">{row.environment}</td>
              <td className="px-4 py-2 text-sm">{row.effectiveDate}</td>
              <td className="px-4 py-2 text-sm">{row.version}</td>
              <td className="px-4 py-2 text-sm">
                <StatusBadge status={row.status} />
              </td>
              <td className="px-4 py-2 text-sm">
                <button
                  type="button"
                  onClick={() => onAction?.(row)}
                  className="inline-flex items-center rounded-md border px-2 py-1 text-xs hover:bg-muted"
                >
                  Open
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default FormVersionTable;
