import {
  Document_Design_List,
  Form_DesignList_ByDocType,
  FormDesign_VersionList,
} from "../api-endpoints";
import { BASE_URL } from "../config";

export interface DesignType { id: number | string; name: string }

export async function fetchDesignTypes(): Promise<DesignType[]> {
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

  const extractId = (item: any): number | string | null => {
    if (!item || typeof item !== "object") return null;
    return (
      item.DocumentDesignTypeID ??
      item.documentDesignTypeId ??
      item.DocumentDesignTypeId ??
      item.Id ??
      item.id ??
      null
    );
  };

  let items: any[] = [];
  if (Array.isArray(data)) items = data;
  else if (data && Array.isArray((data as any).rows)) items = (data as any).rows;

  const mapped: DesignType[] = items
    .map((it) => ({ id: extractId(it), name: extractName(it) }))
    .filter((x): x is DesignType => x.id != null && !!x.name && String(x.name).trim().length > 0);

  // Deduplicate by id, keep first occurrence
  const seen = new Set<string>();
  const unique = mapped.filter((t) => {
    const key = String(t.id);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });

  return unique;
}

export async function fetchFormDesignListByDocType(
  documentDesignTypeId: number | string,
): Promise<unknown> {
  const base = `${BASE_URL}/${Form_DesignList_ByDocType}${encodeURIComponent(String(documentDesignTypeId))}`;
  const url = base.includes("?")
    ? `${base}&_=${Date.now()}`
    : `${base}?_=${Date.now()}`;
  const res = await fetch(url, {
    method: "GET",
    headers: { Accept: "application/json" },
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`FormDesignListByDocType failed (${res.status}): ${text}`);
  }
  return res.json();
}

export async function fetchFormDesignVersionList(
  formId: number | string,
): Promise<unknown> {
  const base = `${BASE_URL}/${FormDesign_VersionList}${encodeURIComponent(String(formId))}`;
  const url = base.includes("?")
    ? `${base}&_=${Date.now()}`
    : `${base}?_=${Date.now()}`;
  const res = await fetch(url, {
    method: "GET",
    headers: { Accept: "application/json" },
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`FormDesignVersionList failed (${res.status}): ${text}`);
  }
  return res.json();
}
