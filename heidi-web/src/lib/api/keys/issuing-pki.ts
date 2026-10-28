// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect } from "@/lib/utils";
import type { CertificateAnchor, CertificateDetail, CertificateProfile } from "./api";

export interface IssuingSubca {
  id: string;
  tenantId: string;
  keyId: string;
  keyVersionId: string;
  subjectDn: string;
  certificateChain: string[];
  certificateHistory: string[];
  certificateSource: "SELF_SIGNED" | "OFFLINE_ROOT" | "PLATFORM_CA" | null;
  createdAt: string;
  parentCaId?: string | null;
  anchor?: CertificateAnchor;
  chainDetails?: CertificateDetail[];
}

function url(path: string, tenantId?: string) {
  const target = new URL(`management/v1/issuing-pki${path}`, runtimeConfig.heidiApiBaseUrl);
  if (tenantId) target.searchParams.set("tenantId", tenantId);
  return target;
}

async function checked(response: Response) {
  if (!response.ok) throw new Error(await response.text());
  return response;
}

export async function getSubcas(tenantId?: string): Promise<IssuingSubca[]> {
  return (await checked(await fetchWithRedirect(url("", tenantId))).then((res) => res.json())) as IssuingSubca[];
}

export async function deleteCaChain(tenantId: string, id: string) {
  return checked(await fetchWithRedirect(url(`/${id}`, tenantId), { method: "DELETE" }));
}

export async function createSubca(tenantId: string | undefined, subjectDn: string,
  algorithm: string, providerId?: number, parentCaId?: string): Promise<IssuingSubca> {
  return (await checked(await fetchWithRedirect(url("", tenantId), {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ subjectDn, algorithm, providerId, parentCaId }),
  })).then((res) => res.json())) as IssuingSubca;
}

export function subcaCsrUrl(tenantId: string | undefined, id: string) {
  return url(`/${id}/csr`, tenantId).toString();
}

export interface RootCertificateOptions {
  pathLengthMode: "LIMITED" | "UNLIMITED";
  pathLength: number | null;
}

export async function selfSignSubca(tenantId: string | undefined, id: string, options: RootCertificateOptions) {
  return checked(await fetchWithRedirect(url(`/${id}/self-sign`, tenantId), {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(options),
  }));
}

export async function importSubcaChain(tenantId: string | undefined, id: string, certificateChain: string[]) {
  return checked(await fetchWithRedirect(url(`/${id}/certificate-chain`, tenantId), {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ certificateChain }),
  }));
}

export async function issueSubcaLeaf(tenantId: string | undefined, id: string, keyId: string,
  versionId: string, subjectDn: string, profile: LeafTemplate, purpose: CertificateProfile = "CREDENTIAL_SIGNING", csr?: string) {
  return checked(await fetchWithRedirect(url(`/${id}/leaves`, tenantId), {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ keyId, versionId, subjectDn, profile, purpose, csr }),
  }));
}

export type LeafTemplate = "GENERIC" | "PID" | "EAA";

export async function certifySubca(tenantId: string | undefined, id: string) {
  return checked(await fetchWithRedirect(url(`/${id}/certify`, tenantId), { method: "POST" }));
}
