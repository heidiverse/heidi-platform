// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

const LOCAL_MODE = "dev-local";

export function resolveApiBaseUrl(
  mode: string,
  configuredUrl: string,
  browserOrigin: string,
): string {
  // Local HTTP and HTTPS entrypoints both keep browser calls on their origin.
  if (mode === LOCAL_MODE) {
    return new URL("/", browserOrigin).href;
  }

  return configuredUrl;
}
