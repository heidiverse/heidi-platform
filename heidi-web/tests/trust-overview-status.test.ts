// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { deepEqual, equal } from "node:assert/strict";
import { test } from "node:test";
import type { IdentityKeySlot } from "../src/lib/api/identity-key-slots/api.ts";
import {
  trustRoles,
  trustStatus,
} from "../src/routes/_authenticated/organisation/-components/trust-overview-status.ts";

function slot(
  id: string,
  type: IdentityKeySlot["type"],
  trustSystem: IdentityKeySlot["trustSystem"],
  configured = true,
): IdentityKeySlot {
  return {
    id,
    type,
    trustSystem,
    keyId: configured ? `${id}-key` : null,
    certificateId:
      configured && trustSystem === "EUDI" ? `${id}-certificate` : null,
    order: 0,
    configuration:
      configured && trustSystem === "Switzerland"
        ? { swissDid: "did:tdw:example" }
        : undefined,
  };
}

test("unused mechanisms remain neutral", () => {
  const input = { slots: [] };

  equal(trustStatus("Custom", input), "NOT_IN_USE");
  equal(trustStatus("EUDI", input), "NOT_IN_USE");
  equal(trustStatus("Switzerland", input), "NOT_IN_USE");
  equal(trustStatus("Federation", input), "NOT_IN_USE");
});
test("declared EUDI setup is incomplete until its access certificate exists", () => {
  const input = {
    declaredTrustSystems: ["EUDI"] as const,
    slots: [slot("issuer", "IDENTITY_STATEMENT", "EUDI", false)],
  };

  equal(trustStatus("EUDI", input), "SETUP_INCOMPLETE");
});

test("EUDI and Swiss identity material produce configured states", () => {
  equal(
    trustStatus("EUDI", {
      slots: [slot("eudi", "IDENTITY_STATEMENT", "EUDI")],
    }),
    "CONFIGURED",
  );
  equal(
    trustStatus("Switzerland", {
      slots: [slot("swiss", "IDENTITY_STATEMENT", "Switzerland")],
    }),
    "CONFIGURED",
  );
});

test("custom signing is configured when its assigned keys exist", () => {
  equal(
    trustStatus("Custom", {
      slots: [slot("custom", "CREDENTIAL_SIGNING", "Default")],
    }),
    "CONFIGURED",
  );
});

test("custom signing provider readiness is required when reported", () => {
  const slots = [
    slot("issuer", "IDENTITY_STATEMENT", "Default"),
    slot("credential", "CREDENTIAL_SIGNING", "Default"),
    slot("verifier", "PRESENTATION_SIGNING", "Default"),
  ];

  equal(trustStatus("Custom", { slots, signingProviderReady: false }), "SETUP_INCOMPLETE");
  equal(trustStatus("Custom", { slots, signingProviderReady: true }), "CONFIGURED");
});

test("trust roles report only roles selected for the identity", () => {
  deepEqual(
    trustRoles("EUDI", {
      slots: [
        slot("issuer", "IDENTITY_STATEMENT", "EUDI"),
        slot("credential", "CREDENTIAL_SIGNING", "EUDI", false),
      ],
    }),
    [
      { label: "Issuer identity", configured: true },
      { label: "Credential signing", configured: false },
    ],
  );
});

test("partially configured OIDF is incomplete", () => {
  equal(
    trustStatus("OIDF", {
      slots: [],
      federation: {
        signingKeyId: null,
        authorityHints: ["https://superior.example"],
        authority: false,
        subordinateIds: [],
        credentialSchemeIds: null,
      },
    }),
    "SETUP_INCOMPLETE",
  );
});

test("OIDF exposes issuer, credential, and verifier key roles", () => {
  deepEqual(
    trustRoles("OIDF", {
      slots: [
        slot("issuer", "IDENTITY_STATEMENT", "OIDF"),
        slot("credential", "CREDENTIAL_SIGNING", "OIDF"),
        slot("verifier", "PRESENTATION_SIGNING", "OIDF"),
      ],
      federation: {
        signingKeyId: "issuer-key",
        authorityHints: [],
        authority: false,
        subordinateIds: [],
        credentialSchemeIds: null,
      },
    }),
    [
      { label: "Federation entity", configured: true },
      { label: "Issuer identity", configured: true },
      { label: "Credential signing", configured: true },
      { label: "Verifier identity", configured: true },
    ],
  );
});

test("OIDF slots mark the mechanism in use without federation settings", () => {
  equal(
    trustStatus("OIDF", {
      slots: [slot("issuer", "IDENTITY_STATEMENT", "OIDF")],
    }),
    "CONFIGURED",
  );
});

test("known runtime problems take precedence over readiness", () => {
  equal(
    trustStatus("EUDI", {
      slots: [slot("eudi", "IDENTITY_STATEMENT", "EUDI")],
      issues: { EUDI: true },
    }),
    "NEEDS_ATTENTION",
  );
});
