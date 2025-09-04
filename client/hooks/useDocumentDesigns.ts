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
        console.log("Fetching document designs from:", API_URL);
        try {
            const response = await fetch(API_URL);
            console.log("Fetch response status:", response.status);
            if (!response.ok) {
                throw new Error(`HTTP error! Status: ${response.status}`);
            }
            const data = await response.json();
            console.log("Fetched data:", data);
            // Map API rows to camelCase
            const mappedRows = (data.rows || []).map((row: any) => ({
                formID: row.FormID,
                formName: row.FormName,
                displayText: row.DisplayText,
                documentDesignName: row.DocumentDesignName,
                formDesignVersionID: row.FormDesignVersionID,
                versionNumber: row.VersionNumber,
                status: row.Status,
                statusID: row.StatusID,
                compiledStatus: row.CompiledStatus,
                effectiveDate: row.EffectiveDate ?? null,
                compiledDate: row.CompiledDate ?? null,
                errorMessage: row.ErrorMessage ?? null,
                addedBy: row.AddedBy,
            }));
            setDesigns(mappedRows);
        } catch (e: any) {
            setError(`Failed to fetch data: ${e.message}`);
            console.error("Error fetching document designs:", e);
        } finally {
            setIsLoading(false);
            console.log("Fetch complete. isLoading set to false.");
        }
    }, []);

    useEffect(() => {
        fetchDesigns();
    }, [fetchDesigns]);

    return { designs, isLoading, error, refetch: fetchDesigns };
};