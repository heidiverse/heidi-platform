import type { IdentityKeySlot } from "@/lib/api/identity-key-slots/api";
import type { PlatformKey } from "@/lib/api/keys/api";

export type SigningClient = "issuer" | "verifier";

export type ProviderClients = {
  providerId: number;
  clients?: readonly { name: string; known: boolean }[] | null;
};

function clientFor(slot: IdentityKeySlot): SigningClient {
  if (slot.consumer?.toLowerCase() === "verifier") return "verifier";
  return slot.type === "PRESENTATION_SIGNING" ? "verifier" : "issuer";
}

function providerFor(slot: IdentityKeySlot, keys: readonly PlatformKey[]) {
  if (slot.providerId !== null && slot.providerId !== undefined) {
    return slot.providerId;
  }
  return keys.find(
    (key) => key.keyId === slot.keyId || key.id === slot.keyId,
  )?.providerId;
}

export function requiredSigningClients(
  slots: readonly IdentityKeySlot[],
  keys: readonly PlatformKey[],
) {
  const requirements = new Map<number, SigningClient[]>();
  for (const slot of slots) {
    if ((slot.trustSystem ?? "Default") !== "Default") continue;

    const providerId = providerFor(slot, keys);
    if (providerId === undefined) continue;

    const clients = requirements.get(providerId) ?? [];
    const client = clientFor(slot);
    if (!clients.includes(client)) clients.push(client);
    requirements.set(providerId, clients);
  }
  return requirements;
}

export function signingProviderReadiness(
  requirements: ReadonlyMap<number, readonly SigningClient[]>,
  providers: readonly ProviderClients[],
) {
  if (requirements.size === 0) return undefined;

  for (const [providerId, clients] of requirements) {
    const provider = providers.find((candidate) => candidate.providerId === providerId);
    if (!provider?.clients?.length) return undefined;
    if (!clients.every((client) =>
      provider.clients?.some((candidate) => candidate.name === client && candidate.known),
    )) {
      return false;
    }
  }
  return true;
}
// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
