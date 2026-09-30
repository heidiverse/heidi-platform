import { deepEqual, equal } from "node:assert/strict";
import { test } from "node:test";
import type { IdentityKeySlot } from "../src/lib/api/identity-key-slots/api.ts";
import type { PlatformKey } from "../src/lib/api/keys/api.ts";
import {
  requiredSigningClients,
  signingProviderReadiness,
} from "../src/routes/_authenticated/organisation/-components/signing-provider-readiness.ts";

function slot(
  id: string,
  type: IdentityKeySlot["type"],
  providerId: number | null,
  keyId = `${id}-key`,
): IdentityKeySlot {
  return {
    id,
    type,
    providerId,
    keyId,
    trustSystem: "Default",
    order: 0,
  };
}

function key(keyId: string, providerId: number): PlatformKey {
  return {
    id: `${keyId}-id`,
    keyId,
    providerId,
    rotationPolicy: { mode: "MANUAL", gracePeriodSeconds: 0 },
    versions: [],
  };
}

test("requires clients from the provider that owns each slot key", () => {
  const requirements = requiredSigningClients(
    [
      slot("issuer", "CREDENTIAL_SIGNING", 1),
      slot("verifier", "PRESENTATION_SIGNING", 2),
    ],
    [],
  );

  deepEqual(requirements, new Map([[1, ["issuer"]], [2, ["verifier"]]]));
  equal(
    signingProviderReadiness(requirements, [
      { providerId: 1, clients: [{ name: "issuer", known: true }, { name: "verifier", known: false }] },
      { providerId: 2, clients: [{ name: "issuer", known: false }, { name: "verifier", known: true }] },
    ]),
    true,
  );
});

test("resolves a slot provider from its assigned key", () => {
  const requirements = requiredSigningClients(
    [slot("issuer", "IDENTITY_STATEMENT", null, "issuer-key")],
    [key("issuer-key", 7)],
  );

  deepEqual(requirements, new Map([[7, ["issuer"]]]));
});
// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
