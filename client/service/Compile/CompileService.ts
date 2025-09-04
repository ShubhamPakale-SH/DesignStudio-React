import { BASE_URL } from "../config";

// DocumentDesign interface adapted from useDocumentDesigns.ts
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

const FEATURE_BASE = `${BASE_URL}/FormDesignCompiler`;

export async function fetchDocumentDesignList(): Promise<DocumentDesign[]> {
  const url = `${FEATURE_BASE}/DocumentDesignList?tenantId=1`;
  
  console.log("Fetching document designs from:", url);
  
  const res = await fetch(url, {
    method: "GET",
    headers: { Accept: "application/json" },
  });
  
  console.log("Fetch response status:", res.status);
  
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`HTTP error! Status: ${res.status} - ${text}`);
  }
  
  const data = await res.json();
  console.log("Fetched data:", data);
  
  // Map API rows to camelCase (same logic as useDocumentDesigns)
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
  
  return mappedRows;
}

export async function fetchCompileData(): Promise<unknown> {
  const url = `${FEATURE_BASE}/data`;
  const res = await fetch(url, {
    method: "GET",
    headers: { Accept: "application/json" },
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Compile data fetch failed (${res.status}): ${text}`);
  }
  return res.json();
}
