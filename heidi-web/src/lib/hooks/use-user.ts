// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useSuspenseQuery } from "@tanstack/react-query";
import { userTokenDataOptions } from "@/lib/auth/query-options";

export function useUser() {
  const { data } = useSuspenseQuery(userTokenDataOptions());
  return data;
}
