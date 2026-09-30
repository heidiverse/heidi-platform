// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { match } from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

test("presentation integration sample uses OID4VP 1.0", async () => {
  const source = await readFile(
    new URL("../src/components/common/integration-detail.tsx", import.meta.url),
    "utf8",
  );

  match(source, /oid4vpVersion:\s*"VERSION_ONE_DOT_ZERO"/);
});
