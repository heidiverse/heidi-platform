// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { equal } from "node:assert/strict";
import { test } from "node:test";
import { readApiError } from "../src/lib/utils/api-error.ts";

test("reads a backend problem detail", async () => {
  equal(
    await readApiError(
      new Response(JSON.stringify({ detail: "Issuer client is not accepted" }), {
        status: 400,
      }),
      "Request failed",
    ),
    "Issuer client is not accepted",
  );
});

test("keeps plain backend error responses", async () => {
  equal(
    await readApiError(
      new Response("Credential signing slot is missing", { status: 400 }),
      "Request failed",
    ),
    "Credential signing slot is missing",
  );
});
