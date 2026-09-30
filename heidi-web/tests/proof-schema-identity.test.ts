// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { strictEqual } from "node:assert";
import { test } from "node:test";
import {
  CREDENTIAL_IDENTITY,
  payloadVerifierIdentityId,
  verifierIdentityId,
} from "../src/lib/api/proof-schemas/verifier-identity.ts";

test("new proof schemas use the selected credential schema identity", () => {
  strictEqual(verifierIdentityId(CREDENTIAL_IDENTITY, undefined, 42), 42);
});

test("explicit verifier identity overrides the credential identity", () => {
  strictEqual(verifierIdentityId("17", undefined, 42), 17);
});

test("existing proof schemas retain their effective identity", () => {
  strictEqual(verifierIdentityId(CREDENTIAL_IDENTITY, 23, undefined), 23);
});

test("proof schema updates send the effective verifier identity ID", () => {
  strictEqual(payloadVerifierIdentityId({ id: 23 }), 23);
  strictEqual(payloadVerifierIdentityId(null), null);
});
