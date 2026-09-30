// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

interface CertificateUsage {
  source: string;
  firstUsedAt?: string;
}

export function certificateRemovalCopy(certificate: CertificateUsage) {
  const archive = Boolean(certificate.firstUsedAt);
  if (archive && certificate.source === "DEVELOPMENT") return {
    archive,
    action: "Archive",
    title: "Archive previously used development certificate?",
    description: "The certificate remains in audit history but cannot be assigned again. Assigned certificates must be unassigned first.",
    success: "Certificate archived.",
  };
  if (archive) return {
    archive,
    action: "Archive",
    title: "Archive imported certificate?",
    description: "The certificate remains in audit history but cannot be assigned again. Assigned certificates must be unassigned first.",
    success: "Certificate archived.",
  };
  if (certificate.source === "DEVELOPMENT") return {
    archive,
    action: "Delete",
    title: "Delete development certificate?",
    description: "This permanently deletes the development certificate. Assigned certificates must be unassigned first.",
    success: "Certificate deleted.",
  };
  return {
    archive,
    action: "Delete",
    title: "Delete unused imported certificate?",
    description: "This permanently deletes the imported certificate because it has never been assigned. Assigned certificates must be unassigned first.",
    success: "Certificate deleted.",
  };
}
