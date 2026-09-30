// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import assert from "node:assert/strict";
import test from "node:test";
import {
  certificateChainFromText,
  certificatesFromText,
} from "./certificate-input.ts";

test("keeps a line-wrapped base64 leaf certificate together", () => {
  assert.deepEqual(certificatesFromText("YWJj\nZGVm\nZ2hp"), ["YWJjZGVmZ2hp"]);
});

test("accepts one PEM leaf without a root", () => {
  const leaf = [
    "-----BEGIN CERTIFICATE-----",
    "YWJj",
    "-----END CERTIFICATE-----",
  ].join("\n");

  assert.deepEqual(certificatesFromText(leaf), [leaf]);
  assert.deepEqual(certificateChainFromText(leaf, ""), [leaf]);
});
