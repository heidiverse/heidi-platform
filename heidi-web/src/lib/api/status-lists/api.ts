// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { jotaiStore, selectedTenantAtom } from "@/lib/atoms";
import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect } from "@/lib/utils";

export type StatusListType = "SD_JWT_VC";
export type StatusListPublishMode = "LOCAL" | "REMOTE";
export const DEFAULT_STATUS_LIST_TTL_SECONDS = 365 * 24 * 60 * 60;

export interface StatusList {
  id: string;
  tenantId: string;
  name: string;
  type: StatusListType;
  bits: 1 | 2 | 4 | 8;
  entryCount: number;
  publishMode: StatusListPublishMode;
  endpoint?: string;
  uri: string;
  signingKeyId: string;
  signingKeyName: string;
  ttl?: number;
  publishedAt?: string;
  swissStatusListId?: string;
  swissStatusListUrl?: string;
  swissPublishedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateStatusList {
  name: string;
  type: StatusListType;
  bits: StatusList["bits"];
  entryCount: number;
  publishMode: StatusListPublishMode;
  endpoint?: string;
  signingKeyId: string;
  ttl?: number;
}

function url(path = "", tenantId?: string) {
  const result = new URL(
    `management/v1/status-lists${path}`,
    runtimeConfig.heidiApiBaseUrl,
  );
  const requestedTenant = tenantId ?? jotaiStore.get(selectedTenantAtom);
  if (requestedTenant) result.searchParams.set("tenantId", requestedTenant);
  return result;
}

async function json<T>(response: Response, message: string): Promise<T> {
  if (!response.ok) throw new Error((await response.text()) || message);
  return response.json() as Promise<T>;
}

export async function getStatusLists(tenantId?: string) {
  return json<StatusList[]>(
    await fetchWithRedirect(url("", tenantId)),
    "Failed to load status lists",
  );
}

export async function getStatusList(id: string) {
  return json<StatusList>(
    await fetchWithRedirect(url(`/${id}`)),
    "Failed to load status list",
  );
}

export async function createStatusList(value: CreateStatusList) {
  return json<StatusList>(
    await fetchWithRedirect(url(), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(value),
    }),
    "Failed to create status list",
  );
}

export async function publishStatusList(id: string) {
  return json<StatusList>(
    await fetchWithRedirect(url(`/${id}/publish`), { method: "PUT" }),
    "Failed to publish status list",
  );
}

export async function publishStatusListToSwitzerland(id: string) {
  return json<StatusList>(
    await fetchWithRedirect(url(`/${id}/publish/switzerland`), { method: "PUT" }),
    "Failed to publish status list to the Swiss registry",
  );
}

export async function getStatusListEntry(id: string, index: number) {
  return json<{ index: number; status: number }>(
    await fetchWithRedirect(url(`/${id}/entries/${index}`)),
    "Failed to load status",
  );
}

export async function setStatusListEntry(
  id: string,
  index: number,
  status: number,
) {
  return json<{ index: number; status: number }>(
    await fetchWithRedirect(url(`/${id}/entries`), {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ index, status }),
    }),
    "Failed to update status",
  );
}
