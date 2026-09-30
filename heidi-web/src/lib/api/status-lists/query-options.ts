// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { queryOptions } from "@tanstack/react-query";
import { getStatusList, getStatusLists } from "./api";

export function statusListOptions(tenantId?: string) {
  return queryOptions({
    queryKey: ["status-lists", tenantId],
    queryFn: () => getStatusLists(tenantId),
  });
}

export function statusListDetailOptions(id: string) {
  return queryOptions({
    queryKey: ["status-list", id],
    queryFn: () => getStatusList(id),
  });
}
