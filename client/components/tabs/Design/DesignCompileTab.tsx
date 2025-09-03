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
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { RefreshCw, Copy, GitMerge, Download } from "lucide-react";

const documentDesigns = [
  // ... (mock data remains the same)
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

const REFRESH_INTERVAL_SECONDS = 60;

const DesignCompileTab = () => {
  // ... (other states remain the same)
  const [selectedDesigns, setSelectedDesigns] = useState<string[]>([]);
  const [selectAll, setSelectAll] = useState(false);
  const [filters, setFilters] = useState({
    designName: "",
    designType: "",
    version: "",
    effectiveDate: "",
    status: "",
    compiledBy: "",
    lastCompiledDate: "",
    compiledStatus: "",
  });
  const [filteredDesigns, setFilteredDesigns] = useState(documentDesigns);
  
  // 1. State for the countdown timer
  const [countdown, setCountdown] = useState(REFRESH_INTERVAL_SECONDS);

  // 2. useEffect for the timer logic
  useEffect(() => {
    // Function to simulate a refresh action
    const handleRefresh = () => {
      console.log("Refreshing grid data...");
      // In a real app, you would re-fetch your data here.
      // For example: fetchData().then(newData => setFilteredDesigns(newData));
    };

    // Set up an interval to decrement the counter every second
    const timer = setInterval(() => {
      setCountdown((prevCountdown) => {
        if (prevCountdown <= 1) {
          // When countdown reaches 0, refresh and reset the timer
          handleRefresh();
          return REFRESH_INTERVAL_SECONDS;
        }
        return prevCountdown - 1;
      });
    }, 1000);

    // Cleanup function to clear the interval when the component unmounts
    return () => clearInterval(timer);
  }, []); // Empty dependency array ensures this runs only once on mount

  // ... (other useEffects and handlers remain the same)
  useEffect(() => {
    let data = [...documentDesigns];
    data = data.filter(d =>
      d.designName.toLowerCase().includes(filters.designName.toLowerCase()) &&
      d.designType.toLowerCase().includes(filters.designType.toLowerCase()) &&
      d.version.toLowerCase().includes(filters.version.toLowerCase()) &&
      d.effectiveDate.toLowerCase().includes(filters.effectiveDate.toLowerCase()) &&
      d.status.toLowerCase().includes(filters.status.toLowerCase()) &&
      d.compiledBy.toLowerCase().includes(filters.compiledBy.toLowerCase()) &&
      d.lastCompiledDate.toLowerCase().includes(filters.lastCompiledDate.toLowerCase()) &&
      d.compiledStatus.toLowerCase().includes(filters.compiledStatus.toLowerCase())
    );
    setFilteredDesigns(data);
  }, [filters]);

  const handleFilterChange = (column: keyof typeof filters, value: string) => {
    setFilters(prev => ({ ...prev, [column]: value }));
  };

  useEffect(() => {
    setSelectAll(filteredDesigns.length > 0 && selectedDesigns.length === filteredDesigns.length);
  }, [selectedDesigns, filteredDesigns]);

  const handleSelectAll = (checked: boolean) => {
    setSelectAll(checked);
    if (checked) {
      setSelectedDesigns(filteredDesigns.map((d) => d.id));
    } else {
      setSelectedDesigns([]);
    }
  };
  
  const handleSelectSingle = (id: string, checked: boolean) => {
    if (checked) {
      setSelectedDesigns((prev) => [...prev, id]);
    } else {
      setSelectedDesigns((prev) => prev.filter((designId) => designId !== id));
    }
  };


  return (
    <TooltipProvider>
      <div className="w-full space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-semibold text-neutral-800">
            Document Design List
          </h3>
          <div className="flex items-center gap-2">
            {/* 3. Added the countdown timer text */}
            <p className="text-sm font-medium text-red-600 mr-4">
              Auto-Refresh in {countdown} seconds.
            </p>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button variant="outline" size="icon">
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
         {/* Table remains unchanged */}
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
              <TableRow className="bg-slate-50">
                <TableCell></TableCell>
                <TableCell><Input placeholder="Filter..." value={filters.designName} onChange={e => handleFilterChange("designName", e.target.value)} /></TableCell>
                <TableCell><Input placeholder="Filter..." value={filters.designType} onChange={e => handleFilterChange("designType", e.target.value)} /></TableCell>
                <TableCell><Input placeholder="Filter..." value={filters.version} onChange={e => handleFilterChange("version", e.target.value)} /></TableCell>
                <TableCell><Input placeholder="Filter..." value={filters.effectiveDate} onChange={e => handleFilterChange("effectiveDate", e.target.value)} /></TableCell>
                <TableCell><Input placeholder="Filter..." value={filters.status} onChange={e => handleFilterChange("status", e.target.value)} /></TableCell>
                <TableCell><Input placeholder="Filter..." value={filters.compiledBy} onChange={e => handleFilterChange("compiledBy", e.target.value)} /></TableCell>
                <TableCell><Input placeholder="Filter..." value={filters.lastCompiledDate} onChange={e => handleFilterChange("lastCompiledDate", e.target.value)} /></TableCell>
                <TableCell><Input placeholder="Filter..." value={filters.compiledStatus} onChange={e => handleFilterChange("compiledStatus", e.target.value)} /></TableCell>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredDesigns.map((design) => (
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
                    className={`px-2 py-1 text-xs font-semibold rounded-full ${
                      design.status === "Active" ? "bg-green-100 text-green-800"
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
                    className={`flex items-center gap-2 text-sm ${
                      design.compiledStatus === "Success" ? "text-green-600" : "text-red-600"
                    }`}
                  >
                    <span className={`w-2 h-2 rounded-full ${
                      design.compiledStatus === "Success" ? "bg-green-500" : "bg-red-500"
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
    </TooltipProvider>
  );
};

export default DesignCompileTab;