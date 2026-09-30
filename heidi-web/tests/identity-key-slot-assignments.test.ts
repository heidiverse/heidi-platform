// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { deepEqual, equal } from "node:assert/strict";
import { test } from "node:test";
import {
  certificateSourceNote,
  expectedProfile,
  hasDevelopmentCertificate,
  assignedKeyIds,
  requiredMaterial,
  rowsFor,
  showTrustContext,
} from "../src/routes/_authenticated/organisation/-components/identity-key-slot-assignments.ts";
import type { IdentityKeySlot } from "../src/lib/api/identity-key-slots/api.ts";
import type { PlatformKey } from "../src/lib/api/keys/api.ts";

function slot(
  id: string,
  type: IdentityKeySlot["type"],
  trustSystem: IdentityKeySlot["trustSystem"] = "Default",
): IdentityKeySlot {
  return { id, type, trustSystem, order: 0 };
}

test("assignment rows include only configured trust contexts", () => {
  const rows = rowsFor(
    [slot("issuer-eudi", "IDENTITY_STATEMENT", "EUDI")],
    ["IDENTITY_STATEMENT", "PRESENTATION_SIGNING"],
    ["Switzerland", "EUDI"],
  );

  deepEqual(
    rows.map(({ type, trustSystem, slot: current }) => ({
      type,
      trustSystem,
      id: current?.id,
    })),
    [{ type: "IDENTITY_STATEMENT", trustSystem: "EUDI", id: "issuer-eudi" }],
  );
});

test("assignment rows stay within an explicit trust context", () => {
  const rows = rowsFor(
    [
      slot("issuer-default", "IDENTITY_STATEMENT"),
      slot("issuer-eudi", "IDENTITY_STATEMENT", "EUDI"),
      slot("verifier-swiss", "PRESENTATION_SIGNING", "Switzerland"),
    ],
    ["IDENTITY_STATEMENT", "PRESENTATION_SIGNING"],
    undefined,
    "EUDI",
  );

  deepEqual(
    rows.map(({ type, trustSystem, slot: current }) => ({
      type,
      trustSystem,
      id: current?.id,
    })),
    [{ type: "IDENTITY_STATEMENT", trustSystem: "EUDI", id: "issuer-eudi" }],
  );
});

test("fixed trust-context tables do not repeat the context on every row", () => {
  equal(showTrustContext("EUDI", "EUDI"), false);
  equal(showTrustContext("EUDI"), true);
  equal(showTrustContext("Default"), false);
});

test("advanced capabilities remain identifiable as separate assignments", () => {
  const rows = rowsFor(
    [
      slot("operation", "OPERATION"),
      slot("federation", "FEDERATION"),
      slot("decrypt", "DECRYPTION"),
      slot("status", "STATUS_LIST"),
    ],
    ["OPERATION", "FEDERATION", "DECRYPTION", "STATUS_LIST"],
  );

  deepEqual(
    rows.map(({ type }) => type),
    ["OPERATION", "FEDERATION", "DECRYPTION", "STATUS_LIST"],
  );
});

test("request decryption assignments can add another scoped key", () => {
  const rows = rowsFor(
    [slot("decrypt-v1", "DECRYPTION")],
    ["DECRYPTION"],
  );

  deepEqual(
    rows.map(({ id, slot: current }) => ({ id, slotId: current?.id })),
    [{ id: "decrypt-v1", slotId: "decrypt-v1" }],
  );
});

test("empty assignment types do not create table rows", () => {
  deepEqual(rowsFor([], ["DECRYPTION", "IDENTITY_STATEMENT"]), []);
});

test("a key cannot be selected twice for the same trust framework", () => {
  const key = "request-key";
  const slots = [
    { ...slot("swiss", "DECRYPTION", "Switzerland"), keyId: key },
    { ...slot("eudi", "DECRYPTION", "EUDI"), keyId: key },
  ];

  deepEqual(
    [...assignedKeyIds(slots, "DECRYPTION", "Switzerland")],
    [key],
  );
  deepEqual(
    [...assignedKeyIds(slots, "DECRYPTION", "Custom")],
    [],
  );
  deepEqual(
    [...assignedKeyIds(
      [{ ...slot("global", "DECRYPTION"), keyId: key }],
      "DECRYPTION",
      "Switzerland",
    )],
    [key],
  );
});

test("legacy Default signing assignments remain visible in the Custom context", () => {
  const rows = rowsFor(
    [slot("custom-legacy", "CREDENTIAL_SIGNING")],
    ["CREDENTIAL_SIGNING"],
    undefined,
    "Custom",
  );

  deepEqual(rows.map(({ trustSystem, slot: current }) => ({ trustSystem, id: current?.id })), [
    { trustSystem: "Custom", id: "custom-legacy" },
  ]);
});

test("EUDI identity and verifier roles require access certificates", () => {
  equal(expectedProfile("IDENTITY_STATEMENT", "EUDI"), "ACCESS");
  equal(expectedProfile("PRESENTATION_SIGNING", "EUDI"), "ACCESS");
  equal(expectedProfile("CREDENTIAL_SIGNING", "EUDI"), "CREDENTIAL_SIGNING");
  equal(
    requiredMaterial("IDENTITY_STATEMENT", "EUDI"),
    "EUDI ACCESS certificate",
  );
  equal(
    requiredMaterial("PRESENTATION_SIGNING", "EUDI"),
    "EUDI ACCESS certificate",
  );
  equal(
    requiredMaterial("CREDENTIAL_SIGNING", "EUDI"),
    "EUDI credential signing certificate",
  );
});

test("development certificates are identified from certificate metadata", () => {
  const assignment = {
    ...slot("verifier", "PRESENTATION_SIGNING"),
    keyId: "key-id",
    certificateId: "certificate-id",
  };
  const keys: PlatformKey[] = [{
    id: "key-id",
    keyId: "local-key",
    providerId: 1,
    activeVersionId: "version-id",
    rotationPolicy: { mode: "MANUAL", gracePeriodSeconds: 0 },
    versions: [{
      id: "version-id",
      version: 1,
      algorithm: "ES256",
      publicJwk: "{}",
      status: "ACTIVE",
      usages: ["SIGN"],
      certificates: [{
        id: "certificate-id",
        profile: "CREDENTIAL_SIGNING",
        source: "DEVELOPMENT",
        trustSystem: null,
        chain: [],
      }],
    }],
  }];

  equal(
    certificateSourceNote(assignment, keys),
    "Development certificate · generated automatically",
  );
  equal(hasDevelopmentCertificate([assignment], keys), true);
});

test("imported certificates do not imply local generation", () => {
  const assignment = {
    ...slot("issuer", "IDENTITY_STATEMENT"),
    keyId: "key-id",
    certificateId: "certificate-id",
  };
  const keys = [{
    id: "key-id",
    activeVersionId: "version-id",
    versions: [{
      id: "version-id",
      certificates: [{ id: "certificate-id", source: "IMPORTED" }],
    }],
  }] as PlatformKey[];

  equal(certificateSourceNote(assignment, keys), undefined);
  equal(hasDevelopmentCertificate([assignment], keys), false);
});
