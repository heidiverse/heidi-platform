// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect } from "@/lib/utils";

export async function getDcqlQuery({
  proofSchemaId,
}: {
  proofSchemaId: string;
}) {
  const res = await fetchWithRedirect(
    new URL(
      `interaction/v1/presentation/dcqlQuery/${proofSchemaId}`,
      runtimeConfig.heidiApiBaseUrl,
    ),
  );
  if (!res.ok) {
    throw new Error("Failed to fetch DCQL Query");
  }
  return (await res.json()) as { credentials: Record<string, unknown>[] };
}
