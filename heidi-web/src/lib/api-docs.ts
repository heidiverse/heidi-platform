// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

const INTEGRATOR_OPENAPI_PATH = "api-docs/integrator";

export type ApiDocsMode = "development" | "production";

export function apiDocsUrl(baseUrl: string, mode: ApiDocsMode): string {
  if (mode === "development") return `/${INTEGRATOR_OPENAPI_PATH}`;

  const normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`;
  return new URL(INTEGRATOR_OPENAPI_PATH, normalizedBaseUrl).href;
}
