// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { DEFAULT_LOCALE } from "@/lib/constants";
import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect } from "@/lib/utils";
import { parseSettingsResponse } from "./response";

export type SupportedWallet = {
  name: string;
  displayName: string;
  appIconUri: string;
  universalLink: string;
};

export type WalletCatalogEntry = SupportedWallet;

export type WalletConfiguration = {
  name?: string;
  logoUrl?: string;
  supportedWallets?: SupportedWallet[];
  defaultWallet?: string;
};

export type ClientConfiguration = {
  wallet?: WalletConfiguration;
  [feature: string]: unknown;
};

export type Settings = {
  tenantId: string;
  displayName: string | null;
  thumbnail: string | null;
  translations: string[];
  defaultLanguage: string;
  issuerIds: number[];
  trustRegistries: string[];
  clientConfiguration: ClientConfiguration | null;
};

export async function getSettingsForOrganisation(tenantId: string) {
  const res = await fetchSettings(tenantId);
  if (!res.ok) {
    return {
      tenantId: tenantId,
      displayName: null,
      thumbnail: "",
      translations: [DEFAULT_LOCALE],
      defaultLanguage: DEFAULT_LOCALE,
      issuerIds: [],
      trustRegistries: [],
      clientConfiguration: null,
    } as Settings;
  }
  return (await res.json()) as Settings;
}

export async function getRequiredSettingsForOrganisation(tenantId: string) {
  const res = await fetchSettings(tenantId);
  return parseSettingsResponse<Settings>(
    res,
    "Could not fetch settings for organisation",
  );
}

async function fetchSettings(tenantId: string) {
  return fetchWithRedirect(
    new URL(
      `management/v1/tenants/${tenantId}`,
      runtimeConfig.heidiApiBaseUrl,
    ),
  );
}

export async function getWalletCatalog() {
  const res = await fetchWithRedirect(
    new URL("management/v1/tenants/wallet-catalog", runtimeConfig.heidiApiBaseUrl),
  );
  if (!res.ok) {
    throw new Error("Could not fetch the wallet catalog");
  }
  return (await res.json()) as WalletCatalogEntry[];
}

export async function updateSettings({
  tenantId,
  displayName,
  thumbnail = "",
  translations,
  defaultLanguage,
  issuerIds,
  clientConfiguration,
  clearClientConfiguration,
}: {
  translations?: string[];
  defaultLanguage?: string;
  displayName?: string;
  thumbnail: string | null;
  tenantId: string;
  issuerIds?: number[];
  clientConfiguration?: ClientConfiguration;
  clearClientConfiguration?: boolean;
}) {
  const res = await fetchWithRedirect(
    new URL(
      `management/v1/tenants/${tenantId}`,
      runtimeConfig.heidiApiBaseUrl,
    ),
    {
      body: JSON.stringify({
        ...(displayName && { displayName }),
        ...(translations && { translations }),
        ...(defaultLanguage && { defaultLanguage }),
        ...(issuerIds && { issuerIds }),
        ...(thumbnail && { thumbnail }),
        ...(clientConfiguration && { clientConfiguration }),
        ...(clearClientConfiguration && { clearClientConfiguration: true }),
      }),
      method: "POST",
      headers: { "Content-Type": "application/json" },
    },
  );
  if (!res.ok) {
    throw new Error("Could not fetch settings for organisation");
  }
}

export async function deleteSettingsForOrganisation(tenantId: string) {
  const res = await fetchWithRedirect(
    new URL(
      `management/v1/tenants/${tenantId}`,
      runtimeConfig.heidiApiBaseUrl,
    ),
    {
      method: "DELETE",
    },
  );
  if (!res.ok) {
    throw new Error("Could not delete settings for organisation");
  }
}

export async function addOrganisationToTrustRegistry(tenantId: string) {
  const res = await fetchWithRedirect(
    new URL(
      `management/v1/tenants/${tenantId}/trust-registry/de`,
      runtimeConfig.heidiApiBaseUrl,
    ),
    { method: "POST" },
  );
  if (!res.ok) {
    throw new Error("Could not add organisation to trust registry");
  }
}

export async function generateVerifierCertificateForOrganisationInTrustRegistry(
  tenantId: string,
) {
  const res = await fetchWithRedirect(
    new URL(
      `management/v1/tenants/${tenantId}/trust-registry/de/generateVerifierCertificate`,
      runtimeConfig.heidiApiBaseUrl,
    ),
    { method: "POST" },
  );
  if (!res.ok) {
    throw new Error("Could not generate verifier certificate");
  }
}
