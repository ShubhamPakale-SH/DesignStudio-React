// Client/components/your-folder/DesignCompileTab.tsx

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
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { RefreshCw, Copy, GitMerge, Download } from "lucide-react";
import { useDocumentDesigns, DocumentDesign } from "@/hooks/useDocumentDesigns";

const REFRESH_INTERVAL_SECONDS = 60;

const DesignCompileTab = () => {
  
  const {
    designs: documentDesigns,
    isLoading,
    error,
    refetch,
  } = useDocumentDesigns();

  const [selectedDesigns, setSelectedDesigns] = useState<number[]>([]);
  const [selectAll, setSelectAll] = useState(false);
  const [filters, setFilters] = useState({
    FormName: "",
    DocumentDesignName: "",
    VersionNumber: "",
    EffectiveDate: "",
    Status: "",
    AddedBy: "",
    CompiledDate: "",
    CompiledStatus: "",
  });
  const [filteredDesigns, setFilteredDesigns] = useState<DocumentDesign[]>([]);
  const [countdown, setCountdown] = useState(REFRESH_INTERVAL_SECONDS);

  useEffect(() => {
    const timer = setInterval(() => {
      setCountdown((prevCountdown) => {
        if (prevCountdown <= 1) {
          console.log("Auto-refreshing grid data...");
          refetch(); 
          return REFRESH_INTERVAL_SECONDS;
        }
        return prevCountdown - 1;
      });
    }, 1000);
    return () => clearInterval(timer); 
  }, [refetch]);

  useEffect(() => {
    let data = [...documentDesigns];
    data = data.filter(
      (d) =>
        d.FormName.toLowerCase().includes(filters.FormName.toLowerCase()) &&
        d.DocumentDesignName.toLowerCase().includes(
          filters.DocumentDesignName.toLowerCase()
        ) &&
        d.VersionNumber.toLowerCase().includes(
          filters.VersionNumber.toLowerCase()
        ) &&
        d.EffectiveDate.toLowerCase().includes(
          filters.EffectiveDate.toLowerCase()
        ) &&
        d.Status.toLowerCase().includes(filters.Status.toLowerCase()) &&
        d.AddedBy.toLowerCase().includes(filters.AddedBy.toLowerCase()) &&
        d.CompiledDate.toLowerCase().includes(
          filters.CompiledDate.toLowerCase()
        ) &&
        d.CompiledStatus.toLowerCase().includes(
          filters.CompiledStatus.toLowerCase()
        )
    );
    setFilteredDesigns(data);
  }, [filters, documentDesigns]);

  useEffect(() => {
    setSelectAll(
      filteredDesigns.length > 0 &&
      selectedDesigns.length === filteredDesigns.length
    );
  }, [selectedDesigns, filteredDesigns]);

  const handleFilterChange = (
    column: keyof typeof filters,
    value: string
  ) => {
    setFilters((prev) => ({ ...prev, [column]: value }));
  };

  const handleSelectAll = (checked: boolean) => {
    setSelectAll(checked);
    if (checked) {
      setSelectedDesigns(filteredDesigns.map((d) => d.FormID));
    } else {
      setSelectedDesigns([]);
    }
  };

  const handleSelectSingle = (id: number, checked: boolean) => {
    if (checked) {
      setSelectedDesigns((prev) => [...prev, id]);
    } else {
      setSelectedDesigns((prev) => prev.filter((designId) => designId !== id));
    }
  };

  const getCompiledStatusClass = (status: string) => {
    switch (status) {
      case "Complete":
        return "text-green-600 font-medium";
      case "Errored":
        return "text-red-600 font-medium underline";
      case "Queued":
        return "text-yellow-600 font-medium";
      default:
        return "text-gray-800";
    }
  };

  if (isLoading) {
    return <div className="p-8 text-center">Loading designs...</div>;
  }

  if (error) {
    return <div className="p-8 text-center text-red-600">Error: {error}</div>;
  }

  return (
    <TooltipProvider>
      <div className="w-full space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-semibold text-neutral-800">
            Document Design List
          </h3>
          <div className="flex items-center gap-2">
            <p className="text-sm font-medium text-red-600 mr-4">
              Auto-Refresh in {countdown} seconds.
            </p>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button variant="outline" size="icon" onClick={refetch}>
                  <RefreshCw className="h-4 w-4" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                <p>Reload Grid</p>
              </TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button variant="outline" size="icon">
                  <Copy className="h-4 w-4" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                <p>Bulk Compile</p>
              </TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button variant="outline" size="icon">
                  <GitMerge className="h-4 w-4" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                <p>Bulk Sync</p>
              </TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button variant="outline" size="icon">
                  <Download className="h-4 w-4" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                <p>Compile Log Download</p>
              </TooltipContent>
            </Tooltip>
          </div>
        </div>

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
              <TableRow className="bg-slate-50 hover:bg-slate-50">
                <TableCell></TableCell>
                <TableCell>
                  <Input
                    placeholder="Filter..."
                    value={filters.FormName}
                    onChange={(e) =>
                      handleFilterChange("FormName", e.target.value)
                    }
                  />
                </TableCell>
                <TableCell>
                  <Input
                    placeholder="Filter..."
                    value={filters.DocumentDesignName}
                    onChange={(e) =>
                      handleFilterChange("DocumentDesignName", e.target.value)
                    }
                  />
                </TableCell>
                <TableCell>
                  <Input
                    placeholder="Filter..."
                    value={filters.VersionNumber}
                    onChange={(e) =>
                      handleFilterChange("VersionNumber", e.target.value)
                    }
                  />
                </TableCell>
                <TableCell>
                  <Input
                    placeholder="Filter..."
                    value={filters.EffectiveDate}
                    onChange={(e) =>
                      handleFilterChange("EffectiveDate", e.target.value)
                    }
                  />
                </TableCell>
                <TableCell>
                  <Input
                    placeholder="Filter..."
                    value={filters.Status}
                    onChange={(e) =>
                      handleFilterChange("Status", e.target.value)
                    }
                  />
                </TableCell>
                <TableCell>
                  <Input
                    placeholder="Filter..."
                    value={filters.AddedBy}
                    onChange={(e) =>
                      handleFilterChange("AddedBy", e.target.value)
                    }
                  />
                </TableCell>
                <TableCell>
                  <Input
                    placeholder="Filter..."
                    value={filters.CompiledDate}
                    onChange={(e) =>
                      handleFilterChange("CompiledDate", e.target.value)
                    }
                  />
                </TableCell>
                <TableCell>
                  <Input
                    placeholder="Filter..."
                    value={filters.CompiledStatus}
                    onChange={(e) =>
                      handleFilterChange("CompiledStatus", e.target.value)
                    }
                  />
                </TableCell>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredDesigns.map((design) => (
                <TableRow key={design.FormID}>
                  <TableCell>
                    <Checkbox
                      checked={selectedDesigns.includes(design.FormID)}
                      onCheckedChange={(checked) =>
                        handleSelectSingle(design.FormID, !!checked)
                      }
                      aria-label={`Select row ${design.FormID}`}
                    />
                  </TableCell>
                  <TableCell className="font-medium">{design.FormName}</TableCell>
                  <TableCell>{design.DocumentDesignName}</TableCell>
                  <TableCell>{design.VersionNumber}</TableCell>
                  <TableCell>
                    {new Date(design.EffectiveDate).toLocaleDateString()}
                  </TableCell>
                  <TableCell>{design.Status}</TableCell>
                  <TableCell>{design.AddedBy}</TableCell>
                  <TableCell>
                    {new Date(design.CompiledDate).toLocaleString()}
                  </TableCell>
                  <TableCell>
                    <span
                      className={getCompiledStatusClass(design.CompiledStatus)}
                    >
                      {design.CompiledStatus}
                    </span>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </div>
    </TooltipProvider>
  );
};

export default DesignCompileTab;