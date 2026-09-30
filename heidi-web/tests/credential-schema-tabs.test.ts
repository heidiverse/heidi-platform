// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import assert from "node:assert/strict";
import test from "node:test";
import { tabNavigation } from "../src/lib/credential-schema-tabs.ts";

test("credential schema tab navigation respects the route blocker", () => {
  const options = tabNavigation("style");

  assert.deepEqual(options, {
    search: { tab: "style" },
    replace: true,
  });
  assert.equal("ignoreBlocker" in options, false);
});
