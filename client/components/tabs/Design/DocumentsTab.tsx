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
  const [selected, setSelected] = useState<string | null>(null); // Tracks the selected item
  const [isDesignListVisible, setDesignListVisible] = useState(false); // Controls visibility of DesignList
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
              setSelected(value); // Update the selected state
            }}
          >
            <SelectTrigger>
              <SelectValue placeholder="Select type" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="select">--Select--</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {/* DesignList visible only when isDesignListVisible is true */}
      {isDesignListVisible && (
        <div className="w-[30%]">
          <DesignList
            designs={designs}
            selected={selected}
            onSelect={(name) => setSelected(name)}
          />
        </div>
      )}
    </div>
  );
};

export default DocumentsTab;