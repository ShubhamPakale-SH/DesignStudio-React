import { useState } from "react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import DesignList from "@/components/Reusable Components/DesignList";

const DocumentsTab = () => {
  const [selected, setSelected] = useState<string | null>(null);
  const designs = ["Anchor", "MasterList", "Collateral", "View"];

  return (
    <div className="flex flex-col gap-4 w-full">
      <div className="flex items-center gap-3">
        <label className="text-sm font-medium text-neutral-700 whitespace-nowrap">
          Document Design Type:
        </label>
        <div className="w-56">
          <Select defaultValue="select">
            <SelectTrigger>
              <SelectValue placeholder="Select type" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="select">--Select--</SelectItem>
             
            </SelectContent>
          </Select>
        </div>
      </div>

      <DesignList
        designs={designs}
        selected={selected as string | null}
        onSelect={(name) => setSelected(name)}
      />
    </div>
  );
};

export default DocumentsTab;
