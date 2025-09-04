import { BASE_URL } from "../config";

const FEATURE_BASE = `${BASE_URL}/Compile`;

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
