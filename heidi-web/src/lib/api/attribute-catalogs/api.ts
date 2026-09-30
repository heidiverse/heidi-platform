// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect } from "@/lib/utils";
import type { DisplayNames } from "@/types";
import type { AttributeType } from "@/types/credential-schema";

export async function getAttributeCatalogs() {
  const res = await fetchWithRedirect(
    new URL("management/v1/attribute-catalogs", runtimeConfig.heidiApiBaseUrl),
  );
  if (!res.ok) {
    throw new Error(`Network Response was not ok! ${res.statusText}`);
  }
  return (await res.json()) as AttributeCatalog[];
}

export type AttributeCatalog = {
  id: number;
  catalogDisplayName: string;
  specificationDisplayName: string;
  specificationUrl: string;
  attributes: {
    id: number;
    attributeKey: string;
    attributeType: AttributeType;
    attributeDisplayName: DisplayNames;
  }[];
};
