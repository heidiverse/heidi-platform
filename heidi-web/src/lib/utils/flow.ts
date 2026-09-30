// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

export function generateCredentialNodeHandleId(attribute: {
  name: string;
  type: string;
}) {
  return `${attribute.name},${attribute.type}`;
}

export function generateProofNodeHandleId({
  credentialIdentifier,
  attribute,
}: {
  credentialIdentifier: string;
  attribute: { name: string; type: string };
}) {
  return `${credentialIdentifier},${attribute.name},${attribute.type}`;
}

export function getCredentialNodeHandleParts(
  handle: undefined | null | string,
) {
  const [attributeName, attributeType] = handle?.split(",") ?? [];
  return {
    attributeName,
    attributeType,
  };
}
export function getProofNodeHandleParts(handle: undefined | null | string) {
  const [credentialIdentifier, attributeName, attributeType] =
    handle?.split(",") ?? [];
  return {
    credentialIdentifier,
    attributeName,
    attributeType,
  };
}
