import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const DocumentsTab = () => {
  return (
    <div className="flex items-center gap-3">
      <label className="text-sm font-medium text-neutral-700 whitespace-nowrap">
        Document Design Type:
      </label>
      <div className="w-56">
        <Select defaultValue="anchor">
          <SelectTrigger>
            <SelectValue placeholder="Select type" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="anchor">Anchor</SelectItem>
            <SelectItem value="masterlist">MasterList</SelectItem>
            <SelectItem value="collateral">Collateral</SelectItem>
            <SelectItem value="view">View</SelectItem>
          </SelectContent>
        </Select>
      </div>
    </div>
  );
};

export default DocumentsTab;
