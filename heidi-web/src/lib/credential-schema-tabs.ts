// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { DetailPageTabValues } from "@/types/common";

export function tabNavigation(tab: DetailPageTabValues) {
  // Let the route blocker protect edits before switching tabs.
  return {
    search: { tab },
    replace: true,
  };
}
