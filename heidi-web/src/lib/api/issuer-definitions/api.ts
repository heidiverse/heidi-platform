// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import slugify from "slugify";
import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect } from "@/lib/utils";
import {
  type ConfiguredTrustSystem,
  normalizeConfiguredTrustSystem,
  type TrustSystem,
} from "@/types/trust-system";

interface RawIssuerDefinition extends Omit<
  IssuerDefinition,
  "defaultTrustSystem" | "trustSystems"
> {
  defaultTrustSystem?: TrustSystem | null;
  trustSystems?: TrustSystem[];
}

function normalizeIssuerDefinition(
  issuer: RawIssuerDefinition,
): IssuerDefinition {
  return {
    ...issuer,
    defaultTrustSystem: normalizeConfiguredTrustSystem(
      issuer.defaultTrustSystem,
    ),
    trustSystems: (issuer.trustSystems ?? []).filter(
      (trustSystem): trustSystem is ConfiguredTrustSystem =>
        normalizeConfiguredTrustSystem(trustSystem) !== undefined,
    ),
  };
}

async function requireOk(res: Response, message: string) {
  if (res.ok) return;

  const body = await res.text();
  let detail: unknown;
  try {
    detail = (JSON.parse(body) as { detail?: unknown }).detail;
  } catch {
    // Non-problem responses still provide useful plain text below.
  }
  throw new Error(typeof detail === "string" && detail ? detail : body || message);
}

export async function getIssuerDefinitionOverview({
  isPublic = false,
  tenantId,
}: {
  isPublic?: boolean;
  tenantId?: string;
}) {
  const url = new URL(
    isPublic ? "public/v1/identity/overview" : "management/v1/identities/overview",
    runtimeConfig.heidiApiBaseUrl,
  );
  if (tenantId && !isPublic) url.searchParams.set("tenantId", tenantId);
  const res = await fetchWithRedirect(url);
  if (!res.ok) {
    throw new Error("Network Response was not ok");
  }

  const overview = (await res.json()) as {
    issuerDefinitions: RawIssuerDefinition[];
  };

  return overview.issuerDefinitions.map(normalizeIssuerDefinition);
}

export async function getIssuerDefinition({
  id,
}: {
  id: IssuerDefinition["id"];
}) {
  const res = await fetchWithRedirect(
    new URL(`management/v1/identities/${id}`, runtimeConfig.heidiApiBaseUrl),
  );
  if (!res.ok) {
    throw new Error("Network Response was not ok");
  }
  const issuer = (await res.json()) as RawIssuerDefinition;
  return normalizeIssuerDefinition(issuer);
}

export async function createIssuerDefinition({
  tenantId,
  ...data
}: Omit<IssuerDefinition, "id" | "tenantId"> & { tenantId?: string }) {
  const displayName = Object.values(data.displayName).find((name) => name.trim());
  data.slug = slugify(displayName ?? "", {
    strict: true,
    lower: true,
    locale: "de",
  });
  const res = await fetchWithRedirect(
    new URL(
      tenantId
        ? `management/v1/identities/tenants/${tenantId}`
        : "management/v1/identities",
      runtimeConfig.heidiApiBaseUrl,
    ),
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    },
  );
  await requireOk(res, "Failed to create identity");
  return normalizeIssuerDefinition((await res.json()) as RawIssuerDefinition);
}

export async function updateIssuerDefinition({
  id,
  ...data
}: IssuerDefinition) {
  const { tenantId, ...request } = data;
  const res = await fetchWithRedirect(
    new URL(
      tenantId
        ? `management/v1/identities/tenants/${tenantId}/${id}`
        : `management/v1/identities/${id}`,
      runtimeConfig.heidiApiBaseUrl,
    ),
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    },
  );
  await requireOk(res, "Failed to update identity");
}

export async function deleteIssuerDefinition({
  id,
  tenantId,
}: Pick<IssuerDefinition, "id" | "tenantId">) {
  const res = await fetchWithRedirect(
    new URL(
      tenantId
        ? `management/v1/identities/tenants/${tenantId}/${id}`
        : `management/v1/identities/${id}`,
      runtimeConfig.heidiApiBaseUrl,
    ),
    { method: "DELETE" },
  );
  await requireOk(res, "Failed to delete identity");
}

export interface IssuerDefinition {
  id: number;
  logo: string;
  slug: string;
  displayName: DisplayName;
  tenantId?: string | null;
  defaultTrustSystem?: ConfiguredTrustSystem | null;
  trustSystems?: ConfiguredTrustSystem[];
  /** Optional display-only label for this identity's custom ecosystem profile. */
  customProfileName?: string | null;
}
export type DisplayName = Record<string, string>;

export interface CredentialEncryptionPolicy {
  requestAlgValues: string[];
  requestEncValues: string[];
  requestZipValues: string[];
  responseAlgValues: string[];
  responseEncValues: string[];
  responseZipValues: string[];
  requestEncryptionRequired: boolean | null;
  responseEncryptionRequired: boolean | null;
  /** Resolved public keys currently available for decrypting Credential Requests. */
  requestKeys?: CredentialEncryptionKey[];
}

export interface CredentialEncryptionKey {
  keyId: string;
  keyUri: string;
  algorithm: string;
  /** Omitted/null means the key applies to every trust framework. */
  trustSystem?: ConfiguredTrustSystem | null;
}

export interface SupportedCredentialEncryption {
  algValues: string[];
  encValues: string[];
  zipValues: string[];
}

export async function getSupportedCredentialEncryption() {
  const res = await fetchWithRedirect(
    new URL(
      "management/v1/identities/credential-encryption/supported",
      runtimeConfig.heidiApiBaseUrl,
    ),
  );
  if (!res.ok) throw new Error("Failed to load supported encryption parameters");
  return (await res.json()) as SupportedCredentialEncryption;
}

function credentialEncryptionUrl(tenantId: string | undefined, issuerId: number) {
  return new URL(
    tenantId
      ? `management/v1/identities/tenants/${tenantId}/${issuerId}/credential-encryption`
      : `management/v1/identities/${issuerId}/credential-encryption`,
    runtimeConfig.heidiApiBaseUrl,
  );
}

export async function getCredentialEncryption({
  tenantId,
  issuerId,
}: {
  tenantId?: string;
  issuerId: number;
}) {
  const res = await fetchWithRedirect(credentialEncryptionUrl(tenantId, issuerId));
  if (!res.ok) throw new Error("Failed to load the encryption policy");
  return (await res.json()) as CredentialEncryptionPolicy;
}

export async function updateCredentialEncryption({
  tenantId,
  issuerId,
  policy,
}: {
  tenantId?: string;
  issuerId: number;
  policy: CredentialEncryptionPolicy;
}) {
  const res = await fetchWithRedirect(credentialEncryptionUrl(tenantId, issuerId), {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(policy),
  });
  if (!res.ok) throw new Error((await res.text()) || "Failed to save the encryption policy");
  return (await res.json()) as CredentialEncryptionPolicy;
}

export interface IdentityFederation {
  signingKeyId: string | null;
  authorityHints: string[];
  authority: boolean;
  subordinateIds: number[];
  /** Null keeps the legacy/default behavior of publishing every eligible scheme. */
  credentialSchemeIds?: number[] | null;
}

export interface FederationCredentialIssuer {
  id: number;
  credentialIdentifier: string;
  version: string;
  displayName?: string | null;
  state: string;
  entityId?: string | null;
  eligible: boolean;
  enabled: boolean;
}

export interface FederationSubordinateCandidate {
  id: number;
  slug: string;
  displayName: IssuerDefinition["displayName"];
  hintsAuthority: boolean;
}

export interface IdentityFederationDetails {
  settings: IdentityFederation;
  entityIds: string[];
  authorityEntityId: string | null;
  subordinateCandidates: FederationSubordinateCandidate[];
  credentialIssuers: FederationCredentialIssuer[];
}

export interface FederationAuthority {
  entityId: string;
  slug: string;
  displayName: IssuerDefinition["displayName"];
}

function federationUrl(tenantId: string | undefined, issuerId: number) {
  return new URL(
    tenantId
      ? `management/v1/identities/tenants/${tenantId}/${issuerId}/federation`
      : `management/v1/identities/${issuerId}/federation`,
    runtimeConfig.heidiApiBaseUrl,
  );
}

export async function getIdentityFederation({
  tenantId,
  issuerId,
}: {
  tenantId?: string;
  issuerId: number;
}) {
  const res = await fetchWithRedirect(federationUrl(tenantId, issuerId));
  if (!res.ok) throw new Error("Failed to load the federation settings");
  return (await res.json()) as IdentityFederationDetails;
}

export async function updateIdentityFederation({
  tenantId,
  issuerId,
  settings,
}: {
  tenantId?: string;
  issuerId: number;
  settings: IdentityFederation;
}) {
  const res = await fetchWithRedirect(federationUrl(tenantId, issuerId), {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(settings),
  });
  if (!res.ok) throw new Error((await res.text()) || "Failed to save the federation settings");
  return (await res.json()) as IdentityFederationDetails;
}

export async function getFederationAuthorities() {
  const res = await fetchWithRedirect(
    new URL("management/v1/identities/federation/authorities", runtimeConfig.heidiApiBaseUrl),
  );
  if (!res.ok) throw new Error("Failed to load federation authorities");
  return (await res.json()) as FederationAuthority[];
}
