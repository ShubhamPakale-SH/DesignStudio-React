// DesignCompileTab.tsx

import { useState, useEffect } from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";

// 1. Expanded mock data for the new columns
const documentDesigns = [
  {
    id: "D001",
    designName: "Anchor Document Template",
    designType: "Anchor",
    version: "2.1",
    effectiveDate: "2023-10-01",
    status: "Active",
    compiledBy: "Alex Green",
    lastCompiledDate: "2023-09-28",
    compiledStatus: "Success",
  },
  {
    id: "D002",
    designName: "MasterList Financial Q3",
    designType: "MasterList",
    version: "1.0",
    effectiveDate: "2023-09-15",
    status: "Active",
    compiledBy: "Sarah Johnson",
    lastCompiledDate: "2023-09-14",
    compiledStatus: "Success",
  },
  {
    id: "D003",
    designName: "Collateral Marketing Brochure",
    designType: "Collateral",
    version: "3.5",
    effectiveDate: "2023-11-01",
    status: "In Review",
    compiledBy: "Michael Chen",
    lastCompiledDate: "2023-08-20",
    compiledStatus: "Failed",
  },
  {
    id: "D004",
    designName: "View Only - Annual Report",
    designType: "View",
    version: "1.2",
    effectiveDate: "2023-01-01",
    status: "Archived",
    compiledBy: "Laura Davis",
    lastCompiledDate: "2022-12-15",
    compiledStatus: "Success",
  },
];

const DesignCompileTab = () => {
  // 2. State management for checkboxes
  const [selectedDesigns, setSelectedDesigns] = useState<string[]>([]);
  const [selectAll, setSelectAll] = useState(false);

  // Effect to sync the 'Select All' checkbox
  useEffect(() => {
    setSelectAll(selectedDesigns.length === documentDesigns.length && documentDesigns.length > 0);
  }, [selectedDesigns]);

  // Handler for the master checkbox
  const handleSelectAll = (checked: boolean) => {
    setSelectAll(checked);
    if (checked) {
      setSelectedDesigns(documentDesigns.map((d) => d.id));
    } else {
      setSelectedDesigns([]);
    }
  };

  // Handler for individual row checkboxes
  const handleSelectSingle = (id: string, checked: boolean) => {
    if (checked) {
      setSelectedDesigns((prev) => [...prev, id]);
    } else {
      setSelectedDesigns((prev) => prev.filter((designId) => designId !== id));
    }
  };

  return (
    <div className="w-full space-y-4">
      {/* Header with search and action button */}
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-neutral-800">
          Document Design List
        </h3>
        <div className="flex items-center gap-2">
          <Input type="search" placeholder="Search designs..." className="w-64" />
          <Button disabled={selectedDesigns.length === 0}>
            Compile Selected ({selectedDesigns.length})
          </Button>
        </div>
      </div>

      {/* 3. Updated table with 9 columns */}
      <div className="border rounded-md">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[50px]">
                <Checkbox
                  checked={selectAll}
                  onCheckedChange={handleSelectAll}
                  aria-label="Select all rows"
                />
              </TableHead>
              <TableHead>Design</TableHead>
              <TableHead>Design Type</TableHead>
              <TableHead>Version</TableHead>
              <TableHead>Effective Date</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Compiled By</TableHead>
              <TableHead>Last Successful Compiled Date</TableHead>
              <TableHead>Compiled Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {documentDesigns.map((design) => (
              <TableRow key={design.id}>
                <TableCell>
                  <Checkbox
                    checked={selectedDesigns.includes(design.id)}
                    onCheckedChange={(checked) => handleSelectSingle(design.id, !!checked)}
                    aria-label={`Select row ${design.id}`}
                  />
                </TableCell>
                <TableCell className="font-medium">{design.designName}</TableCell>
                <TableCell>{design.designType}</TableCell>
                <TableCell>{design.version}</TableCell>
                <TableCell>{design.effectiveDate}</TableCell>
                <TableCell>
                  <span
                    className={`px-2 py-1 text-xs font-semibold rounded-full ${design.status === "Active" ? "bg-green-100 text-green-800"
                        : design.status === "In Review" ? "bg-yellow-100 text-yellow-800"
                          : "bg-gray-100 text-gray-800"
                      }`}
                  >
                    {design.status}
                  </span>
                </TableCell>
                <TableCell>{design.compiledBy}</TableCell>
                <TableCell>{design.lastCompiledDate}</TableCell>
                <TableCell>
                  <span
                    className={`flex items-center gap-2 text-sm ${design.compiledStatus === "Success" ? "text-green-600" : "text-red-600"
                      }`}
                  >
                    <span className={`w-2 h-2 rounded-full ${design.compiledStatus === "Success" ? "bg-green-500" : "bg-red-500"
                      }`}></span>
                    {design.compiledStatus}
                  </span>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  );
};

export default DesignCompileTab;