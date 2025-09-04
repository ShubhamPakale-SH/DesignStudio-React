import { Document_Design_List } from "../api-endpoints";
import { BASE_URL } from "../config";

export async function fetchDesignTypes(): Promise<string[]> {
  const url = `${BASE_URL}/${Document_Design_List}`;
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
    if (typeof item === "string") return item;
    if (!item || typeof item !== "object") return null;
    return (
      item.name ??
      item.Name ??
      item.documentDesignType ??
      item.DocumentDesignType ??
      item.displayText ??
      item.DisplayText ??
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

  // Deduplicate and sort for stable UI
  const unique = Array.from(new Set(types)).filter((t) => t.trim().length > 0);
  return unique;
}
