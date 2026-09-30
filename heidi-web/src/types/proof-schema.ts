// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { IssuerDefinition } from "@/lib/api/issuer-definitions/api";
import type { DisplayNames } from "@/types";
import type {
  AttributeNameOverrides,
  AttributeType,
  CredentialIssuerSettings,
} from "@/types/credential-schema";
import type { PresentationProfileId } from "@/types/ecosystem-profile";
import type { TrustSystem } from "@/types/trust-system";

export type TrustedAuthorityType =
  "aki" | "etsi_tl" | "openid_federation" | (string & {});

export type TrustedAuthorityQuery = {
  type: TrustedAuthorityType;
  values: string[];
  credentialId?: string | null;
};

export type VerifierInfo = {
  format: string;
  data: string;
  credentialIds?: string[] | null;
};

export type SwissVerificationQuery = {
  id?: string | null;
  version?: number | null;
  status?: string | null;
  purposeName?: string | null;
  purposeDescription?: string | null;
  scope?: string | null;
  query?: Record<string, unknown> | null;
  jwt?: string | null;
  expiresAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type ProofSchema = {
  tenantId?: string | null;
  uuid: string;
  title: string;
  purpose: string;
  validationLogic: string;
  validationMode?: "DISABLED" | "ENABLED" | "ENFORCED";
  createdAt: number;
  updatedAt: number;
  redirectUri: null | string;
  verifierIdentity?: IssuerDefinition | null;
  verifierIdentityOverridden?: boolean;
  presentationProfileId: PresentationProfileId;
  verifierSigningKeyId?: string | null;
  proofSigningProviderId?: number | null;
  verifierTrustSystem?: TrustSystem | null;
  verifierClientIdScheme?:
    | "x509_san_dns"
    | "x509_hash"
    | "decentralized_identifier"
    | "openid_federation"
    | null;
  verifierClientIdSchemeOverridden?: boolean;
  registrationCertificate?: string | null;
  swissIdentityStatement?: string | null;
  swissVerificationQueryStatement?: string | null;
  swissVerificationQueryEnabled?: boolean;
  alwaysIncludeDcqlQuery?: boolean;
  swissProtectedVerificationStatements?: string[];
  trustedAuthorities?: TrustedAuthorityQuery[];
  verifierInfos?: VerifierInfo[];
  credentialSchemes: {
    id: string;
    credentialIdentifier: string;
    version: string;
    displayName: string;
    issuerDefinition?: IssuerDefinition;
    issuerSettings: CredentialIssuerSettings;
    attributes: {
      id: number;
      name: string;
      type: AttributeType;
      displayName: DisplayNames;
      attributeNameOverrides?: AttributeNameOverrides;
    }[];
  }[];
};
