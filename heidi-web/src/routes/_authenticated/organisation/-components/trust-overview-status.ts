// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { IdentityKeySlot } from "@/lib/api/identity-key-slots/api";
import type { IdentityFederation } from "@/lib/api/issuer-definitions/api";
import type { ConfiguredTrustSystem } from "@/types/trust-system";

export const trustMechanisms = [
  "EUDI",
  "Switzerland",
  "OIDF",
  "Custom",
] as const;

export type TrustMechanism = (typeof trustMechanisms)[number];
export type TrustStatus =
  | "NOT_IN_USE"
  | "SETUP_INCOMPLETE"
  | "CONFIGURED"
  | "NEEDS_ATTENTION";

export type TrustRole = {
  label: string;
  configured: boolean;
};

export type TrustOverviewInput = {
  declaredTrustSystems?: readonly ConfiguredTrustSystem[];
  slots: readonly IdentityKeySlot[];
  federation?: IdentityFederation;
  signingProviderReady?: boolean;
  issues?: Partial<Record<TrustMechanism, boolean>>;
};

const trustSlotTypes = new Set<IdentityKeySlot["type"]>([
  "IDENTITY_STATEMENT",
  "CREDENTIAL_SIGNING",
  "PRESENTATION_SIGNING",
]);

// Keep the old internal mechanism name readable for callers while exposing OIDF as the
// first-class label in the overview and framework selectors.
function isOidf(mechanism: TrustMechanism) {
  return mechanism === "OIDF" || String(mechanism) === "Federation";
}

function slotsFor(mechanism: TrustMechanism, slots: readonly IdentityKeySlot[]) {
  const trustSystem = mechanism;
  return slots.filter(
    (slot) =>
      trustSlotTypes.has(slot.type) &&
      ((slot.trustSystem ?? "Default") === trustSystem ||
        (mechanism === "Custom" && (slot.trustSystem ?? "Default") === "Default")),
  );
}

function federationInUse(settings?: IdentityFederation) {
  if (!settings) return false;

  return Boolean(
    settings.signingKeyId ||
      settings.authority ||
      settings.authorityHints.length ||
      settings.subordinateIds.length,
  );
}

function slotComplete(slot: IdentityKeySlot) {
  if (!slot.keyId) return false;

  if (slot.trustSystem !== "EUDI") return true;
  if (
    slot.type !== "IDENTITY_STATEMENT" &&
    slot.type !== "PRESENTATION_SIGNING" &&
    slot.type !== "CREDENTIAL_SIGNING"
  ) {
    return true;
  }

  return Boolean(slot.certificateId);
}

const roleLabels: Partial<Record<IdentityKeySlot["type"], string>> = {
  IDENTITY_STATEMENT: "Issuer identity",
  CREDENTIAL_SIGNING: "Credential signing",
  PRESENTATION_SIGNING: "Verifier identity",
};
const trustRoleTypes = [
  "IDENTITY_STATEMENT",
  "CREDENTIAL_SIGNING",
  "PRESENTATION_SIGNING",
] as const;

export function trustRoles(
  mechanism: TrustMechanism,
  input: TrustOverviewInput,
): TrustRole[] {
  const slots = slotsFor(mechanism, input.slots);
  const roles = trustRoleTypes.flatMap((type) => {
    const slot = slots.find((candidate) => candidate.type === type);
    if (!slot) return [];

    const label = roleLabels[slot.type];
    return label ? [{ label, configured: slotComplete(slot) }] : [];
  });
  if (isOidf(mechanism)) {
    if (!federationInUse(input.federation)) return roles;

    const federationRoles = [{ label: "Federation entity", configured: Boolean(input.federation?.signingKeyId) }];
    if (input.federation?.authority) {
      federationRoles.push({ label: "Trust anchor", configured: Boolean(input.federation.signingKeyId) });
    }
    return [...federationRoles, ...roles];
  }

  if (mechanism === "Custom" && input.signingProviderReady !== undefined) {
    roles.push({
      label: "Signing provider",
      configured: input.signingProviderReady,
    });
  }
  return roles;
}

function ecosystemReady(mechanism: TrustMechanism, slots: readonly IdentityKeySlot[]) {
  const statement = slots.find((slot) => slot.type === "IDENTITY_STATEMENT");
  if (!statement || !slots.every(slotComplete)) return false;

  if (mechanism === "EUDI") return slotComplete(statement);
  if (mechanism === "OIDF") return true;

  const configuration = statement.configuration as
    | { swissDid?: unknown }
    | undefined;
  return (
    slotComplete(statement) &&
    typeof configuration?.swissDid === "string" &&
    configuration.swissDid.length > 0
  );
}

export function trustStatus(
  mechanism: TrustMechanism,
  input: TrustOverviewInput,
): TrustStatus {
  const slots = slotsFor(mechanism, input.slots);
  const declared = input.declaredTrustSystems ?? [];
  const inUse =
    isOidf(mechanism)
      ? federationInUse(input.federation) ||
        slots.length > 0 ||
        declared.includes(mechanism)
      : slots.length > 0 ||
        (mechanism !== "Custom" && declared.includes(mechanism));

  if (!inUse) return "NOT_IN_USE";
  if (input.issues?.[mechanism]) return "NEEDS_ATTENTION";

  if (isOidf(mechanism)) {
    return ecosystemReady(mechanism, slots) ? "CONFIGURED" : "SETUP_INCOMPLETE";
  }
  if (mechanism === "Custom") {
    return slots.every(slotComplete) && input.signingProviderReady !== false
      ? "CONFIGURED"
      : "SETUP_INCOMPLETE";
  }

  if (mechanism !== "EUDI" && mechanism !== "Switzerland") {
    return "SETUP_INCOMPLETE";
  }

  return ecosystemReady(mechanism, slots) ? "CONFIGURED" : "SETUP_INCOMPLETE";
}
