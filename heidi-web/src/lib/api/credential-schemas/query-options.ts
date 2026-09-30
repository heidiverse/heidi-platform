// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { queryOptions } from "@tanstack/react-query";
import type { CredentialSchemaState } from "@/types/credential-schema";
import { getSchema, getSchemaOverview } from "./api";

export function schemaListOptions(
  options: {
    statesToExclude?: CredentialSchemaState[];
    includeStyle?: boolean;
    includeImages?: boolean;
    credentialIdentifier?: string;
    isPublic?: boolean;
  } = {},
) {
  return queryOptions({
    queryKey: ["credential-schemas", ...Object.values(options)],
    queryFn: () => getSchemaOverview(options),
  });
}

export function schemaOptions(
  data:
    | { schemaId: string }
    | { credentialIdentifier: string; version: string },
  options?: { isPublic?: boolean },
) {
  return queryOptions({
    queryKey: [
      "credential-schema",
      ...("schemaId" in data
        ? [data.schemaId]
        : [data.credentialIdentifier, data.version]),
      options,
    ],
    queryFn: () =>
      getSchema({
        ...data,
        ...(options?.isPublic && { isPublic: options.isPublic }),
      }),
    refetchOnWindowFocus: false,
    refetchInterval: false,
    refetchOnReconnect: false,
  });
}
