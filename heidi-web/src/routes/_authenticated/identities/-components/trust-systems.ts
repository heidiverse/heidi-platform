// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { IdentityKeySlot } from "../../../../lib/api/identity-key-slots/api.ts";
import {
  type ConfiguredTrustSystem,
  configuredTrustSystems,
} from "../../../../types/trust-system.ts";

export function activeTrustSystems(
  declared: ConfiguredTrustSystem[],
  slots: Pick<IdentityKeySlot, "type" | "trustSystem">[],
) {
  const active = new Set<ConfiguredTrustSystem>();
  for (const slot of slots) {
    if (
      (slot.type === "IDENTITY_STATEMENT" ||
        slot.type === "CREDENTIAL_SIGNING" ||
        slot.type === "PRESENTATION_SIGNING") &&
      configuredTrustSystems.includes(
        slot.trustSystem as ConfiguredTrustSystem,
      )
    ) {
      active.add(slot.trustSystem as ConfiguredTrustSystem);
    }
  }
  for (const trustSystem of declared) active.add(trustSystem);

  return configuredTrustSystems.filter((trustSystem) =>
    active.has(trustSystem),
  );
}

export function availableTrustSystems(active: ConfiguredTrustSystem[]) {
  return configuredTrustSystems.filter(
    (trustSystem) => !active.includes(trustSystem),
  );
}
