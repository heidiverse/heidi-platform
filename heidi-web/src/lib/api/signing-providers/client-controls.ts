// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

export type SigningProviderClient = "issuer" | "verifier";
export type SigningProviderScope = "global" | "tenant";

export function clientRegistrationPath(
  providerId: number,
  client: SigningProviderClient,
  scope: SigningProviderScope,
) {
  const prefix = scope === "global" ? "/global" : "";
  return `${prefix}/${encodeURIComponent(providerId)}/register-client/${client}`;
}

export function clientConnectionPath(providerId: number, scope: SigningProviderScope) {
  const prefix = scope === "global" ? "/global" : "";
  return `${prefix}/${encodeURIComponent(providerId)}/connection`;
}
