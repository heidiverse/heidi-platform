// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { queryOptions } from "@tanstack/react-query";
import {
  getProofSchemaById,
  getProofSchemas,
} from "@/lib/api/proof-schemas/api";

export function proofSchemaListOptions({
  isPublic = false,
  credentialIdentifiers = [],
}: {
  isPublic?: boolean;
  credentialIdentifiers?: string[];
} = {}) {
  return queryOptions({
    queryKey: ["proof-schemas", isPublic, ...credentialIdentifiers],
    queryFn: () => getProofSchemas({ isPublic, credentialIdentifiers }),
  });
}

export function proofSchemaOptions({
  schemaId,
  isPublic = false,
}: {
  schemaId: string;
  isPublic?: boolean;
}) {
  return queryOptions({
    queryKey: ["proof-schema", schemaId, isPublic],
    queryFn: () => getProofSchemaById({ schemaId, isPublic }),
  });
}
