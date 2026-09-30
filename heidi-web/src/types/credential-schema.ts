// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { DisplayNames } from ".";
import type { IssuanceProfileId } from "./ecosystem-profile";
import type { ConfiguredTrustSystem, TrustSystem } from "./trust-system";

export const AttributeType = {
  String: "STRING",
  Number: "NUMBER",
  Date: "DATE",
  DateOfBirth: "DATEOFBIRTH",
  DateTime: "DATETIME",
  Time: "TIME",
  Boolean: "BOOLEAN",
  Image: "IMAGE",
  Location: "LOCATION",
  Other: "OTHER",
  Link: "LINK",
  FileDownload: "FILE_DOWNLOAD",
  Mail: "MAIL",
  Phone: "PHONE",
} as const;
export type AttributeType = (typeof AttributeType)[keyof typeof AttributeType];

export const fieldTypes: { [key in AttributeType]: string } = {
  [AttributeType.String]: "String",
  [AttributeType.Number]: "Number",
  [AttributeType.Date]: "Date",
  [AttributeType.DateOfBirth]: "Date of Birth",
  [AttributeType.DateTime]: "Datetime",
  [AttributeType.Time]: "Time",
  [AttributeType.Boolean]: "Boolean",
  [AttributeType.Image]: "Image",
  [AttributeType.Location]: "Location",
  [AttributeType.Other]: "Other",
  [AttributeType.Link]: "Link",
  [AttributeType.FileDownload]: "File download",
  [AttributeType.Mail]: "Mail",
  [AttributeType.Phone]: "Phone",
};

export const TextColor = {
  Light: "light",
  Dark: "dark",
} as const;
export type TextColor = (typeof TextColor)[keyof typeof TextColor];

export const OcaVersion = {
  Legacy: "LEGACY",
  Swiyu: "SWIYU",
} as const;
export type OcaVersion = (typeof OcaVersion)[keyof typeof OcaVersion];

export const IssuerKeyTypes = {
  HARDWARE_BIOMETRIC_AUTH: "HARDWARE_BIOMETRIC_AUTH",
  SOFTWARE_NO_AUTH: "SOFTWARE_NO_AUTH",
  NO_KEY_CLAIM_BINDING: "NO_KEY_CLAIM_BINDING",
} as const;

export type IssuerKeyTypes =
  (typeof IssuerKeyTypes)[keyof typeof IssuerKeyTypes];

export const SupportedCredentialTypes = {
  SdJwt: "SD_JWT",
  Mdoc: "MSO_MDOC",
  Zkp: "ZKP_VC",
  W3C: "W3C_VCDM",
} as const;
export type SupportedCredentialTypes =
  (typeof SupportedCredentialTypes)[keyof typeof SupportedCredentialTypes];

export const CredentialOfferType = {
  Uri: "URI",
  Value: "VALUE",
} as const;
export type CredentialOfferType =
  (typeof CredentialOfferType)[keyof typeof CredentialOfferType];

export const FrontOverlayPositions = {
  BottomRight: "BottomRight",
  BottomLeft: "BottomLeft",
  TopRight: "TopRight",
} as const;
export type FrontOverlayPositions =
  (typeof FrontOverlayPositions)[keyof typeof FrontOverlayPositions];

export const FrontOverlayContentTypes = {
  ImagePortrait: "Image",
  ImageLogo: "ImageLogo",
  ImageIcon: "ImageIcon",
  Text: "Text",
} as const;
export type FrontOverlayContentTypes =
  (typeof FrontOverlayContentTypes)[keyof typeof FrontOverlayContentTypes];

export const AllowedOverlayContentTypesForPosition: Record<
  FrontOverlayPositions,
  FrontOverlayContentTypes[]
> = {
  [FrontOverlayPositions.BottomRight]: [
    FrontOverlayContentTypes.Text,
    FrontOverlayContentTypes.ImagePortrait,
    FrontOverlayContentTypes.ImageLogo,
    FrontOverlayContentTypes.ImageIcon,
  ],
  [FrontOverlayPositions.BottomLeft]: [
    FrontOverlayContentTypes.Text,
    FrontOverlayContentTypes.ImagePortrait,
    FrontOverlayContentTypes.ImageLogo,
    FrontOverlayContentTypes.ImageIcon,
  ],
  [FrontOverlayPositions.TopRight]: [
    FrontOverlayContentTypes.ImageLogo,
    FrontOverlayContentTypes.ImageIcon,
  ],
};

export type CredentialSchemaStyle = {
  title: string;
  subtitle: string;
  textColor: TextColor;
  cardColor: number;
  backgroundCard: string | null;
  orderedProperties: string[];
  frontOverlays?: {
    content: string;
    position: FrontOverlayPositions;
    contentType: FrontOverlayContentTypes;
    showLabel?: boolean;
  }[];
};

type CredentialSchemaStyleDetails = {
  style: CredentialSchemaStyle;
  // biome-ignore lint/suspicious/noExplicitAny: TODO: dont use any
  ocaBundle: any; // we are not using it, so we don't care about the type
  ocaVersion?: OcaVersion;
};

type CredentialSchemaStylePayload = CredentialSchemaStyleDetails & {
  textColor: TextColor;
  cardColor: string;
  ocaVersion: OcaVersion;
};

export const CredentialSchemaState = {
  Created: "CREATED",
  Published: "PUBLISHED",
  Archived: "ARCHIVED",
} as const;
export type CredentialSchemaState =
  (typeof CredentialSchemaState)[keyof typeof CredentialSchemaState];

export const OverrideCredentialTypes = ["SD_JWT", "MSO_MDOC"] as const;
export type OverrideCredentialTypes = (typeof OverrideCredentialTypes)[number];
export type AttributeNameOverrides = Partial<
  Record<OverrideCredentialTypes, string>
>;

export type CredentialSchemaAttribute = {
  id: number;
  name: string;
  isSensitive: boolean;
  isDisclosable: boolean;
  isArray: boolean;
  type: AttributeType;
  displayName: DisplayNames;
  attributeNameOverrides: AttributeNameOverrides;
};

type CredentialSchemaMetadata = {
  metaAttributes: CredentialSchemaMetadataAttribute[];
};

type CredentialSchemaMetadataAttribute = {
  attributeKey: string;
  displayName: DisplayNames;
};

export type CredentialIssuerSettings = {
  issuerKeyType: IssuerKeyTypes;
  id: number;
  issuanceProfileId: IssuanceProfileId;
  doctype: string | null | undefined;
  namespace: string | null | undefined;
  supportedCredentialTypes: SupportedCredentialTypes[];
  vct: string | null | undefined;
  defaultTrustSystem?: ConfiguredTrustSystem | null;
  signingKeyIds?: Partial<Record<TrustSystem, string | null>>;
  statusListId?: string | null;
  credentialOfferType: CredentialOfferType;
};

// For the GET /management/v1/credential-schemas/{id} endpoint
export type CredentialSchema = {
  tenantId: string | null;
  id: string;
  credentialIdentifier: string;
  version: string;
  displayName: string;
  attributes: CredentialSchemaAttribute[];
  credentialSchemeStyleDetails: CredentialSchemaStyleDetails[];
  state: CredentialSchemaState;
  schemeMetadata: CredentialSchemaMetadata;
  issuerSettings: CredentialIssuerSettings;
  maxBatchSize: number;
  templateId: string | null;
};

// For the POST and PUT /management/v1/credential-schemas endpoints
// They require a slightly different payload
export type CredentialSchemaSendPayload = Omit<
  CredentialSchema,
  | "tenantId"
  | "credentialSchemeStyleDetails"
  | "state"
  | "schemeMetadata"
  | "id"
> & {
  credentialSchemeStylePayloads: CredentialSchemaStylePayload[];
  metadata: CredentialSchemaMetadata;
  id?: string;
};

export type CreateCredentialSchemaResponse = {
  id: string;
  message: string;
};

// For the /management/v1/credential-schemas/overview endpoint
export type CredentialSchemaLite = Omit<
  CredentialSchema,
  "schemeMetadata" | "attributes" | "maxBatchSize" | "templateId"
> & {
  updatedAt: number;
  createdOn: number;
};

export type CredentialSchemaOverviewResponse = {
  credentialSchemes: CredentialSchemaLite[];
};
