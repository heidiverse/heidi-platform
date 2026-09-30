// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect } from "@/lib/utils";

export type TestingProcessBody = Record<string, unknown>;

export type TestingProcessResponse = {
  processId: string;
  clientInteractionToken: string;
  txCode: string | null;
  expiresAt: string;
};

/**
 * The Cockpit testing UI uses the authenticated platform session. The combined endpoint keeps
 * initialization and start server-side, so the backend-only process token never reaches the
 * browser; only the resulting client interaction token is returned.
 */
export async function createTestingProcess(
  body: TestingProcessBody,
): Promise<TestingProcessResponse> {
  const response = await fetchWithRedirect(
    new URL(
      "management/v1/testing/processes",
      runtimeConfig.heidiApiBaseUrl,
    ),
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    },
  );
  if (!response.ok) {
    throw new Error("Failed to create testing process");
  }
  return (await response.json()) as TestingProcessResponse;
}
