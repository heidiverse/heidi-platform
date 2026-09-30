// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect } from "@/lib/utils";
import {
  clientConnectionPath,
  clientRegistrationPath,
  type SigningProviderClient,
  type SigningProviderScope,
} from "./client-controls";

export interface SigningProvider {
  id: number;
  name: string;
  scheme: string | null;
  endpoint?: string;
  authenticationMode: string;
  defaultProvider: boolean;
  supportedAlgorithms: string[];
  digestSigningAlgorithms: string[];
  supportedOperations: string[];
  keylessOperations: string[];
  canCreate: boolean;
  canImport: boolean;
  canDelete: boolean;
  scope: "global" | "tenant";
  tenantId?: string;
}

export interface CreateSigningProviderRequest {
  name: string;
  endpoint?: string;
  bearerToken?: string;
  defaultProvider?: boolean;
  authenticationMode?: string;
}

export interface SigningClientStatus {
  name: string;
  /** Whether the signing service already accepts this client. */
  known: boolean;
  /** Whether the client completed a registered-key handshake. */
  registered: boolean;
  /** Last authenticated request, when available. */
  lastUse?: string | null;
  /** Public key to add to the signing service; only present for a client it does not know. */
  publicKey?: string;
}

export interface SigningProviderConnection {
  scheme: string;
  endpoint: string;
  authenticationMode: string;
  supportedAlgorithms: string[];
  digestSigningAlgorithms: string[];
  supportedOperations: string[];
  keylessOperations: string[];
  canCreate: boolean;
  canImport: boolean;
  canDelete: boolean;
  clientAcceptance?: string | null;
  clients?: SigningClientStatus[];
}

function providerUrl(path: string, tenantId?: string, scoped = true) {
  const scope = !tenantId && scoped ? "/global" : "";
  const url = new URL(
    `management/v1/signing-providers${scope}${path}`,
    runtimeConfig.heidiApiBaseUrl,
  );
  if (tenantId) url.searchParams.set("tenantId", tenantId);
  return url;
}

async function requireOk(res: Response, message: string) {
  if (!res.ok) throw new Error((await res.text()) || message);
}

export async function getSigningProviders(tenantId?: string) {
  const res = await fetchWithRedirect(providerUrl("", tenantId));
  await requireOk(res, "Failed to load signing providers");
  return (await res.json()) as SigningProvider[];
}

export async function createSigningProvider({
  tenantId,
  provider,
}: {
  tenantId?: string;
  provider: CreateSigningProviderRequest;
}) {
  const res = await fetchWithRedirect(providerUrl("", tenantId), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(provider),
  });
  await requireOk(res, "Failed to create signing provider");
  return (await res.json()) as SigningProvider;
}

export async function checkSigningProviderConnection({
  tenantId,
  provider,
}: {
  tenantId?: string;
  provider: CreateSigningProviderRequest;
}) {
  const res = await fetchWithRedirect(providerUrl("/check-connection", tenantId, false), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(provider),
  });
  await requireOk(res, "Failed to check signing provider connection");
  return (await res.json()) as SigningProviderConnection;
}

export async function registerSigningProviderClient({
  tenantId,
  provider,
  client,
}: {
  tenantId?: string;
  provider: CreateSigningProviderRequest;
  client: "issuer" | "verifier";
}) {
  const res = await fetchWithRedirect(providerUrl(`/register-client/${client}`, tenantId, false), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(provider),
  });
  await requireOk(res, "Failed to register signing client");
  return (await res.json()) as SigningProviderConnection;
}

export async function registerProviderClient({
  tenantId,
  providerId,
  client,
  scope,
}: {
  tenantId?: string;
  providerId: number;
  client: SigningProviderClient;
  scope: SigningProviderScope;
}) {
  const path = clientRegistrationPath(providerId, client, scope);
  const res = await fetchWithRedirect(providerUrl(path, scope === "global" ? undefined : tenantId, false), {
    method: "POST",
  });
  await requireOk(res, "Failed to register signing client");
  return (await res.json()) as SigningProviderConnection;
}

export async function getSigningProviderConnection({
  tenantId,
  providerId,
  scope,
}: {
  tenantId?: string;
  providerId: number;
  scope: SigningProviderScope;
}) {
  const path = clientConnectionPath(providerId, scope);
  const res = await fetchWithRedirect(providerUrl(path, scope === "global" ? undefined : tenantId, false));
  await requireOk(res, "Failed to load signing provider client status");
  return (await res.json()) as SigningProviderConnection;
}

export async function refreshSigningProvider({
  tenantId,
  providerId,
}: {
  tenantId?: string;
  providerId: number;
}) {
  const res = await fetchWithRedirect(providerUrl(`/${providerId}/refresh`, tenantId), {
    method: "POST",
  });
  await requireOk(res, "Failed to refresh signing provider capabilities");
  return (await res.json()) as SigningProvider;
}

export async function deleteSigningProvider({
  tenantId,
  providerId,
}: {
  tenantId?: string;
  providerId: number;
}) {
  const res = await fetchWithRedirect(providerUrl(`/${providerId}`, tenantId), {
    method: "DELETE",
  });
  await requireOk(res, "Failed to delete signing provider");
}
