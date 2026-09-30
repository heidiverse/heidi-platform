// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { resolveApiBaseUrl } from "./runtime-config-url";

export interface RuntimeConfig {
  heidiApiBaseUrl: string;
  heidiDefaultNamespace: string;
  heidiDefaultDoctype: string;
  appVersion: string;
  /**
   * Where signing out goes. Deployments that put an authenticating proxy in front
   * of the cockpit set this to the path that ends the session; without one there
   * is no session to end, and the cockpit offers no sign-out.
   */
  logoutUrl?: string;
}

/**
 * Configuration belonging to extensions rather than the cockpit itself. An
 * extension declares what it needs by augmenting this; deployments supply the
 * values in `config.json` next to the built-in ones.
 */
// biome-ignore lint/suspicious/noEmptyInterface: the extension point is empty by design
export interface RuntimeConfigExtensions {}

const viteConfig: RuntimeConfig = {
  heidiApiBaseUrl: resolveApiBaseUrl(
    import.meta.env.MODE,
    import.meta.env.VITE_HEIDI_API_BASE_URL,
    window.location.origin,
  ),
  heidiDefaultNamespace: import.meta.env.VITE_HEIDI_DEFAULT_NAMESPACE,
  heidiDefaultDoctype: import.meta.env.VITE_HEIDI_DEFAULT_DOCTYPE,
  appVersion: import.meta.env.VITE_APP_VERSION,
};

export let runtimeConfig = viteConfig as RuntimeConfig & RuntimeConfigExtensions;

export async function loadRuntimeConfig(): Promise<void> {
  // Local Vite runs get their service URLs from the root .env.local. The committed
  // config.json contains the default checkout's deployment URLs and would
  // otherwise override a secondary checkout's ports before the app starts.
  if (import.meta.env.MODE === "dev-local") {
    return;
  }

  try {
    const response = await fetch(`/config.json?t=${Date.now()}`, {
      cache: "no-store",
    });

    if (!response.ok) {
      return;
    }

    const config = (await response.json()) as Partial<
      RuntimeConfig & RuntimeConfigExtensions
    >;

    runtimeConfig = { ...runtimeConfig, ...config };
  } catch {
    // Vite env values keep local development and tests working without config.json.
  }
}
