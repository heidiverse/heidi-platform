// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { getOrganisationFeatures } from "@/lib/api/organisation-features/api";

export function organisationFeaturesOptions(tenantId: string) {
  return {
    queryKey: ["organisation-features", tenantId],
    queryFn: () => getOrganisationFeatures(tenantId),
  };
}
