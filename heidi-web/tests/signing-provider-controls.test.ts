// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { deepEqual } from "node:assert/strict";
import { test } from "node:test";
import {
  clientConnectionPath,
  clientRegistrationPath,
} from "../src/lib/api/signing-providers/client-controls.ts";

test("builds tenant client registration paths", () => {
  deepEqual(
    clientRegistrationPath(7, "issuer", "tenant"),
    "/7/register-client/issuer",
  );
});

test("builds global client registration paths", () => {
  deepEqual(
    clientRegistrationPath(7, "verifier", "global"),
    "/global/7/register-client/verifier",
  );
});

test("builds persisted provider connection paths", () => {
  deepEqual(clientConnectionPath(7, "tenant"), "/7/connection");
  deepEqual(clientConnectionPath(7, "global"), "/global/7/connection");
});
