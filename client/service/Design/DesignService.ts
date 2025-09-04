import { FORM_DESIGN_TYPES } from "../api-endpoints";
import { BASE_URL } from "../config";

const FEATURE_BASE = `${BASE_URL}/FormDesign`;

export async function fetchDesignTypes(): Promise<unknown> {
  const url = `${FEATURE_BASE}/${FORM_DESIGN_TYPES}`;
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
