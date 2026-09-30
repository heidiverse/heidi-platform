// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { deepEqual, equal } from "node:assert/strict";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import {
  applyBrandingToManifest,
  readExtensionBranding,
  type ExtensionBranding,
} from "../vite-plugins/extension-branding.ts";

const branding: ExtensionBranding = {
  appName: "Example",
  icon: "/assets/example-icon.svg",
  themeColor: "#336699",
};

test("reads optional branding from the extension package manifest", () => {
  equal(readExtensionBranding(undefined), undefined);

  const extensionRoot = mkdtempSync(join(tmpdir(), "heidi-web-branding-"));
  try {
    writeFileSync(
      join(extensionRoot, "package.json"),
      JSON.stringify({ heidi: { web: { branding } } }),
    );
    deepEqual(readExtensionBranding(extensionRoot), branding);
  } finally {
    rmSync(extensionRoot, { recursive: true, force: true });
  }
});

test("applies extension name and theme color to the web manifest", () => {
  const result = applyBrandingToManifest(
    JSON.stringify({
      short_name: "Heidi",
      name: "Heidi Web Cockpit",
      theme_color: "#F4F8F9",
    }),
    branding,
  );

  deepEqual(JSON.parse(result), {
    short_name: "Example",
    name: "Example",
    theme_color: "#336699",
  });
});
