// Client/hooks/useDocumentDesigns.ts

import { useState, useEffect, useCallback } from "react";

export interface DocumentDesign {
  FormID: number;
  DisplayText: string;
  DocumentDesignName: string;
  VersionNumber: string;
  Status: string;
  CompiledStatus: string;
  EffectiveDate: string;
  CompiledDate: string;
  AddedBy: string;
}

export const useDocumentDesigns = () => {
  const [designs, setDesigns] = useState<DocumentDesign[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const API_URL =
    "https://localhost:7129/api/v2/FormDesignCompiler/DocumentDesignList";

  // useCallback ensures the function isn't recreated on every render
  const fetchDesigns = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await fetch(API_URL);
      if (!response.ok) {
        throw new Error(`HTTP error! Status: ${response.status}`);
      }
      const data = await response.json();
      // The actual data is in the 'rows' property of the response
      setDesigns(data.rows || []);
    } catch (e: any) {
      setError(`Failed to fetch data: ${e.message}`);
      console.error(e);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDesigns();
  }, [fetchDesigns]);

  return { designs, isLoading, error, refetch: fetchDesigns };
};
