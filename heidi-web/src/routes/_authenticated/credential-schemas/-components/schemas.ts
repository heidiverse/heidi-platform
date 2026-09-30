// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { useIntl } from "react-intl";
import { z } from "zod";
import { credentialSchemaValidationMessages } from "@/lib/translations";
import {
  AttributeType,
  CredentialOfferType,
  CredentialSchemaState,
  FrontOverlayContentTypes,
  FrontOverlayPositions,
  IssuerKeyTypes,
  OcaVersion,
  OverrideCredentialTypes,
  SupportedCredentialTypes,
  TextColor,
} from "@/types/credential-schema";
import { issuanceProfileIds } from "@/types/ecosystem-profile";
import { TrustSystem, trustFrameworks } from "@/types/trust-system";

export function createCredentialSchemaFormSchema(
  intl: ReturnType<typeof useIntl>,
) {
  const sharedAttributesShema = z.object({
    id: z.number({
      error: () =>
        intl.$t(credentialSchemaValidationMessages.validationIdRequired),
    }),
    name: z.string().min(1, {
      error: () =>
        intl.$t(credentialSchemaValidationMessages.validationKeyRequired),
    }),
    displayName: z.record(z.string(), z.string()).check((ctx) => {
      if (!Object.values(ctx.value).some((v) => v && v.length > 0)) {
        ctx.issues.push({
          code: "custom",
          message: intl.$t(
            credentialSchemaValidationMessages.validationDisplayNameRequired,
          ),
          input: ctx.value,
          path: [Object.keys(ctx.value)[0] ?? "de"],
        });
      }
    }),
  });

  const attributesSchema = z.object({
    attributes: z.array(
      sharedAttributesShema.extend({
        type: z.enum(AttributeType, {
          error: () =>
            intl.$t(
              credentialSchemaValidationMessages.validationAttributeTypeInvalid,
            ),
        }),
        isSensitive: z.boolean(),
        isDisclosable: z.boolean(),
        isArray: z.boolean(),
        attributeNameOverrides: z.partialRecord(
          z.enum(OverrideCredentialTypes),
          z.string(),
        ),
      }),
    ),
    metaAttributes: z.array(sharedAttributesShema),
  });

  const styleSchema = z.object({
    style: z.object({
      cardTitle: z.string(),
      cardSubtitle: z.string(),
      textColor: z.enum(TextColor),
      cardColor: z.string(),
      ocaVersion: z.enum(OcaVersion),
      backgroundImage: z.string().nullable(),
      orderedProperties: z.array(z.string()),
      frontOverlays: z.array(
        z.object({
          position: z.enum(FrontOverlayPositions),
          contentType: z.enum(FrontOverlayContentTypes),
          content: z.string().min(1, {
            error: () =>
              intl.$t(
                credentialSchemaValidationMessages.validationFrontOverlayContentRequired,
              ),
          }),
          showLabel: z.optional(z.boolean()),
        }),
      ),
    }),
  });

  const issuingSettingsSchema = z.object({
    issuingSettings: z.object({
      doctype: z.string().optional(),
      namespace: z.string().optional(),
      vct: z.string().optional(),
      issuerId: z.number(),
      issuanceProfileId: z.enum(issuanceProfileIds),
      keyType: z.enum(IssuerKeyTypes, {
        error: () =>
          intl.$t(credentialSchemaValidationMessages.validationKeyTypeInvalid),
      }),
      supportedCredentialTypes: z.array(z.enum(SupportedCredentialTypes)),
      defaultTrustSystem: z.enum(trustFrameworks).optional(),
      signingKeyIds: z.object({
        [TrustSystem.Switzerland]: z.string().optional(),
        [TrustSystem.EUDI]: z.string().optional(),
        [TrustSystem.Custom]: z.string().optional(),
        [TrustSystem.OIDF]: z.string().optional(),
        [TrustSystem.Default]: z.string().optional(),
      }),
      statusListId: z.string().optional(),
      credentialOfferType: z.enum(CredentialOfferType),
    }),
  });

  const generalCredentialSchema = z.object({
    id: z.string(),
    state: z.enum(CredentialSchemaState),
    tenantId: z.string().nullable(),
    credentialIdentifier: z.string(),
    version: z.string(),
    displayName: z.string().min(1, {
      error: () =>
        intl.$t(
          credentialSchemaValidationMessages.validationSchemaDisplayNameRequired,
        ),
    }),
    maxBatchSize: z.number().int().min(1),
    templateId: z.string().optional(),
  });

  return z.object({
    ...generalCredentialSchema.shape,
    ...attributesSchema.shape,
    ...styleSchema.shape,
    ...issuingSettingsSchema.shape,
  });
}

export type CredentialSchemaForm = z.infer<
  ReturnType<typeof createCredentialSchemaFormSchema>
>;
