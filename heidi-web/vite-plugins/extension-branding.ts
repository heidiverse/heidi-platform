// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import type { Plugin } from "vite";

export type ExtensionBranding = {
  appName: string;
  icon?: string;
  themeColor?: string;
};

type JsonObject = Record<string, unknown>;

function isJsonObject(value: unknown): value is JsonObject {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** Reads optional web branding declared by the selected extension package. */
export function readExtensionBranding(
  extensionRoot: string | undefined,
): ExtensionBranding | undefined {
  if (!extensionRoot) return undefined;

  const packagePath = resolve(extensionRoot, "package.json");
  if (!existsSync(packagePath)) return undefined;

  const packageMetadata = JSON.parse(readFileSync(packagePath, "utf8")) as {
    heidi?: { web?: { branding?: unknown } };
  };
  const branding = packageMetadata.heidi?.web?.branding;
  if (branding === undefined) return undefined;
  if (!isJsonObject(branding)) {
    throw new Error(
      'Extension package field "heidi.web.branding" must be an object',
    );
  }

  const appName = branding.appName;
  if (typeof appName !== "string" || appName.trim() === "") {
    throw new Error(
      'Extension package field "heidi.web.branding.appName" must be a non-empty string',
    );
  }

  const icon = branding.icon;
  if (
    icon !== undefined &&
    (typeof icon !== "string" || !icon.startsWith("/") || icon.startsWith("//"))
  ) {
    throw new Error(
      'Extension package field "heidi.web.branding.icon" must be a root-relative URL',
    );
  }

  const themeColor = branding.themeColor;
  if (
    themeColor !== undefined &&
    (typeof themeColor !== "string" || !/^#[\da-f]{6}$/i.test(themeColor))
  ) {
    throw new Error(
      'Extension package field "heidi.web.branding.themeColor" must be a six-digit hex color',
    );
  }

  return {
    appName: appName.trim(),
    ...(icon !== undefined && { icon }),
    ...(themeColor !== undefined && { themeColor }),
  };
}

/** Updates the installable app's visible name and browser theme color. */
export function applyBrandingToManifest(
  source: string,
  branding: ExtensionBranding,
): string {
  const manifest: unknown = JSON.parse(source);
  if (!isJsonObject(manifest)) {
    throw new Error("Web app manifest must contain a JSON object");
  }

  manifest.name = branding.appName;
  manifest.short_name = branding.appName;
  if (branding.themeColor) manifest.theme_color = branding.themeColor;
  return `${JSON.stringify(manifest, null, 2)}\n`;
}

/** Applies optional extension branding consistently in dev and production builds. */
export function extensionBrandingPlugin(
  branding: ExtensionBranding | undefined,
): Plugin {
  if (!branding) return { name: "heidi-web-extension-branding" };

  let outputManifestPath: string | undefined;

  return {
    name: "heidi-web-extension-branding",
    configResolved(config) {
      outputManifestPath = resolve(
        config.root,
        config.build.outDir,
        "assets/manifest.json",
      );
    },
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        if ((request.url ?? "").split("?")[0] !== "/assets/manifest.json") {
          next();
          return;
        }

        const manifestPath = resolve(
          process.cwd(),
          "public/assets/manifest.json",
        );
        if (!existsSync(manifestPath)) {
          next();
          return;
        }

        response.statusCode = 200;
        response.setHeader("Content-Type", "application/manifest+json");
        response.end(
          applyBrandingToManifest(readFileSync(manifestPath, "utf8"), branding),
        );
      });
    },
    writeBundle() {
      if (!outputManifestPath || !existsSync(outputManifestPath)) return;
      const source = readFileSync(outputManifestPath, "utf8");
      writeFileSync(
        outputManifestPath,
        applyBrandingToManifest(source, branding),
      );
    },
  };
}
