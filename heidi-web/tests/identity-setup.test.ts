// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { deepEqual, equal, ok } from "node:assert/strict";
import { test } from "node:test";
import {
  eudiRoleSlot,
  eudiRoles,
  eudiSetupReady,
} from "../src/routes/_authenticated/identities/-components/identity-setup.ts";

test("EUDI setup describes issuer and verifier roles as ACCESS certificate roles", () => {
  deepEqual(eudiRoles.map((role) => role.id), ["issuer", "verifier"]);
  ok(eudiRoles.every((role) => role.technical.endsWith(".technical")));
  equal(eudiRoleSlot("issuer"), "IDENTITY_STATEMENT");
  equal(eudiRoleSlot("verifier"), "PRESENTATION_SIGNING");
});

test("EUDI setup requires a certificate for every selected role", () => {
  const empty = { issuer: "", verifier: "" } as const;
  equal(eudiSetupReady([], empty), false);
  equal(eudiSetupReady(["issuer"], empty), false);
  equal(eudiSetupReady(["issuer"], { issuer: "access-1", verifier: "" }), true);
  equal(eudiSetupReady(["issuer", "verifier"], { issuer: "access-1", verifier: "access-1" }), true);
  equal(eudiSetupReady(["issuer", "verifier"], { issuer: "access-1", verifier: "" }), false);
});
