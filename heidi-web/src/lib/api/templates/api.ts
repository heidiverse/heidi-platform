// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect } from "@/lib/utils";
import { readApiError } from "@/lib/utils/api-error";
import type {
  AttributeType,
  CredentialOfferType,
  CredentialSchemaStyle,
  IssuerKeyTypes,
  SupportedCredentialTypes,
} from "@/types/credential-schema";
import type { IssuanceProfileId } from "@/types/ecosystem-profile";
import type { TrustSystem } from "@/types/trust-system";

interface TemplateLibrary {
  id: string;
  key: string;
  displayName: string;
  templateUrls: string[];
}

export interface Template {
  id: string;
  displayName: string;
  libraryKey: TemplateLibrary["key"];
  attributes: AttributesItem[];
  issuerSettings: IssuerSettings;
  attributeRules: Record<string, AttributeRule>;
  style?: CredentialSchemaStyle;
  metaAttributes?: Omit<AttributesItem, "type">[];
}

interface AttributesItem {
  name: string;
  isSensitive?: boolean;
  isDisclosable?: boolean;
  isArray?: boolean;
  type: AttributeType;
  displayName: Record<string, string>;
}

interface IssuerSettings {
  issuerKeyType: IssuerKeyTypes;
  issuanceProfileId?: IssuanceProfileId;
  credentialOfferType?: CredentialOfferType;
  supportedCredentialTypes?: SupportedCredentialTypes[];
  doctype: string;
  namespace: string;
  vct: string;
  signingKeyIds?: Partial<Record<TrustSystem, string | null>>;
  defaultTrustSystem?: TrustSystem | null;
}

export interface AttributeRule {
  description: string;
  isDisplayNameEditable: boolean;
  isRequired: boolean;
  canBeEmpty: boolean;
}

export async function getTemplateLibraries() {
  const res = await fetchWithRedirect(
    new URL("management/v1/templates/libraries", runtimeConfig.heidiApiBaseUrl),
  );
  if (!res.ok) {
    throw new Error("Failed to fetch template libraries");
  }
  return (await res.json()) as TemplateLibrary[];
}

export async function getTemplates() {
  const res = await fetchWithRedirect(
    new URL("management/v1/templates", runtimeConfig.heidiApiBaseUrl),
  );
  if (!res.ok) {
    throw new Error("Failed to fetch templates");
  }
  return (await res.json()) as Template[];
}

export async function getTemplate(templateId: string) {
  const res = await fetchWithRedirect(
    new URL(`management/v1/templates/${templateId}`, runtimeConfig.heidiApiBaseUrl),
  );
  if (!res.ok) {
    throw new Error(`Failed to fetch template with ID: ${templateId}`);
  }
  return (await res.json()) as Template;
}

export async function addLibrary(libraryUrl: string) {
  const url = new URL(
    "management/v1/templates/libraries",
    runtimeConfig.heidiApiBaseUrl,
  );
  url.searchParams.set("libraryUrl", libraryUrl);
  const res = await fetchWithRedirect(url, { method: "POST" });
  if (!res.ok) {
    throw new Error("Failed to add library");
  }
}

export async function deleteLibrary(libraryId: string) {
  const res = await fetchWithRedirect(
    new URL(
      `management/v1/templates/libraries/${libraryId}`,
      runtimeConfig.heidiApiBaseUrl,
    ),
    { method: "DELETE" },
  );
  if (!res.ok) {
    throw new Error("Failed to delete library");
  }
}

export async function importI14yTemplate(datasetIdentifier: string) {
  const res = await fetchWithRedirect(
    new URL("management/v1/templates/i14y", runtimeConfig.heidiApiBaseUrl),
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ datasetId: datasetIdentifier }),
    },
  );
  if (!res.ok) {
    throw new Error(await readApiError(res, "Failed to import I14Y schema"));
  }
  return (await res.json()) as {
    template: Template;
    warnings: string[];
  };
}
