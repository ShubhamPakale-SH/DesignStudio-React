import {
  FORM_DESIGN_GROUP_LIST,
  FORM_GROUP_MAPPING_LIST_BASE,
} from "../api-endpoints";
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

export async function fetchFormGroupMappingList(
  formGroupId: number | string,
): Promise<unknown> {
  const url = `${BASE_URL}/${FORM_GROUP_MAPPING_LIST_BASE}${encodeURIComponent(String(formGroupId))}`;
  const res = await fetch(url, {
    method: "GET",
    headers: {
      Accept: "application/json",
    },
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(
      `FormGroupMappingList request failed (${res.status}): ${text}`,
    );
  }
  return res.json();
}
