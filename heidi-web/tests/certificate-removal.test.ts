// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { strictEqual } from "node:assert";
import { test } from "node:test";
import { certificateRemovalCopy } from "../src/routes/_authenticated/keys/-certificate-removal.ts";

test("unused imported certificates use neutral deletion copy", () => {
  const copy = certificateRemovalCopy({ source: "IMPORTED" });

  strictEqual(copy.action, "Delete");
  strictEqual(copy.title, "Delete unused imported certificate?");
});

test("development and used imported certificates describe their actual operation", () => {
  strictEqual(certificateRemovalCopy({ source: "DEVELOPMENT" }).title,
    "Delete development certificate?");
  strictEqual(certificateRemovalCopy({ source: "IMPORTED", firstUsedAt: "2026-09-21T00:00:00Z" }).action,
    "Archive");
});

test("previously used development certificates are archived", () => {
  const copy = certificateRemovalCopy({ source: "DEVELOPMENT", firstUsedAt: "2026-09-21T00:00:00Z" });

  strictEqual(copy.action, "Archive");
  strictEqual(copy.title, "Archive previously used development certificate?");
});
