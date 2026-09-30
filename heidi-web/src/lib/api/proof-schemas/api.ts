// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { IssuerDefinition } from "@/lib/api/issuer-definitions/api";
import { payloadVerifierIdentityId } from "@/lib/api/proof-schemas/verifier-identity";
import { jotaiStore, selectedTenantAtom } from "@/lib/atoms";
import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect } from "@/lib/utils";
import type { DisplayNames } from "@/types";
import type {
  AttributeType,
  CredentialIssuerSettings,
} from "@/types/credential-schema";
import type { PresentationProfileId } from "@/types/ecosystem-profile";
import type {
  TrustedAuthorityQuery,
  VerifierInfo,
} from "@/types/proof-schema";
import type { TrustSystem } from "@/types/trust-system";

export async function getProofSchemas({
  isPublic = false,
  credentialIdentifiers = [],
}: {
  isPublic?: boolean;
  credentialIdentifiers?: string[];
}) {
  const url = new URL(
    isPublic
      ? "public/v1/proofscheme/overview"
      : "management/v1/proof-schemas/overview",
    runtimeConfig.heidiApiBaseUrl,
  );
  if (credentialIdentifiers.length > 0) {
    url.searchParams.set(
      "credentialIdentifiers",
      credentialIdentifiers.join(","),
    );
  }
  const res = await fetchWithRedirect(url);
  if (!res.ok)
    throw new Error(`Network Response was not ok! ${res.statusText}`);
  const { proofSchemeDetails } = (await res.json()) as ProofSchemaResponse;
  return proofSchemeDetails.sort((a, b) => a.title.localeCompare(b.title));
}

export async function getProofSchemaById({
  schemaId,
  isPublic = false,
}: {
  schemaId: string;
  isPublic?: boolean;
}) {
  const res = await fetchWithRedirect(
    new URL(
      isPublic
        ? `public/v1/proofscheme/${schemaId}`
        : `management/v1/proof-schemas/${schemaId}`,
      runtimeConfig.heidiApiBaseUrl,
    ),
  );
  if (!res.ok)
    throw new Error(`Network Response was not ok! ${res.statusText}`);
  return (await res.json()) as ProofSchema;
}

export async function createProofSchema(
  schemaDetails: Omit<ProofSchemaCreateUpdate, "createdAt" | "updatedAt">,
) {
  const url = new URL(
    "management/v1/proof-schemas/create",
    runtimeConfig.heidiApiBaseUrl,
  );
  const selectedTenant = jotaiStore.get(selectedTenantAtom);
  selectedTenant && url.searchParams.set("tenantId", selectedTenant);
  const res = await fetchWithRedirect(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(schemaDetails),
  });
  if (!res.ok) {
    throw new Error(`Network Response was not ok! ${res.statusText}`);
  }
  const { id } = (await res.json()) as { id: number };
  return { id };
}

export async function updateProofSchema({
  uuid,
  tenantId,
  createdAt,
  updatedAt,
  verifierIdentity,
  verifierClientIdScheme: _verifierClientIdScheme,
  verifierClientIdSchemeOverridden: _verifierClientIdSchemeOverridden,
  verifierTrustSystem: _verifierTrustSystem,
  ...schemaDetails
}: ProofSchemaCreateUpdate & { uuid: string }) {
  const res = await fetchWithRedirect(
    new URL(
      `management/v1/proof-schemas/update/${uuid}`,
      runtimeConfig.heidiApiBaseUrl,
    ),
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        ...schemaDetails,
        verifierIdentityId: payloadVerifierIdentityId(verifierIdentity),
      }),
    },
  );
  if (!res.ok) {
    throw new Error(`Network Response was not ok! ${res.statusText}`);
  }
  return { schemaId: uuid };
}

export async function archiveProofSchema({
  proofSchemaId,
}: {
  proofSchemaId: string;
}) {
  const res = await fetchWithRedirect(
    new URL(
      `management/v1/proof-schemas/archive/${proofSchemaId}`,
      runtimeConfig.heidiApiBaseUrl,
    ),
    { method: "PUT" },
  );
  if (!res.ok) {
    throw new Error(`Network Response was not ok! ${res.statusText}`);
  }
}

export async function publishProofSchemaToTrustRegistry({
  proofSchemaId,
}: {
  proofSchemaId: string;
}) {
  const res = await fetchWithRedirect(
    new URL(
      `management/v1/proof-schemas/publish/${proofSchemaId}/trust-registry/de`,
      runtimeConfig.heidiApiBaseUrl,
    ),
    { method: "PUT" },
  );
  if (!res.ok) {
    throw new Error(
      `Failed to publish proof schema to trust registry! ${res.statusText}`,
    );
  }
}

type ProofSchema = {
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
    }[];
  }[];
};

type ProofSchemaResponse = {
  proofSchemeDetails: ProofSchema[];
};

type ProofSchemaCreateUpdate = Omit<
  ProofSchema,
  "uuid" | "credentialSchemes"
> & {
  credentialSchemes: { id: string; attributes: number[] }[];
};
