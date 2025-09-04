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
  return res.json();
}
