// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { tanstackRouter } from "@tanstack/router-plugin/vite";
import { physical, rootRoute } from "@tanstack/virtual-file-routes";
import { existsSync, readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, resolve } from "node:path";
import react, { reactCompilerPreset } from "@vitejs/plugin-react";
import { defineConfig, loadEnv, type Plugin } from "vite";
import tailwindcss from "@tailwindcss/vite";
import wasm from "vite-plugin-wasm";
import { devtools } from "@tanstack/devtools-vite";
import babel from "@rolldown/plugin-babel";
import {
  extensionBrandingPlugin,
  readExtensionBranding,
} from "./vite-plugins/extension-branding.ts";

const extensionVirtualModule = "virtual:heidi-web-extension";
const extensionVirtualModuleId = `\0${extensionVirtualModule}`;
const require = createRequire(import.meta.url);

function extensionRootFromWorkspace(): string | undefined {
  const marker = resolve(process.cwd(), ".heidi-extension-package");
  const configuredPackage =
    process.env.HEIDI_WEB_EXTENSION_PACKAGE ??
    (existsSync(marker) ? readFileSync(marker, "utf8").trim() : "");

  if (!configuredPackage) {
    return undefined;
  }

  try {
    return dirname(require.resolve(configuredPackage));
  } catch (error) {
    throw new Error(
      `Unable to resolve frontend extension package "${configuredPackage}"`,
      { cause: error },
    );
  }
}

function extensionEntryPlugin(extensionRoot: string | undefined): Plugin {
  return {
    name: "heidi-web-extension-entry",
    resolveId(id) {
      return id === extensionVirtualModule
        ? extensionVirtualModuleId
        : undefined;
    },
    load(id) {
      if (id !== extensionVirtualModuleId || !extensionRoot) {
        return id === extensionVirtualModuleId ? "" : undefined;
      }

      const entry = resolve(extensionRoot, "index.ts");
      return existsSync(entry) ? `import ${JSON.stringify(entry)};` : "";
    },
  };
}

// config.json carries a deployment's service URLs and overrides the VITE_ values
// baked into the bundle. It is served by nginx in a deployment, but in dev it
// would also be served straight out of public/, pinning every URL to whatever
// that checked-in file happens to say. Answering 404 here leaves the env file as
// the one place local URLs are configured, which is what the runtime config's
// own fallback is for.
const noRuntimeConfigInDev: Plugin = {
  name: "heidi-no-runtime-config-in-dev",
  apply: "serve",
  configureServer(server) {
    server.middlewares.use((req, res, next) => {
      if ((req.url ?? "").split("?")[0] === "/config.json") {
        res.statusCode = 404;
        res.end();
        return;
      }

      next();
    });
  },
};

// https://vitejs.dev/config/
export default defineConfig(({ command, mode }) => {
  // HEIDI_ is loaded alongside VITE_ so the dev server port can come from the
  // root env file as the API URLs it talks to. Only VITE_ reaches the client.
  const envDir =
    mode === "dev-local" ? resolve(process.cwd(), "..") : process.cwd();
  process.env = {
    ...process.env,
    ...loadEnv(mode, envDir, ["VITE_", "HEIDI_"]),
  };

  const extensionRoot = extensionRootFromWorkspace();
  const extensionBranding = readExtensionBranding(extensionRoot);
  const extensionRoutes = extensionRoot
    ? resolve(extensionRoot, "routes")
    : undefined;
  const publicBase = process.env.HEIDI_PUBLIC_HOST ?? "https://localhost";
  const httpsPort = process.env.HEIDI_HTTPS_PORT ?? "8443";
  const defaultApiBaseUrl = `${publicBase}:${httpsPort}/`;
  const apiBaseUrl =
    process.env.HEIDI_PLATFORM_PUBLIC_BASE_URL ??
    process.env.VITE_HEIDI_API_BASE_URL ??
    defaultApiBaseUrl;
  const publicHost = new URL(apiBaseUrl).hostname;
  const webAppName = extensionBranding?.appName ?? "Heidi";
  const webAppIcon = extensionBranding?.icon ?? "/assets/heidi.png";
  const webThemeColor = extensionBranding?.themeColor ?? "#F4F8F9";
  const escapeHtml = (value: string) =>
    value
      .replaceAll("&", "&amp;")
      .replaceAll('"', "&quot;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;");

  return {
    envDir,
    plugins: [
      extensionEntryPlugin(extensionRoot),
      extensionBrandingPlugin(extensionBranding),
      noRuntimeConfigInDev,
      devtools(),
      tanstackRouter({
        target: "react",
        autoCodeSplitting: true,
        quoteStyle: "double",
        semicolons: true,
        // Declaring the route tree explicitly rather than letting it be inferred
        // from the routes directory alone: builds that carry additional routes
        // mount their own directory alongside this one.
        routesDirectory: "src",
        virtualRouteConfig: rootRoute("routes/__root.tsx", [
          physical("", "routes"),
          ...(extensionRoutes && existsSync(extensionRoutes)
            ? [physical("", extensionRoutes)]
            : []),
        ]),
      }),
      tailwindcss(),
      react(),
      babel({
        presets: [reactCompilerPreset()],
      }),
      wasm(),
    ],
    build: {
      target: "es2022", // Set the target to es2022 or later
      rollupOptions: {
        input: {
          index: resolve(process.cwd(), "index.html"),
          "api-docs": resolve(process.cwd(), "api-docs.html"),
        },
      },
    },
    define: {
      "import.meta.env.VITE_HEIDI_WEB_APP_NAME": JSON.stringify(webAppName),
      "import.meta.env.VITE_HEIDI_WEB_HTML_TITLE": JSON.stringify(
        escapeHtml(webAppName),
      ),
      "import.meta.env.VITE_HEIDI_WEB_APP_ICON": JSON.stringify(
        escapeHtml(webAppIcon),
      ),
      "import.meta.env.VITE_HEIDI_WEB_THEME_COLOR":
        JSON.stringify(webThemeColor),
      ...(command === "serve" && {
        // Browser API calls use the shared Caddy origin, even when Vite runs directly.
        "import.meta.env.VITE_HEIDI_API_BASE_URL": JSON.stringify(apiBaseUrl),
      }),
    },
    server: {
      port: Number(process.env.HEIDI_WEB_PORT ?? 5173),
      host: true,
      allowedHosts: [publicHost],
      // The backends allow this exact origin, so a port taken by something
      // else has to fail rather than silently move the dev server.
      strictPort: true,
      proxy: {
        "/api-docs/": {
          target: apiBaseUrl,
          changeOrigin: true,
        },
      },
      ...(process.env.PROXY_MODE === "true" && {
        hmr: {
          port: 3000,
        },
      }),
    },
    resolve: {
      alias: {
        // Extension packages are compiled from node_modules but may import
        // the host application's public OSS modules through the same alias
        // used by files under src/.
        "@": resolve(process.cwd(), "src"),
      },
      tsconfigPaths: true,
    },
  };
});
