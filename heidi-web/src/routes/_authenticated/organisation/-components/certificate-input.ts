// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

export function certificatesFromText(value: string) {
  const pemCertificates = value.match(
    /-----BEGIN CERTIFICATE-----[\s\S]*?-----END CERTIFICATE-----/g,
  );
  if (pemCertificates) return pemCertificates.map((certificate) => certificate.trim());

  const certificate = value.replaceAll(/\s/g, "");
  return certificate ? [certificate] : [];
}

export function certificateChainFromText(leaf: string, chain: string) {
  return [...certificatesFromText(leaf), ...certificatesFromText(chain)];
}
