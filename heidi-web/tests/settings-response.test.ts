// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { rejects, strictEqual } from "node:assert/strict";
import { test } from "node:test";
import { parseSettingsResponse } from "../src/lib/api/settings/response.ts";

test("does not turn a failed settings read into fallback settings", async () => {
  await rejects(
    parseSettingsResponse(
      new Response("unavailable", { status: 503 }),
      "Could not fetch settings for organisation",
    ),
    (error: unknown) => {
      strictEqual(
        (error as Error).message,
        "Could not fetch settings for organisation",
      );
      return true;
    },
  );
});
