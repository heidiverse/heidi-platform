// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { createIssuerDefinition } from "@/lib/api/issuer-definitions/api";
import {
  deleteSettingsForOrganisation,
  getRequiredSettingsForOrganisation,
  updateSettings,
} from "@/lib/api/settings/api";
import { jotaiStore, selectedTenantAtom } from "@/lib/atoms";
import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect } from "@/lib/utils";

export type Organisation = {
  tenantId: string;
  displayName: string;
  revoked: boolean;
};

/** The subset of `OrganisationResponse` this module needs. */
type OrganisationResponse = {
  tenantId: string;
  displayName: string | null;
  deleted: boolean;
};

function toOrganisation({
  tenantId,
  displayName,
  deleted,
}: OrganisationResponse): Organisation {
  return {
    tenantId: tenantId,
    // Organisations created before display names existed fall back to their id.
    displayName: displayName || tenantId,
    revoked: deleted,
  };
}

export async function getOrganisations() {
  const res = await fetchWithRedirect(
    new URL("management/v1/tenants/list", runtimeConfig.heidiApiBaseUrl),
  );
  if (!res.ok) {
    throw new Error("Failed to fetch Organisations");
  }
  return ((await res.json()) as OrganisationResponse[])
    .map(toOrganisation)
    .sort((a, b) => a.displayName.localeCompare(b.displayName));
}

export async function getOrganisationByTenantId(tenantId: string) {
  const res = await fetchWithRedirect(
    new URL(
      `management/v1/tenants/${tenantId}`,
      runtimeConfig.heidiApiBaseUrl,
    ),
  );
  if (!res.ok) {
    throw new Error("Failed to fetch Organisation");
  }
  return toOrganisation((await res.json()) as OrganisationResponse);
}

export async function createOrganisation(data: Omit<Organisation, "revoked">) {
  // Create the tenant first so the API chooses its deployment-configured language.
  await updateSettings({
    tenantId: data.tenantId,
    displayName: data.displayName,
    thumbnail: "",
  });

  const settings = await getRequiredSettingsForOrganisation(data.tenantId);
  const issuerDefinition = await createIssuerDefinition({
    tenantId: data.tenantId,
    displayName: { [settings.defaultLanguage]: data.displayName },
    logo: "",
    slug: data.tenantId,
  });

  return issuerDefinition;
}

export async function updateOrganisation({
  status,
  ...data
}: Omit<Organisation, "revoked"> & {
  status?: "inactive" | "active";
}) {
  // Writing an organisation clears its deactivation, so "active" needs nothing
  // beyond this call and "inactive" has to follow it rather than precede it.
  await updateSettings({
    tenantId: data.tenantId,
    displayName: data.displayName,
    thumbnail: null,
  });

  if (status === "inactive") {
    await deleteSettingsForOrganisation(data.tenantId);
  }
}

export async function deleteOrganisationBySlug(tenantId: string) {
  await deleteSettingsForOrganisation(tenantId);
  jotaiStore.set(selectedTenantAtom, null);
}
