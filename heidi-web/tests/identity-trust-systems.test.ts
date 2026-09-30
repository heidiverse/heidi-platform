// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { deepEqual } from "node:assert/strict";
import { test } from "node:test";
import {
  activeTrustSystems,
  availableTrustSystems,
} from "../src/routes/_authenticated/identities/-components/trust-systems.ts";

test("trust systems include slot-backed contexts without duplicates", () => {
  deepEqual(
    activeTrustSystems(
      ["EUDI"],
      [
        { type: "IDENTITY_STATEMENT", trustSystem: "Switzerland" },
        { type: "CREDENTIAL_SIGNING", trustSystem: "EUDI" },
        { type: "IDENTITY_STATEMENT", trustSystem: "Default" },
      ],
    ),
    ["Switzerland", "EUDI"],
  );
});

test("each supported trust system can be added once", () => {
  deepEqual(availableTrustSystems(["Switzerland"]), ["EUDI", "Custom", "OIDF"]);
  deepEqual(availableTrustSystems(["Switzerland", "EUDI"]), ["Custom", "OIDF"]);
});
