// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(
  new URL("./create-or-update-dialog.tsx", import.meta.url),
  "utf8",
);

test("proof schema edit dialog can scroll its content", () => {
  assert.match(
    source,
    /<DialogContent className="(?=[^"]*max-h-\[90vh\])(?=[^"]*overflow-y-auto)[^"]*"/,
  );
});
