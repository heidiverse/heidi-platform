// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { queryOptions } from "@tanstack/react-query";
import { getIssuerDefinition, getIssuerDefinitionOverview } from "./api";

export function issuerDefinitionListOptions({
  isPublic = false,
  tenantId,
}: {
  isPublic?: boolean;
  tenantId?: string;
} = {}) {
  return queryOptions({
    queryKey: ["issuer-definitions", { isPublic, tenantId }],
    queryFn: () => getIssuerDefinitionOverview({ isPublic, tenantId }),
  });
}

export function issuerDefinitionOptions({ id }: { id: number }) {
  return queryOptions({
    queryKey: ["issuer-definition", id],
    queryFn: () => getIssuerDefinition({ id }),
  });
}
