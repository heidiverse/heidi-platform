// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { IdentityKeySlotType } from "@/lib/api/identity-key-slots/api";

export type EudiRole = "issuer" | "verifier";

export const eudiRoles: readonly {
  id: EudiRole;
  label: string;
  technical: string;
  slotType: IdentityKeySlotType;
}[] = [
  {
    id: "issuer",
    label: "identity.setup.roles.issuer.label",
    technical: "identity.setup.roles.issuer.technical",
    slotType: "IDENTITY_STATEMENT",
  },
  {
    id: "verifier",
    label: "identity.setup.roles.verifier.label",
    technical: "identity.setup.roles.verifier.technical",
    slotType: "PRESENTATION_SIGNING",
  },
];

export function eudiRoleSlot(role: EudiRole) {
  return role === "issuer" ? "IDENTITY_STATEMENT" : "PRESENTATION_SIGNING";
}

export function eudiSetupReady(
  roles: readonly EudiRole[],
  certificates: Readonly<Record<EudiRole, string>>,
) {
  return roles.length > 0 && roles.every((role) => Boolean(certificates[role]));
}
