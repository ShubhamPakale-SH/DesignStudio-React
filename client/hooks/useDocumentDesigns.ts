// Client/hooks/useDocumentDesigns.ts

import { useState, useEffect, useCallback } from "react";

// CHANGED: Interface updated to camelCase and nullable dates
export interface DocumentDesign {
  formID: number;
  formName: string;
  displayText: string;
  documentDesignName: string;
  formDesignVersionID: number;
  versionNumber: string;
  status: string;
  statusID: number;
  compiledStatus: string;
  effectiveDate: string | null;
  compiledDate: string | null;
  errorMessage: string | null;
  addedBy: string;
}

// The custom hook
export const useDocumentDesigns = () => {
  const [designs, setDesigns] = useState<DocumentDesign[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const API_URL = "https://localhost:7129/api/v2/FormDesignCompiler/DocumentDesignList?tenantId=1";

  const fetchDesigns = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await fetch(API_URL);
      if (!response.ok) {
        throw new Error(`HTTP error! Status: ${response.status}`);
      }
      const data = await response.json();
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