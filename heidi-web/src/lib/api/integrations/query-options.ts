// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { queryOptions } from "@tanstack/react-query";
import { getIntegration, getIntegrations } from "@/lib/api/integrations/api";

export function integrationsListOptions() {
  return queryOptions({
    queryKey: ["integrations"],
    queryFn: getIntegrations,
  });
}

export function integrationOptions({ id }: { id: string }) {
  return queryOptions({
    queryKey: ["integrations", id],
    queryFn: () => getIntegration(id),
  });
}
