// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { equal } from "node:assert/strict";
import { test } from "node:test";
import { migrateTenantSelection } from "../src/lib/auth/tenant-selection.ts";

test("moves the former local selection to the signed-in tenant", () => {
  equal(migrateTenantSelection("local", "acme"), "acme");
});

test("preserves another selected tenant", () => {
  equal(migrateTenantSelection("customer", "acme"), "customer");
});
