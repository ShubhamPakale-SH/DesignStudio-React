import { useState } from "react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import DesignList from "@/components/Reusable Components/DesignList";
import FormVersionTable, { FormVersionRow } from "@/components/Reusable Components/FormVersionTable";

interface DocumentsTabProps {
  designTypes: string[];
}

const DocumentsTab = ({ designTypes }: DocumentsTabProps) => {
  const [selected, setSelected] = useState<string | null>(null);
  const [isDesignListVisible, setDesignListVisible] = useState(false);
  const designs = ["Anchor", "MasterList", "Collateral", "View"];

  return (
    <div className="flex flex-col gap-4 w-full">
      {/* Dropdown for Design Type */}
      <div className="flex items-center gap-3">
        <label className="text-sm font-medium text-neutral-700 whitespace-nowrap">
          Document Design Type:
        </label>
        <div className="w-56">
          <Select
            onValueChange={(value) => {
              if (value === "select") {
                setDesignListVisible(true); // Show DesignList when "--Select--" is chosen
              } else {
                setDesignListVisible(false); // Hide DesignList for other options (if any)
              }
              setSelected(value);
            }}
          >
            <SelectTrigger>
              <SelectValue placeholder="Select type" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="select">--Select--</SelectItem>
              {designTypes.map((type) => (
                <SelectItem key={type} value={type}>
                  {type}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {/* DesignList visible only when isDesignListVisible is true */}
      {isDesignListVisible && (
        <div className="w-full flex flex-col gap-4 md:flex-row">
          <div className="md:w-1/3 w-full">
            <DesignList
              designs={designs}
              selected={selected}
              onSelect={(name) => setSelected(name)}
            />
          </div>

          {selected && selected !== "select" && (
            <div className="md:w-2/3 w-full">
              <FormVersionTable
                rows={([
                  { environment: "Development", effectiveDate: "2025-01-12", version: "1.0.0", status: "Active" },
                  { environment: "QA", effectiveDate: "2025-02-03", version: "1.1.0", status: "Draft" },
                  { environment: "Production", effectiveDate: "2025-03-15", version: "2.0.0", status: "Active" },
                ]) as FormVersionRow[]}
              />
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default DocumentsTab;
