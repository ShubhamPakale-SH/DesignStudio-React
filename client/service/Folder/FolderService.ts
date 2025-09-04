import { FORM_DESIGN_GROUP_LIST } from "../api-endpoints";
import { BASE_URL } from "../config";


export type FormDesignGroupItem = Record<string, unknown>;

export async function fetchFormDesignGroupList(): Promise<unknown> {
  const url = `${BASE_URL}/${FORM_DESIGN_GROUP_LIST}`;
  const res = await fetch(url, {
    method: "GET",
    headers: {
      Accept: "application/json",
    },
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(
      `FormDesignGroupList request failed (${res.status}): ${text}`,
    );
  }
  return res.json();
}
