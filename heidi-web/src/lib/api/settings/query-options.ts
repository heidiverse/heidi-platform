// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  getSettingsForOrganisation,
  getWalletCatalog,
} from "@/lib/api/settings/api";

export function settingsForOrganisationOptions(tenantId: string) {
  return {
    queryKey: ["settings", tenantId],
    queryFn: () => getSettingsForOrganisation(tenantId),
    staleTime: Number.POSITIVE_INFINITY,
  };
}

export function walletCatalogOptions() {
  return {
    queryKey: ["wallet-catalog"],
    queryFn: getWalletCatalog,
    staleTime: 5 * 60 * 1000,
  };
}
