// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

export const CREDENTIAL_IDENTITY = "credential";

/** Resolve the explicit identity required by identity-owned proof configuration. */
export function verifierIdentityId(
  selection: string,
  existingIdentityId?: number,
  credentialIdentityId?: number,
) {
  if (selection !== CREDENTIAL_IDENTITY) return Number(selection);

  return existingIdentityId ?? credentialIdentityId;
}

/** Map the expanded response identity back to the update contract. */
export function payloadVerifierIdentityId(
  identity?: { id: number } | null,
) {
  return identity?.id ?? null;
}
