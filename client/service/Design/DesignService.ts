import { Document_Design_List, Form_DesignList_ByDocType, FormDesign_VersionList } from "../api-endpoints";
import { BASE_URL } from "../config";

export async function fetchDesignTypes(): Promise<string[]> {
  // Build URL and add a cache-busting param to avoid stale responses
  const base = `${BASE_URL}/${Document_Design_List}`;
  const url = base.includes("?")
    ? `${base}&_=${Date.now()}`
    : `${base}?_=${Date.now()}`;

  const res = await fetch(url, {
    method: "GET",
    headers: { Accept: "application/json" },
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`GetDocumentDesignType failed (${res.status}): ${text}`);
  }
  const data = await res.json();

  const extractName = (item: any): string | null => {
    if (!item) return null;
    if (typeof item === "string") return item;
    if (typeof item !== "object") return null;
    return (
      item.DocumentDesignName ??
      item.documentDesignName ??
      item.DocumentDesignType ??
      item.documentDesignType ??
      item.DisplayText ??
      item.displayText ??
      item.Name ??
      item.name ??
      null
    );
  };

  let types: string[] = [];
  if (Array.isArray(data)) {
    types = data.map(extractName).filter((v): v is string => Boolean(v));
  } else if (data && Array.isArray((data as any).rows)) {
    types = (data as any).rows
      .map(extractName)
      .filter((v): v is string => Boolean(v));
  }

  // Deduplicate and keep order of first occurrence
  const seen = new Set<string>();
  const unique = types.filter((t) => {
    const key = t.trim();
    if (!key || seen.has(key)) return false;
    seen.add(key);
    return true;
  });

  return unique;
}
