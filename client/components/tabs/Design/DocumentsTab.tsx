import { useEffect, useState } from "react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import DesignList from "@/components/Reusable Components/DesignList";
import FormVersionTable, {
  FormVersionRow,
} from "@/components/Reusable Components/FormVersionTable";
import {
  fetchFormDesignListByDocType,
  type DesignType,
} from "@/service/Design/DesignService";

interface DocumentsTabProps {
  designTypes: DesignType[];
}

const DocumentsTab = ({ designTypes }: DocumentsTabProps) => {
  const [selected, setSelected] = useState<string | null>(null);
  const [selectedTypeId, setSelectedTypeId] = useState<string | null>(null);
  const [designs, setDesigns] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  return (
    <div className="flex flex-col gap-4 w-full">
      {/* Dropdown for Design Type */}
      <div className="flex items-center gap-3">
        <label className="text-sm font-medium text-neutral-700 whitespace-nowrap">
          Document Design Type:
        </label>
        <div className="w-56">
          <Select
            onValueChange={async (value) => {
              setSelectedTypeId(value);
              setSelected(null);
              setError(null);
              setLoading(true);
              try {
                const res: any = await fetchFormDesignListByDocType(value);
                const rows = Array.isArray(res)
                  ? res
                  : Array.isArray(res?.rows)
                    ? res.rows
                    : [];
                const names: string[] = rows
                  .map(
                    (r: any) =>
                      r.FormDesignName ??
                      r.DocumentDesignName ??
                      r.formDesignName ??
                      "",
                  )
                  .filter((s: string) => !!s && s.trim().length > 0);
                setDesigns(Array.from(new Set(names)));
              } catch (e) {
                setError(e instanceof Error ? e.message : "Request failed");
                setDesigns([]);
              } finally {
                setLoading(false);
              }
            }}
          >
            <SelectTrigger>
              <SelectValue placeholder="Select type" />
            </SelectTrigger>
            <SelectContent>
              {designTypes.map((dt) => (
                <SelectItem key={String(dt.id)} value={String(dt.id)}>
                  {dt.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="w-full flex flex-col gap-4 md:flex-row">
        <div className="md:w-1/3 w-full">
          {loading && <p className="text-sm">Loading…</p>}
          {error && <p className="text-sm text-red-600">{error}</p>}
          {!loading && !error && (
            <DesignList
              designs={designs}
              selected={selected}
              onSelect={(name) => setSelected(name)}
            />
          )}
        </div>

        {selected && selectedTypeId && (
          <div className="md:w-2/3 w-full">
            <FormVersionTable
              rows={
                [
                  {
                    environment: "Development",
                    effectiveDate: "2025-01-12",
                    version: "1.0.0",
                    status: "Active",
                  },
                  {
                    environment: "QA",
                    effectiveDate: "2025-02-03",
                    version: "1.1.0",
                    status: "Draft",
                  },
                  {
                    environment: "Production",
                    effectiveDate: "2025-03-15",
                    version: "2.0.0",
                    status: "Active",
                  },
                ] as FormVersionRow[]
              }
            />
          </div>
        )}
      </div>
    </div>
  );
};

export default DocumentsTab;
