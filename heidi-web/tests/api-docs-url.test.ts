// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { equal } from "node:assert/strict";
import { test } from "node:test";
import { apiDocsUrl } from "../src/lib/api-docs.ts";

test("uses the same-origin proxy in development", () => {
  equal(apiDocsUrl("http://apair.ubinfra.lan:8090/", "development"), "/api-docs/integrator");
});

test("uses the configured API origin in production", () => {
  equal(
    apiDocsUrl("https://api.example/", "production"),
    "https://api.example/api-docs/integrator",
  );
});
