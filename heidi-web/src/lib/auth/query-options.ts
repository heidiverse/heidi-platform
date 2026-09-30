// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { queryOptions } from "@tanstack/react-query";
import { getCurrentUser } from "@/lib/auth/identity";

export function userTokenDataOptions() {
  return queryOptions({
    queryFn: getCurrentUser,
    queryKey: ["user"],
    staleTime: Number.POSITIVE_INFINITY,
  });
}
