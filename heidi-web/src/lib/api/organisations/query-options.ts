// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { queryOptions } from "@tanstack/react-query";
import {
  getOrganisationByTenantId,
  getOrganisations,
} from "@/lib/api/organisations/api";

export function organisationListOptions() {
  return queryOptions({
    queryKey: ["organisations"],
    queryFn: getOrganisations,
  });
}

export function organisationOptions(tenantId: string) {
  return queryOptions({
    queryKey: ["organisation", tenantId],
    queryFn: () => getOrganisationByTenantId(tenantId),
  });
}
