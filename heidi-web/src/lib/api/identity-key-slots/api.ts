// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect } from "@/lib/utils";
import type { TrustSystem } from "@/types/trust-system";

export type IdentityKeySlotType =
  | "CREDENTIAL_SIGNING"
  | "IDENTITY_STATEMENT"
  | "PRESENTATION_SIGNING"
  | "DECRYPTION"
  | "OPERATION"
  | "FEDERATION"
  | "STATUS_LIST";

export type IdentityKeySlotConsumer = "ISSUER" | "VERIFIER";

export interface IdentityKeySlot {
  id: string;
  type: IdentityKeySlotType;
  trustSystem?: TrustSystem | null;
  operation?: string | null;
  keyId?: string | null;
  providerId?: number | null;
  certificateId?: string | null;
  order: number;
  updatedAt?: string;
  consumer?: IdentityKeySlotConsumer | null;
  configuration?: unknown;
}

export interface IdentityKeySlotRequest {
  id?: string;
  type: IdentityKeySlotType;
  trustSystem?: TrustSystem | null;
  operation?: string | null;
  keyId?: string | null;
  providerId?: number | null;
  certificateId?: string | null;
  order?: number;
  consumer?: IdentityKeySlotConsumer | null;
  configuration?: unknown;
}

export interface IdentityGrantStatus {
  scope: string;
  providerId: number;
  desired: Record<string, string[]>;
  confirmed?: Record<string, string[]> | null;
  pending: boolean;
}

function slotUrl(tenantId: string | undefined, identityId: number) {
  return new URL(
    tenantId
      ? `management/v1/identities/tenants/${tenantId}/${identityId}/slots`
      : `management/v1/identities/${identityId}/slots`,
    runtimeConfig.heidiApiBaseUrl,
  );
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

export async function getIdentityKeySlots({
  tenantId,
  identityId,
}: {
  tenantId?: string;
  identityId: number;
}) {
  const res = await fetchWithRedirect(slotUrl(tenantId, identityId));
  await requireOk(res, "Failed to load identity key slots");
  return (await res.json()) as IdentityKeySlot[];
}

export async function getIdentityGrants({
  tenantId,
  identityId,
}: {
  tenantId?: string;
  identityId: number;
}) {
  const url = slotUrl(tenantId, identityId);
  url.pathname = url.pathname.replace(/\/slots$/, "/grants");
  const res = await fetchWithRedirect(url);
  await requireOk(res, "Failed to load identity grants");
  return (await res.json()) as IdentityGrantStatus[];
}

export async function saveIdentityKeySlot({
  tenantId,
  identityId,
  slot,
}: {
  tenantId?: string;
  identityId: number;
  slot: IdentityKeySlotRequest;
}) {
  const res = await fetchWithRedirect(slotUrl(tenantId, identityId), {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(slot),
  });
  await requireOk(res, "Failed to save identity key slot");
  return (await res.json()) as IdentityKeySlot;
}

export async function deleteIdentityKeySlot({
  tenantId,
  identityId,
  slotId,
}: {
  tenantId?: string;
  identityId: number;
  slotId: string;
}) {
  const url = slotUrl(tenantId, identityId);
  url.pathname = `${url.pathname}/${slotId}`;
  const res = await fetchWithRedirect(
    url,
    { method: "DELETE" },
  );
  await requireOk(res, "Failed to delete identity key slot");
}
