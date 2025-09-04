import { useState, useEffect } from "react";

export interface DocumentDesign {
  id: string;
  name: string;
  designType: string;
  version: string;
  effectiveDate: string;
  status: "Active" | "Draft" | "Deprecated";
  compiledBy?: string;
  lastSuccessfulCompiledDate?: string;
  compiledStatus?: "Success" | "Failed" | "Pending";
}

export interface UseDocumentDesignsReturn {
  designs: DocumentDesign[];
  loading: boolean;
  error: string | null;
  selectedDesign: DocumentDesign | null;
  setSelectedDesign: (design: DocumentDesign | null) => void;
  refreshDesigns: () => Promise<void>;
  filterDesigns: (searchTerm: string) => DocumentDesign[];
}

export const useDocumentDesigns = (): UseDocumentDesignsReturn => {
  const [designs, setDesigns] = useState<DocumentDesign[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedDesign, setSelectedDesign] = useState<DocumentDesign | null>(null);

  // Mock data - replace with actual API call
  const mockDesigns: DocumentDesign[] = [
    {
      id: "1",
      name: "Anchor Document Template",
      designType: "Anchor",
      version: "1.0.0",
      effectiveDate: "2025-01-12",
      status: "Active",
      compiledBy: "John Doe",
      lastSuccessfulCompiledDate: "2025-01-11",
      compiledStatus: "Success",
    },
    {
      id: "2",
      name: "MasterList Financial Q3",
      designType: "MasterList",
      version: "2.1.0",
      effectiveDate: "2025-02-03",
      status: "Draft",
      compiledBy: "Jane Smith",
      lastSuccessfulCompiledDate: "2025-01-28",
      compiledStatus: "Pending",
    },
    {
      id: "3",
      name: "Collateral Marketing Brief",
      designType: "Collateral",
      version: "1.5.0",
      effectiveDate: "2025-03-15",
      status: "Active",
      compiledBy: "Mike Johnson",
      lastSuccessfulCompiledDate: "2025-03-10",
      compiledStatus: "Success",
    },
  ];

  const fetchDesigns = async () => {
    try {
      setLoading(true);
      setError(null);
      
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));
      
      // Replace this with actual API call:
      // const response = await fetch('/api/document-designs');
      // const data = await response.json();
      // setDesigns(data);
      
      setDesigns(mockDesigns);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to fetch designs");
    } finally {
      setLoading(false);
    }
  };

  const refreshDesigns = async () => {
    await fetchDesigns();
  };

  const filterDesigns = (searchTerm: string): DocumentDesign[] => {
    if (!searchTerm.trim()) return designs;
    
    const term = searchTerm.toLowerCase();
    return designs.filter(design =>
      design.name.toLowerCase().includes(term) ||
      design.designType.toLowerCase().includes(term) ||
      design.version.toLowerCase().includes(term) ||
      design.status.toLowerCase().includes(term)
    );
  };

  useEffect(() => {
    fetchDesigns();
  }, []);

  return {
    designs,
    loading,
    error,
    selectedDesign,
    setSelectedDesign,
    refreshDesigns,
    filterDesigns,
  };
};
