// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { customAlphabet } from "nanoid";
import { compare, gt } from "semver";
import { omit } from "@/lib/utils";
import { argbToHex, hexToArgb } from "@/lib/utils/color";
import { sortAttributes } from "@/lib/utils/sort-attributes";
import type { CredentialSchemaForm } from "@/routes/_authenticated/credential-schemas/-components/schemas";
import {
  CredentialOfferType,
  type CredentialSchema,
  type CredentialSchemaLite,
  type CredentialSchemaSendPayload,
  CredentialSchemaState,
  OcaVersion,
} from "@/types/credential-schema";
import { issuanceProfileForTrustSystem } from "@/types/ecosystem-profile";
import {
  normalizeConfiguredTrustSystem,
  normalizeSigningKeyIds,
} from "@/types/trust-system";

export function selectNewestAndDraftSchema(
  schemas: CredentialSchemaLite[],
): Record<
  string,
  {
    newestPublished?: CredentialSchemaLite;
    newestDraft?: CredentialSchemaLite;
  }
> {
  const relevantSchemas = schemas.reduce(
    (acc, curr) => {
      if (!acc[curr.credentialIdentifier]) {
        acc[curr.credentialIdentifier] = {
          newestPublished: undefined,
          newestDraft: undefined,
        };
      }
      if (
        isPublished(curr) &&
        (acc[curr.credentialIdentifier]!.newestPublished === undefined ||
          compare(
            curr.version,
            acc[curr.credentialIdentifier]!.newestPublished!.version,
          ) > 0)
      ) {
        acc[curr.credentialIdentifier]!.newestPublished = curr;
      } else if (
        curr.state === CredentialSchemaState.Created && // is draft
        (acc[curr.credentialIdentifier]!.newestDraft === undefined ||
          // pick the newest draft (updated at) - there should only be one
          curr.updatedAt >
            acc[curr.credentialIdentifier]!.newestDraft!.updatedAt)
      ) {
        acc[curr.credentialIdentifier]!.newestDraft = curr;
      }

      return acc;
    },
    {} as Record<
      string,
      {
        newestPublished?: CredentialSchemaLite;
        newestDraft?: CredentialSchemaLite;
      }
    >,
  );

  return relevantSchemas;
}

export function selectNewestSchemas(schemas: CredentialSchemaLite[]) {
  return Object.values(
    schemas.reduce(
      (prev, cur) => {
        if (
          !prev[cur.credentialIdentifier] ||
          (prev[cur.credentialIdentifier] &&
            gt(cur.version, prev[cur.credentialIdentifier]!.version))
        ) {
          prev[cur.credentialIdentifier] = cur;
        }
        return prev;
      },
      {} as Record<string, CredentialSchemaLite>,
    ),
  );
}

function isPublished(schema: CredentialSchemaLite | CredentialSchema) {
  return schema.state === CredentialSchemaState.Published;
}

export function marshalCredentialSchema(
  schema: CredentialSchemaForm,
  includeID = false,
): CredentialSchemaSendPayload {
  const safeSchema = omit(schema, ["tenantId", "state"]);
  const {
    style,
    id,
    attributes,
    metaAttributes,
    issuingSettings,
    templateId,
    ...credential
  } = safeSchema;

  const res: CredentialSchemaSendPayload = {
    ...credential,
    attributes: attributes
      .filter((a) => Boolean(a.name))
      .map((a) => ({
        ...a,
        attributeNameOverrides: Object.fromEntries(
          Object.entries(a.attributeNameOverrides ?? {}).filter(([, value]) =>
            Boolean(value),
          ),
        ),
      })),
    metadata: {
      metaAttributes: metaAttributes
        .filter((a) => a.name)
        .map(({ name, displayName }) => ({
          displayName: Object.fromEntries(
            Object.entries(displayName).filter(([, v]) => !!v),
          ),
          attributeKey: name,
        })),
    },
    credentialSchemeStylePayloads: [
      {
        style: {
          title: style.cardTitle,
          subtitle: style.cardSubtitle,
          textColor: style.textColor,
          cardColor: hexToArgb(style.cardColor),
          backgroundCard: style.backgroundImage,
          orderedProperties: attributes.map((a) => a.name).filter(Boolean),
          frontOverlays: style.frontOverlays,
        },
        ocaBundle: undefined,
        ocaVersion: style.ocaVersion,
        cardColor: style.cardColor,
        textColor: style.textColor,
      },
    ],
    issuerSettings: {
      id: issuingSettings.issuerId,
      issuanceProfileId: issuingSettings.issuanceProfileId,
      issuerKeyType: issuingSettings.keyType,
      doctype: issuingSettings.doctype || null,
      namespace: issuingSettings.namespace || null,
      vct: issuingSettings.vct || null,
      supportedCredentialTypes: issuingSettings.supportedCredentialTypes || [],
      defaultTrustSystem: issuingSettings.defaultTrustSystem ?? null,
      signingKeyIds: Object.fromEntries(
        Object.entries(issuingSettings.signingKeyIds ?? {}).filter(
          ([, value]) => Boolean(value),
        ),
      ),
      statusListId: issuingSettings.statusListId || null,
      credentialOfferType:
        issuingSettings.credentialOfferType ?? CredentialOfferType.Value,
    },
    templateId: templateId || null,
  };

  if (includeID) {
    return { ...res, id };
  }
  return res;
}

const nanoid = customAlphabet("123456789", 6);
export function getDefaultValuesForSchema(
  schema: CredentialSchema,
  languagesForSchema: string[],
): CredentialSchemaForm {
  const orderedProperties =
    schema.credentialSchemeStyleDetails[0]?.style.orderedProperties;
  const attributes = orderedProperties
    ? sortAttributes(schema.attributes, orderedProperties)
    : schema.attributes;

  return {
    id: schema.id,
    tenantId: schema.tenantId,
    credentialIdentifier: schema.credentialIdentifier,
    version: schema.version,
    displayName: schema.displayName,
    maxBatchSize: schema.maxBatchSize ?? 1,
    state: schema.state,
    attributes: attributes.map((a) => ({
      ...a,
      displayName: Object.fromEntries(
        languagesForSchema.map((lang) => [lang, a.displayName[lang] ?? ""]),
      ),
    })),
    metaAttributes:
      schema.schemeMetadata?.metaAttributes.map(
        ({ attributeKey, displayName }) => ({
          id: Number.parseInt(nanoid(), 10),
          name: attributeKey,
          displayName: Object.fromEntries(
            languagesForSchema.map((lang) => [lang, displayName[lang] ?? ""]),
          ),
        }),
      ) ?? [],
    style: {
      cardTitle: schema.credentialSchemeStyleDetails[0]?.style.title ?? "",
      cardSubtitle:
        schema.credentialSchemeStyleDetails[0]?.style.subtitle ?? "",
      textColor:
        schema.credentialSchemeStyleDetails[0]?.style.textColor ?? "light",
      cardColor: argbToHex(
        schema.credentialSchemeStyleDetails[0]?.style.cardColor,
      ),
      backgroundImage:
        schema.credentialSchemeStyleDetails[0]?.style.backgroundCard || "",
      orderedProperties:
        schema.credentialSchemeStyleDetails[0]?.style.orderedProperties ?? [],
      frontOverlays:
        schema.credentialSchemeStyleDetails[0]?.style.frontOverlays ?? [],
      ocaVersion:
        schema.credentialSchemeStyleDetails[0]?.ocaVersion ?? OcaVersion.Legacy,
    },
    issuingSettings: {
      issuerId: schema.issuerSettings.id,
      issuanceProfileId:
        schema.issuerSettings.issuanceProfileId ??
        issuanceProfileForTrustSystem(schema.issuerSettings.defaultTrustSystem),
      keyType: schema.issuerSettings.issuerKeyType,
      doctype: schema.issuerSettings.doctype || "",
      namespace: schema.issuerSettings.namespace || "",
      vct: schema.issuerSettings.vct || "",
      supportedCredentialTypes:
        schema.issuerSettings.supportedCredentialTypes || [],
      defaultTrustSystem: normalizeConfiguredTrustSystem(
        schema.issuerSettings.defaultTrustSystem,
      ),
      signingKeyIds: normalizeSigningKeyIds(
        schema.issuerSettings.signingKeyIds,
      ),
      statusListId: schema.issuerSettings.statusListId || undefined,
      credentialOfferType:
        schema.issuerSettings.credentialOfferType ?? CredentialOfferType.Value,
    },
    templateId: schema.templateId || "",
  };
}

export function formatHandlebars(
  attributes: NonNullable<CredentialSchemaForm["attributes"]>,
  metaAttributes: NonNullable<CredentialSchemaForm["metaAttributes"]>,
  content: string,
) {
  let res = content;
  for (const { name, displayName } of [...attributes, ...metaAttributes]) {
    const firstDisplayName = Object.values(displayName).find(Boolean);
    if (!firstDisplayName) {
      continue;
    }
    res = res.replaceAll(`{{${name}}}`, firstDisplayName);
  }
  return res;
}
