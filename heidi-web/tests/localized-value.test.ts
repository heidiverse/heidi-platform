// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { equal } from "node:assert/strict";
import { test } from "node:test";
import { getLocalizedValue, isLanguageTag } from "../src/lib/utils/localized.ts";

test("uses the tenant fallback language before other values", () => {
  equal(
    getLocalizedValue({ en: "English", de: "Deutsch" }, "de"),
    "Deutsch",
  );
});

test("prefers an exact regional fallback before its base language", () => {
  equal(
    getLocalizedValue({ de: "Deutsch", "de-CH": "Schwiizerdütsch" }, "de-CH"),
    "Schwiizerdütsch",
  );
});

test("falls back from a regional language to its base language", () => {
  equal(getLocalizedValue({ de: "Deutsch" }, "de-CH"), "Deutsch");
});

test("uses any non-empty value when the fallback is missing", () => {
  equal(getLocalizedValue({ fr: "Français", de: "" }, "en"), "Français");
});

test("accepts valid non-canonical language tags", () => {
  equal(isLanguageTag("en-us"), true);
});
