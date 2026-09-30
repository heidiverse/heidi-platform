// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect } from "@/lib/utils";
import type { SwissVerificationQuery } from "@/types/proof-schema";
import type { ConfiguredTrustSystem } from "@/types/trust-system";

export type TrustSystem = ConfiguredTrustSystem;

export interface TrustConfiguration {
  issuerId: number;
  trustSystem: TrustSystem;
  issuerCertificateAvailable: boolean;
  eudiIssuerCertificateChains: {
    keyId: string;
    logicalKeyId: string;
    algorithm: string;
    certificateChain: string[];
  }[];
  issuerClaim?: string;
  swissDid?: string;
  swissRegistryBaseUrl?: string;
  swissStatusRegistryApiUrl?: string;
  swissStatusRegistryPartnerId?: string;
  swissTrustRegistryAuthoringUrl?: string;
  swissTrustRegistryTokenUrl?: string;
  swissTrustRegistryClientId?: string;
  swissTrustRegistryCredentialsConfigured?: boolean;
  swissIdentityStatement?: string;
  swissIdentityStatementExpiresAt?: string;
  swissIssuanceStatements: Record<string, string>;
  swissIssuanceStatementsExpiresAt?: string;
  eudiVerificationTrustAnchors: string[];
  swissVerificationTrustAnchor?: string;
  /** Also trust the organisation's Issuing PKI SubCAs. */
  eudiTrustOwnPki: boolean;
}

function identityConfigUrl(path: string, tenantId?: string) {
  const scope = tenantId ? "" : "/global";
  const url = new URL(`management/v1/identities${scope}${path}`, runtimeConfig.heidiApiBaseUrl);
  if (tenantId) url.searchParams.set("tenantId", tenantId);
  return url;
}

async function requireOk(res: Response, message: string) {
  if (res.ok) return;
  throw new Error((await res.text()) || message);
}

export async function getTrustConfiguration({
  tenantId,
  issuerId,
  trustSystem,
}: {
  tenantId?: string;
  issuerId: number;
  trustSystem: TrustSystem;
}) {
  const res = await fetchWithRedirect(
    identityConfigUrl(`/issuers/${issuerId}/trust-systems/${trustSystem}`, tenantId),
  );
  await requireOk(res, "Failed to load trust configuration");
  return (await res.json()) as TrustConfiguration;
}

export async function getSwissVerificationQueries({
  tenantId,
  issuerId,
}: {
  tenantId?: string;
  issuerId: number;
}) {
  const res = await fetchWithRedirect(
    identityConfigUrl(`/issuers/${issuerId}/trust-systems/Switzerland/vqps`, tenantId),
  );
  await requireOk(res, "Failed to load Swiss verification query statements");
  return (await res.json()) as SwissVerificationQuery[];
}

export type TrustConfigurationUpdate = Partial<TrustConfiguration> & {
  swissTrustRegistryClientSecret?: string;
  swissTrustRegistryRefreshToken?: string;
};

export async function updateTrustConfiguration({
  tenantId,
  issuerId,
  trustSystem,
  configuration,
}: {
  tenantId?: string;
  issuerId: number;
  trustSystem: TrustSystem;
  configuration: TrustConfigurationUpdate;
}) {
  const res = await fetchWithRedirect(
    identityConfigUrl(`/issuers/${issuerId}/trust-systems/${trustSystem}`, tenantId),
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(configuration),
    },
  );
  await requireOk(res, "Failed to update trust configuration");
  return (await res.json()) as TrustConfiguration;
}

export async function refreshSwissTrustConfiguration({
  tenantId,
  issuerId,
}: {
  tenantId?: string;
  issuerId: number;
}) {
  const res = await fetchWithRedirect(
    identityConfigUrl(`/issuers/${issuerId}/trust-systems/Switzerland/refresh`, tenantId),
    { method: "POST" },
  );
  await requireOk(res, "Failed to refresh Swiss trust statements");
  return (await res.json()) as TrustConfiguration;
}

export async function exportIssuerPublicKey({
  tenantId,
  issuerId,
  trustSystem,
  keyId,
  format,
}: {
  tenantId?: string;
  issuerId: number;
  trustSystem: TrustSystem;
  keyId: string;
  format: "JWK" | "PEM";
}) {
  const res = await fetchWithRedirect(
    identityConfigUrl(
      `/issuers/${issuerId}/trust-systems/${trustSystem}/keys/${encodeURIComponent(keyId)}/public-key?format=${format}`,
      tenantId,
    ),
  );
  await requireOk(res, "Failed to export public key");
  return res.blob();
}

export async function setEudiTrustCertificateChain({
  tenantId,
  issuerId,
  keyId,
  certificateChain,
}: {
  tenantId?: string;
  issuerId: number;
  keyId: string;
  certificateChain: string[];
}) {
  const res = await fetchWithRedirect(
    identityConfigUrl(
      `/issuers/${issuerId}/trust-systems/EUDI/keys/${encodeURIComponent(keyId)}/certificate-chain`,
      tenantId,
    ),
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ certificateChain }),
    },
  );
  await requireOk(res, "Certificate does not match the identity key");
  return (await res.json()) as TrustConfiguration;
}
