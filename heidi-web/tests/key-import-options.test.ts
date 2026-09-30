// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { deepEqual } from "node:assert/strict";
import { test } from "node:test";
import { privateKeyFormats } from "../src/lib/api/keys/import.ts";

test("offers every supported private-key import format", () => {
  deepEqual(
    privateKeyFormats.map(({ value }) => value),
    ["PKCS12", "PEM", "JWK", "JWKS"],
  );
});
