// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect } from "@/lib/utils";
import type { PrivateKeyFormat } from "./import";

export type CertificateProfile = "CREDENTIAL_SIGNING" | "ACCESS" | "STATUS_LIST";
export interface KeyCertificate {
  id: string;
  profile: CertificateProfile;
  source: string;
  eudiLeafProfile?: "PID" | "EAA" | null;
  trustSystem: "Default" | "EUDI" | "Switzerland" | "Custom" | "OIDF" | null;
  chain: string[];
  notBefore?: string;
  notAfter?: string;
  firstUsedAt?: string;
  retiredAt?: string;
}

export type KeyUsage = "SIGN" | "KEY_AGREEMENT" | "UNWRAP";

export interface PlatformKeyVersion {
  previousUntil?: string;
  usages: KeyUsage[];
  id: string;
  version: number;
  algorithm: string;
  publicJwk: string;
  status: "ACTIVE" | "PREPARED" | "PREVIOUS" | "REVOKED" | string;
  certificates: KeyCertificate[];
  createdAt?: string;
}

export interface KeyRotationPolicy {
  mode: "MANUAL" | "AUTOMATIC" | "EXTERNAL";
  intervalSeconds?: number;
  gracePeriodSeconds: number;
}

export interface PlatformKey {
  rotationPolicy: KeyRotationPolicy;
  id: string;
  keyId: string;
  providerId: number;
  activeVersionId?: string;
  versions: PlatformKeyVersion[];
  createdAt?: string;
}

export interface CreatePlatformKeyRequest {
  usage?: KeyUsage;
  privateKeyFormat?: PrivateKeyFormat;
  privateKey?: string;
  privateKeyPassword?: string;
  /** Compatibility with the original JWK-only endpoint. */
  privateJwk?: string;
  keyId: string;
  algorithm: string;
  providerId?: number;
}

function keyUrl(path: string, tenantId?: string) {
  const scope = tenantId ? "" : "/global";
  const url = new URL(`management/v1/keys${scope}${path}`, runtimeConfig.heidiApiBaseUrl);
  if (tenantId) url.searchParams.set("tenantId", tenantId);
  return url;
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

export async function getKeys(tenantId?: string) {
  const res = await fetchWithRedirect(keyUrl("", tenantId));
  await requireOk(res, "Failed to load keys");
  return (await res.json()) as PlatformKey[];
}

export function keyCsrDownloadUrl(tenantId: string | undefined, keyId: string, versionId: string, subject: string, dnsName: string) {
  const url = keyUrl(`/${encodeURIComponent(keyId)}/versions/${encodeURIComponent(versionId)}/csr`, tenantId);
  url.searchParams.set("subject", subject);
  if (dnsName.trim()) url.searchParams.append("dnsName", dnsName.trim());
  return url.toString();
}

export async function endKeyVersion(tenantId: string | undefined, keyId: string, versionId: string, action: "retire" | "revoke") {
  const res = await fetchWithRedirect(keyUrl(`/${encodeURIComponent(keyId)}/versions/${encodeURIComponent(versionId)}/${action}`, tenantId), { method: "POST" });
  await requireOk(res, `Failed to ${action} key version`);
  return (await res.json()) as PlatformKey;
}

export async function createKey({
  tenantId,
  key,
}: {
  tenantId?: string;
  key: CreatePlatformKeyRequest;
}) {
  const res = await fetchWithRedirect(keyUrl("", tenantId), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(key),
  });
  await requireOk(res, "Failed to create key");
  return (await res.json()) as PlatformKey;
}

export async function prepareKeyRotation({ tenantId, keyId }: { tenantId?: string; keyId: string }) {
  const res = await fetchWithRedirect(keyUrl(`/${encodeURIComponent(keyId)}/rotate`, tenantId), {
    method: "POST",
  });
  await requireOk(res, "Failed to prepare key rotation");
  return (await res.json()) as PlatformKey;
}

export async function activateKeyVersion({
  tenantId,
  keyId,
  versionId,
  certificatesBySlot,
}: {
  tenantId?: string;
  keyId: string;
  versionId: string;
  certificatesBySlot?: Record<string, string>;
}) {
  const res = await fetchWithRedirect(
    keyUrl(`/${encodeURIComponent(keyId)}/versions/${encodeURIComponent(versionId)}/activate`, tenantId),
    { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ certificatesBySlot }) },
  );
  await requireOk(res, "Failed to activate key version");
  return (await res.json()) as PlatformKey;
}

export async function deleteKey({ tenantId, keyId }: { tenantId?: string; keyId: string }) {
  const res = await fetchWithRedirect(keyUrl(`/${encodeURIComponent(keyId)}`, tenantId), {
    method: "DELETE",
  });
  await requireOk(res, "Failed to delete key");
}

export async function keyCapabilities(_tenantId?: string) {
  const res = await fetchWithRedirect(new URL("management/v1/keys/capabilities", runtimeConfig.heidiApiBaseUrl));
  await requireOk(res, "Failed to load key capabilities");
  return (await res.json()) as { developmentCertificates: boolean };
}

export async function addKeyCertificate(args: {
  tenantId?: string; keyId: string; versionId: string;
  profile: CertificateProfile; trustSystem: string | null;
  certificateChain?: string[];
}) {
  const action = args.certificateChain ? "certificates" : "development-certificate";
  const res = await fetchWithRedirect(keyUrl(
    `/${encodeURIComponent(args.keyId)}/versions/${args.versionId}/${action}`, args.tenantId), {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ profile: args.profile, trustSystem: args.trustSystem, certificateChain: args.certificateChain }),
  });
  await requireOk(res, "Could not add certificate");
  return (await res.json()) as PlatformKey | { certificateId: string };
}

export async function removeKeyCertificate(args: {
  tenantId?: string; keyId: string; versionId: string; certificateId: string;
}) {
  const res = await fetchWithRedirect(keyUrl(
    `/${encodeURIComponent(args.keyId)}/versions/${args.versionId}/certificates/${args.certificateId}`,
    args.tenantId,
  ), { method: "DELETE" });
  await requireOk(res, "Could not remove certificate");
}

export interface ActivationSlot {
  slotId: string;
  identityId: number;
  type: string;
  trustSystem: string | null;
  profile: CertificateProfile;
  required: boolean;
  certificates: string[];
}

export async function getActivationSlots(tenantId: string | undefined, keyId: string, versionId: string) {
  const res = await fetchWithRedirect(keyUrl(
    `/${encodeURIComponent(keyId)}/versions/${versionId}/activation-slots`, tenantId));
  await requireOk(res, "Could not check activation requirements");
  return (await res.json()) as ActivationSlot[];
}

export async function updateRotationPolicy(tenantId: string | undefined, keyId: string, policy: KeyRotationPolicy) {
  const res = await fetchWithRedirect(keyUrl(`/${encodeURIComponent(keyId)}/rotation-policy`, tenantId), {
    method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(policy),
  });
  await requireOk(res, "Could not save rotation policy");
  return (await res.json()) as PlatformKey;
}
