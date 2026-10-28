// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

const PEM_LINE_LENGTH = 64;

/** Preserve the stored order: selected certificate first, root last. */
export function downloadPem(chain: string[], filename: string) {
  const pem = chain.map((certificate) => {
    const lines = [];
    for (let offset = 0; offset < certificate.length; offset += PEM_LINE_LENGTH) {
      lines.push(certificate.slice(offset, offset + PEM_LINE_LENGTH));
    }
    return `-----BEGIN CERTIFICATE-----\n${lines.join("\n")}\n-----END CERTIFICATE-----\n`;
  }).join("");
  const url = URL.createObjectURL(new Blob([pem], { type: "application/x-pem-file" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
