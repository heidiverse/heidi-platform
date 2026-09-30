// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { customAlphabet } from "nanoid";
import slugify from "slugify";
import { jotaiStore, selectedTenantAtom } from "@/lib/atoms";
import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect } from "@/lib/utils";
import { readApiError } from "@/lib/utils/api-error";
import { argbToHex } from "@/lib/utils/color";
import { marshalCredentialSchema } from "@/lib/utils/credential-schemas";
import type { CredentialSchemaForm } from "@/routes/_authenticated/credential-schemas/-components/schemas";
import {
  type CreateCredentialSchemaResponse,
  type CredentialSchema,
  type CredentialSchemaOverviewResponse,
  type CredentialSchemaSendPayload,
  CredentialSchemaState,
  OcaVersion,
} from "@/types/credential-schema";

const nanoid = customAlphabet("1234567890abcdefghijklmnopqrstuvwxyz", 5);

export async function createSchema(
  schema: Omit<CredentialSchemaForm, "tenantId" | "state">,
) {
  const url = new URL(
    "management/v1/credential-schemas",
    runtimeConfig.heidiApiBaseUrl,
  );
  const selectedTenant = jotaiStore.get(selectedTenantAtom);
  selectedTenant && url.searchParams.set("tenantId", selectedTenant);
  const res = await fetchWithRedirect(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(
      marshalCredentialSchema(schema as CredentialSchemaForm),
    ),
  });
  if (!res.ok) throw new Error(await readApiError(res, "Could not create schema"));
  return (await res.json()) as CreateCredentialSchemaResponse;
}

export async function createDraftFromSchema(schema: CredentialSchemaForm) {
  // unset verison to create a new draft
  schema.version = "";
  // backand should handle this correctly but I want to be save
  schema.state = CredentialSchemaState.Created;
  return createSchema(schema);
}

export async function copySchemaToOrganisation({
  tenantId,
  id,
  credentialSchemeStyleDetails,
  schemeMetadata,
  state,
  ...schema
}: CredentialSchema) {
  if (!tenantId) {
    throw new Error("tenantId is required");
  }
  const url = new URL(
    "management/v1/credential-schemas",
    runtimeConfig.heidiApiBaseUrl,
  );
  url.searchParams.set("tenantId", tenantId);

  schema.credentialIdentifier = `${slugify(schema.displayName, { strict: true, lower: true, locale: "de" })}-${nanoid()}`;
  schema.version = "";

  const body: CredentialSchemaSendPayload = {
    ...schema,
    credentialSchemeStylePayloads: credentialSchemeStyleDetails.map(
      ({ style, ocaVersion }) => ({
        style: {
          title: style.title,
          subtitle: style.subtitle,
          textColor: style.textColor,
          cardColor: style.cardColor,
          backgroundCard: style.backgroundCard,
          orderedProperties: schema.attributes
            .map((a) => a.name)
            .filter(Boolean),
        },
        ocaBundle: undefined,
        ocaVersion: ocaVersion ?? OcaVersion.Legacy,
        cardColor: argbToHex(style.cardColor),
        textColor: style.textColor,
      }),
    ),
    metadata: {
      metaAttributes: schemeMetadata.metaAttributes
        .filter((a) => a.displayName)
        .map(({ attributeKey, displayName }) => ({
          displayName: Object.fromEntries(
            Object.entries(displayName).filter(([, v]) => !!v),
          ),
          attributeKey,
        })),
    },
  };

  const res = await fetchWithRedirect(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    throw new Error("Failed to copy schema");
  }
  return (await res.json()) as CreateCredentialSchemaResponse;
}

export async function updateSchema(schema: CredentialSchemaForm) {
  const res = await fetchWithRedirect(
    new URL(
      `management/v1/credential-schemas/${schema.id}`,
      runtimeConfig.heidiApiBaseUrl,
    ),
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(marshalCredentialSchema(schema, true)),
    },
  );
  if (!res.ok) throw new Error(await readApiError(res, "Could not save schema"));
  return schema;
}

export async function publishSchema({
  schemaId,
  version,
}: {
  schemaId: string;
  version: string;
}) {
  const res = await fetchWithRedirect(
    new URL(
      `management/v1/credential-schemas/${schemaId}/publish?version=${version}`,
      runtimeConfig.heidiApiBaseUrl,
    ),
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
    },
  );
  if (!res.ok) throw new Error(await readApiError(res, "Could not publish schema"));
}

export async function archiveSchema(schemaId: string) {
  const res = await fetchWithRedirect(
    new URL(
      `management/v1/credential-schemas/${schemaId}/archive`,
      runtimeConfig.heidiApiBaseUrl,
    ),
    {
      method: "PUT",
    },
  );
  if (!res.ok) throw new Error(await readApiError(res, "Could not archive schema"));
}

export async function getSchema({
  isPublic,
  ...data
}:
  | {
    schemaId: string;
    isPublic?: boolean;
  }
  | { credentialIdentifier: string; version: string; isPublic?: boolean }) {
  const fetchUsingSchemaId = "schemaId" in data;
  const res = await fetchWithRedirect(
    new URL(
      fetchUsingSchemaId
        ? isPublic
          ? `public/v2/schema/${data.schemaId}`
          : `management/v1/credential-schemas/${data.schemaId}`
        : isPublic
          ? `public/v2/schema/${data.credentialIdentifier}/${data.version}/detail`
          : `management/v1/credential-schemas/${data.credentialIdentifier}/${data.version}/detail`,
      runtimeConfig.heidiApiBaseUrl,
    ),
  );
  if (!res.ok) {
    throw new Error(`Network Response was not ok! ${res.statusText}`);
  }
  const schema = (await res.json()) as CredentialSchema;
  return schema;
}

export async function getSchemaOverview({
  statesToExclude = [],
  includeStyle = true,
  includeImages = true,
  credentialIdentifier,
  isPublic = false,
}: {
  statesToExclude?: CredentialSchemaState[];
  includeStyle?: boolean;
  includeImages?: boolean;
  credentialIdentifier?: string;
  isPublic?: boolean;
} = {}) {
  const url = new URL(
    isPublic ? "public/v2/schema/overview" : "management/v1/credential-schemas/overview",
    runtimeConfig.heidiApiBaseUrl,
  );
  if (statesToExclude.length) {
    const statesToInclude = Object.values(CredentialSchemaState).filter(
      (state) => !statesToExclude.includes(state),
    );

    if (statesToInclude.length) {
      url.searchParams.set("state", statesToInclude.join(","));
    }
  }
  !includeStyle && url.searchParams.set("includeStyle", "false");
  !includeImages && url.searchParams.set("includeImages", "false");
  credentialIdentifier &&
    url.searchParams.set("credentialIdentifier", credentialIdentifier);
  const res = await fetchWithRedirect(url);
  if (!res.ok) {
    throw new Error("Network Response was not ok");
  }
  const overview = (await res.json()) as CredentialSchemaOverviewResponse;
  return overview.credentialSchemes;
}
