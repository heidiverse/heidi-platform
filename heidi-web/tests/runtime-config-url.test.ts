// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { equal } from "node:assert/strict";
import { test } from "node:test";
import { resolveApiBaseUrl } from "../src/lib/runtime-config-url.ts";

test("uses the browser origin for local development", () => {
  equal(
    resolveApiBaseUrl(
      "dev-local",
      "https://apair.ubinfra.lan:8443/",
      "http://apair.ubinfra.lan",
    ),
    "http://apair.ubinfra.lan/",
  );
});

test("uses the configured API URL outside local development", () => {
  equal(
    resolveApiBaseUrl(
      "production",
      "https://api.example/",
      "https://cockpit.example",
    ),
    "https://api.example/",
  );
});
