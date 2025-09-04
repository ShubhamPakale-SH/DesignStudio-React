// DesignCompileTab.tsx

import { useState, useEffect, useCallback } from "react";
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
// 1. Updated import to use the service file from the correct path
import {
    fetchDocumentDesignList,
    DocumentDesign,
} from "@/service/Compile/CompileService";

const REFRESH_INTERVAL_SECONDS = 60;

const DesignCompileTab = () => {
    // 2. States for data, loading, and error are now managed directly in this component
    const [documentDesigns, setDocumentDesigns] = useState<DocumentDesign[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    // States for UI interactions (unchanged)
    const [selectedDesigns, setSelectedDesigns] = useState<number[]>([]);
    const [selectAll, setSelectAll] = useState(false);
    const [filters, setFilters] = useState({
        formName: "",
        documentDesignName: "",
        versionNumber: "",
        effectiveDate: "",
        status: "",
        addedBy: "",
        compiledDate: "",
        compiledStatus: "",
    });
    const [filteredDesigns, setFilteredDesigns] = useState<DocumentDesign[]>([]);
    const [countdown, setCountdown] = useState(REFRESH_INTERVAL_SECONDS);

    // 3. Data fetching logic is now inside the component, wrapped in useCallback
    const refetch = useCallback(async () => {
        setIsLoading(true);
        setError(null);
        try {
            // Call the service function to get the data
            const data = await fetchDocumentDesignList();
            setDocumentDesigns(data);
        } catch (e: any) {
            setError(`Failed to fetch data: ${e.message}`);
            console.error(e);
        } finally {
            setIsLoading(false);
        }
    }, []); // Empty dependency array creates the function once

    // 4. useEffect to trigger the initial data fetch when the component mounts
    useEffect(() => {
        refetch();
    }, [refetch]);

    // Effect for the auto-refresh timer (unchanged, still uses `refetch`)
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

    // Effect for client-side filtering (unchanged)
    useEffect(() => {
        let data = [...documentDesigns];
        data = data.filter(
            (d) =>
                (d.formName ?? "")
                    .toLowerCase()
                    .includes(filters.formName.toLowerCase()) &&
                (d.documentDesignName ?? "")
                    .toLowerCase()
                    .includes(filters.documentDesignName.toLowerCase()) &&
                (d.versionNumber ?? "")
                    .toLowerCase()
                    .includes(filters.versionNumber.toLowerCase()) &&
                ((d.effectiveDate ?? "") + "")
                    .toLowerCase()
                    .includes(filters.effectiveDate.toLowerCase()) &&
                (d.status ?? "").toLowerCase().includes(filters.status.toLowerCase()) &&
                (d.addedBy ?? "").toLowerCase().includes(filters.addedBy.toLowerCase()) &&
                ((d.compiledDate ?? "") + "")
                    .toLowerCase()
                    .includes(filters.compiledDate.toLowerCase()) &&
                (d.compiledStatus ?? "")
                .trim()
                    .toLowerCase()
                    .includes(filters.compiledStatus.trim().toLowerCase())
        );
        setFilteredDesigns(data);
    }, [filters, documentDesigns]);

    // All other handlers and helper functions remain exactly the same
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
            setSelectedDesigns(filteredDesigns.map((d) => d.formID));
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
            case "Finalized":
                return "text-green-600 font-medium";
            case "Errored":
                return "text-red-600 font-medium underline";
            case "Queued":
                return "text-yellow-600 font-medium";
        }
    };

    if (isLoading) {
        return <div className="p-8 text-center">Loading designs...</div>;
    }

    if (error) {
        return <div className="p-8 text-center text-red-600">Error: {error}</div>;
    }

    // The entire JSX render block is unchanged
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
                                        value={filters.formName}
                                        onChange={(e) =>
                                            handleFilterChange("formName", e.target.value)
                                        }
                                    />
                                </TableCell>
                                <TableCell>
                                    <Input
                                        placeholder="Filter..."
                                        value={filters.documentDesignName}
                                        onChange={(e) =>
                                            handleFilterChange("documentDesignName", e.target.value)
                                        }
                                    />
                                </TableCell>
                                <TableCell>
                                    <Input
                                        placeholder="Filter..."
                                        value={filters.versionNumber}
                                        onChange={(e) =>
                                            handleFilterChange("versionNumber", e.target.value)
                                        }
                                    />
                                </TableCell>
                                <TableCell>
                                    <Input
                                        placeholder="Filter..."
                                        value={filters.effectiveDate}
                                        onChange={(e) =>
                                            handleFilterChange("effectiveDate", e.target.value)
                                        }
                                    />
                                </TableCell>
                                <TableCell>
                                    <Input
                                        placeholder="Filter..."
                                        value={filters.status}
                                        onChange={(e) =>
                                            handleFilterChange("status", e.target.value)
                                        }
                                    />
                                </TableCell>
                                <TableCell>
                                    <Input
                                        placeholder="Filter..."
                                        value={filters.addedBy}
                                        onChange={(e) =>
                                            handleFilterChange("addedBy", e.target.value)
                                        }
                                    />
                                </TableCell>
                                <TableCell>
                                    <Input
                                        placeholder="Filter..."
                                        value={filters.compiledDate}
                                        onChange={(e) =>
                                            handleFilterChange("compiledDate", e.target.value)
                                        }
                                    />
                                </TableCell>
                                <TableCell>
                                    <Input
                                        placeholder="Filter..."
                                        value={filters.compiledStatus}
                                        onChange={(e) =>
                                            handleFilterChange("compiledStatus", e.target.value)
                                        }
                                    />
                                </TableCell>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {filteredDesigns.map((design) => (
                                <TableRow key={design.formID}>
                                    <TableCell>
                                        <Checkbox
                                            checked={selectedDesigns.includes(design.formID)}
                                            onCheckedChange={(checked) =>
                                                handleSelectSingle(design.formID, !!checked)
                                            }
                                            aria-label={`Select row ${design.formID}`}
                                        />
                                    </TableCell>
                                    <TableCell className="font-medium">
                                        {design.formName ?? ""}
                                    </TableCell>
                                    <TableCell>{design.documentDesignName ?? ""}</TableCell>
                                    <TableCell>{design.versionNumber ?? ""}</TableCell>
                                    <TableCell>
                                        {design.effectiveDate
                                            ? new Date(design.effectiveDate).toLocaleDateString()
                                            : "N/A"}
                                    </TableCell>
                                    <TableCell>{design.status ?? ""}</TableCell>
                                    <TableCell>{design.addedBy ?? ""}</TableCell>
                                    <TableCell>
                                        {design.compiledDate
                                            ? new Date(design.compiledDate).toLocaleString()
                                            : "N/A"}
                                    </TableCell>
                                    <TableCell>
                                        <span
                                            className={getCompiledStatusClass(
                                                design.compiledStatus
                                            )}
                                        >
                                            {design.compiledStatus ?? ""}
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