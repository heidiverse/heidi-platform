// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { OrganisationFeatures } from "@/lib/organisation-features";
import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect } from "@/lib/utils";

export async function getOrganisationFeatures(tenantId: string) {
  const res = await fetchWithRedirect(
    new URL(
      `management/v1/tenants/${tenantId}/features`,
      runtimeConfig.heidiApiBaseUrl,
    ),
  );
  if (!res.ok) {
    throw new Error("Failed to fetch organisation features");
  }
  return (await res.json()) as OrganisationFeatures;
}

export async function updateOrganisationFeatures(
  tenantId: string,
  features: OrganisationFeatures,
) {
  const res = await fetchWithRedirect(
    new URL(
      `management/v1/tenants/${tenantId}/features`,
      runtimeConfig.heidiApiBaseUrl,
    ),
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(features),
    },
  );
  if (!res.ok) {
    throw new Error("Could not update organisation features");
  }
}
