// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import assert from "node:assert/strict";
import test from "node:test";
import { authorityHintsFromText, toggled } from "./federation-input.ts";

test("reads one authority hint per line", () => {
  assert.deepEqual(
    authorityHintsFromText(
      " https://anchor.example \n\nhttps://intermediate.example\r\nhttps://anchor.example",
    ),
    ["https://anchor.example", "https://intermediate.example"],
  );
});

test("toggles an entry without duplicating it", () => {
  assert.deepEqual(toggled([1, 2], 2, true), [1, 2]);
  assert.deepEqual(toggled([1, 2], 3, true), [1, 2, 3]);
  assert.deepEqual(toggled([1, 2], 1, false), [2]);
});
