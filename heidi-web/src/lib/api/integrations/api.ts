// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { jotaiStore, selectedTenantAtom } from "@/lib/atoms";
import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect } from "@/lib/utils";

export interface IntegrationPayload {
  id: string;
  tenantId: string;
  displayName: string;
  credentialIdentifiers: string[];
  scopes: ("issue" | "verify")[] | null;
}

interface IntegrationResponse extends IntegrationPayload {
  apiKey: string;
}

export async function getIntegrations() {
  const url = new URL(
    "management/v1/integrations/overview",
    runtimeConfig.heidiApiBaseUrl,
  );
  url.searchParams.set("isPublic", "false");
  const res = await fetchWithRedirect(url);
  if (!res.ok) {
    throw new Error("Failed to get integrations");
  }
  return ((await res.json()) as { integrations: IntegrationPayload[] })
    .integrations;
}

export async function getIntegration(id: string) {
  const res = await fetchWithRedirect(
    new URL(
      `management/v1/integrations/${id}`,
      runtimeConfig.heidiApiBaseUrl,
    ),
  );
  if (!res.ok) {
    throw new Error("Failed to get integration");
  }
  return (await res.json()) as IntegrationResponse;
}

export async function createIntegration({
  id,
  tenantId,
  ...data
}: IntegrationPayload) {
  const selectedTenant = jotaiStore.get(selectedTenantAtom);
  const url = new URL(
    "management/v1/integrations/create",
    runtimeConfig.heidiApiBaseUrl,
  );
  selectedTenant && url.searchParams.set("tenantId", selectedTenant);
  const res = await fetchWithRedirect(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  });
  if (!res.ok) {
    throw new Error("Failed to create integrations");
  }
  return (await res.json()) as IntegrationResponse;
}

export async function updateIntegration({
  id,
  tenantId,
  ...data
}: IntegrationPayload) {
  const res = await fetchWithRedirect(
    new URL(
      `management/v1/integrations/update/${id}`,
      runtimeConfig.heidiApiBaseUrl,
    ),
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    },
  );
  if (!res.ok) {
    throw new Error("Failed to update integration");
  }
  return (await res.json()) as IntegrationResponse;
}

export async function deleteIntegration(id: string) {
  const res = await fetchWithRedirect(
    new URL(
      `management/v1/integrations/delete/${id}`,
      runtimeConfig.heidiApiBaseUrl,
    ),
    { method: "DELETE" },
  );
  if (!res.ok) {
    throw new Error("Failed to delete integration");
  }
}
