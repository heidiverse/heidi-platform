// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

const LEGACY_LOCAL_TENANT_ID = "local";

/** Moves the persisted local-development selection after the Acme rename. */
export function migrateTenantSelection(
  selectedTenant: string | null,
  userTenant: string,
) {
  if (selectedTenant === LEGACY_LOCAL_TENANT_ID) {
    return userTenant;
  }

  return selectedTenant;
}
