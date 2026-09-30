// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { test } from "node:test";
import { deepEqual } from "node:assert/strict";
import { keyOptions } from "../src/lib/api/keys/key-options.ts";
import type { PlatformKey } from "../src/lib/api/keys/api.ts";

test("slots exclude inactive versions and require the role's cryptographic usage", () => {
  const key = (id: string, status: string, usages: string[]): PlatformKey => ({
    id, keyId: id, providerId: 1, activeVersionId: status === "PREVIOUS" ? undefined : id,
    rotationPolicy: { mode: "AUTOMATIC", intervalSeconds: 100, gracePeriodSeconds: 10 },
    versions: [{ id, version: 1, status, algorithm: "ES256", publicJwk: "{}", certificates: [], usages }],
  } as PlatformKey);
  const keys = [key("active", "ACTIVE", ["SIGN"]), key("retired", "PREVIOUS", ["SIGN"]),
    key("revoked", "REVOKED", ["SIGN"]), key("prepared", "PREPARED", ["SIGN"]),
    key("decrypt", "ACTIVE", ["KEY_AGREEMENT"])];

  deepEqual(keyOptions(keys, "CREDENTIAL_SIGNING").map((option) => option.id), ["active"]);
  deepEqual(keyOptions(keys, "DECRYPTION").map((option) => option.id), ["decrypt"]);
});

test("certificate choices bind an active signing key and match framework, profile and validity", async () => {
  const { certificateOptions } = await import("../src/lib/api/keys/key-options.ts");
  const now = Date.parse("2026-09-17T12:00:00Z");
  const certificate = { id: "valid", profile: "ACCESS", trustSystem: "EUDI", source: "IMPORTED",
    chain: [], notBefore: "2026-09-01T00:00:00Z", notAfter: "2027-09-01T00:00:00Z" };
  const key = { id: "key", keyId: "identity", activeVersionId: "active", versions: [
    { id: "active", status: "ACTIVE", usages: ["SIGN"], certificates: [certificate,
      { ...certificate, id: "wrong-profile", profile: "CREDENTIAL_SIGNING" },
      { ...certificate, id: "wrong-framework", trustSystem: "Switzerland" },
      { ...certificate, id: "expired", notAfter: "2026-09-01T00:00:00Z" },
      { ...certificate, id: "retired", retiredAt: "2026-09-16T00:00:00Z" },
      { ...certificate, id: "future", notBefore: "2027-09-01T00:00:00Z" }] },
    { id: "prepared", status: "PREPARED", usages: ["SIGN"], certificates: [certificate] },
  ] } as PlatformKey;
  deepEqual(certificateOptions([key], "ACCESS", "EUDI", now).map(({ id, keyId }) => ({ id, keyId })),
    [{ id: "valid", keyId: "key" }]);
  deepEqual(certificateOptions([{ ...key, activeVersionId: undefined }], "ACCESS", "EUDI", now), []);
});
