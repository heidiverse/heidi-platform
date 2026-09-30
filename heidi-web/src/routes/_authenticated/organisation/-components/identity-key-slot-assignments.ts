// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type {
  IdentityKeySlot,
  IdentityKeySlotType,
} from "../../../../lib/api/identity-key-slots/api";
import type { PlatformKey } from "../../../../lib/api/keys/api";
import type {
  ConfiguredTrustSystem,
  TrustSystem,
} from "../../../../types/trust-system";

export const advancedTypes = new Set<IdentityKeySlotType>([
  "DECRYPTION",
  "OPERATION",
  "FEDERATION",
  "STATUS_LIST",
]);

export type AssignmentRow = {
  id: string;
  type: IdentityKeySlotType;
  trustSystem: TrustSystem;
  slot?: IdentityKeySlot;
};

function assignedCertificate(slot: IdentityKeySlot, keys: PlatformKey[]) {
  if (!slot.certificateId || !slot.keyId) return undefined;

  return keys
    .find((key) => key.id === slot.keyId)
    ?.versions.flatMap((version) => version.certificates)
    .find((certificate) => certificate.id === slot.certificateId);
}

export function certificateSourceNote(
  slot: IdentityKeySlot,
  keys: PlatformKey[],
) {
  if (assignedCertificate(slot, keys)?.source !== "DEVELOPMENT") {
    return undefined;
  }

  return "Development certificate · generated automatically";
}

export function hasDevelopmentCertificate(
  slots: IdentityKeySlot[],
  keys: PlatformKey[],
) {
  return slots.some((slot) => certificateSourceNote(slot, keys));
}

export function showTrustContext(
  rowTrustSystem: TrustSystem,
  fixedTrustSystem?: TrustSystem,
) {
  return !fixedTrustSystem && rowTrustSystem !== "Default";
}

export function assignedKeyIds(
  slots: IdentityKeySlot[],
  type: IdentityKeySlotType,
  trustSystem: TrustSystem,
  excludeSlotId?: string,
) {
  return new Set(
    slots
      .filter(
        (slot) => {
          if (slot.id === excludeSlotId || slot.type !== type || !slot.keyId) {
            return false;
          }

          const assignedTrustSystem = slot.trustSystem ?? "Default";
          return type === "DECRYPTION"
            ? assignedTrustSystem === "Default" ||
                trustSystem === "Default" ||
                assignedTrustSystem === trustSystem
            : assignedTrustSystem === trustSystem;
        },
      )
      .map((slot) => slot.keyId as string),
  );
}

export function rowsFor(
  slots: IdentityKeySlot[],
  types: readonly IdentityKeySlotType[],
  _addTrustSystems?: readonly ConfiguredTrustSystem[],
  trustSystem?: TrustSystem,
) {
  const rows: AssignmentRow[] = [];

  for (const type of types) {
    const matching = slots.filter(
      (slot) =>
        slot.type === type &&
        (!trustSystem ||
          (slot.trustSystem ?? "Default") === trustSystem ||
          (trustSystem === "Custom" && (slot.trustSystem ?? "Default") === "Default")),
    );

    if (trustSystem) {
      rows.push(
        ...matching.map((slot) => ({
          id: slot.id,
          type,
          trustSystem,
          slot,
        })),
      );
      continue;
    }

    if (matching.length > 0) {
      rows.push(
        ...matching.map((slot) => ({
          id: slot.id,
          type,
          trustSystem: slot.trustSystem ?? "Default",
          slot,
        })),
      );
    }
  }

  return rows;
}

export function requiredMaterial(
  type: IdentityKeySlotType,
  trustSystem: TrustSystem,
) {
  if (
    trustSystem === "EUDI" &&
    (type === "IDENTITY_STATEMENT" || type === "PRESENTATION_SIGNING")
  ) {
    return "EUDI ACCESS certificate";
  }
  if (trustSystem === "EUDI" && type === "CREDENTIAL_SIGNING") {
    return "EUDI credential signing certificate";
  }
  if (type === "DECRYPTION") return "Key agreement or unwrap key";
  return "Signing key";
}

export function expectedProfile(
  type: IdentityKeySlotType,
  trustSystem: TrustSystem,
) {
  if (type === "STATUS_LIST") return "STATUS_LIST";
  if (
    trustSystem === "EUDI" &&
    (type === "IDENTITY_STATEMENT" || type === "PRESENTATION_SIGNING")
  ) {
    return "ACCESS";
  }
  return "CREDENTIAL_SIGNING";
}
