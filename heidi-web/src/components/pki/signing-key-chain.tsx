// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { IdentityKeySlot } from "@/lib/api/identity-key-slots/api";
import type { PlatformKey } from "@/lib/api/keys/api";
import { certificateOptions } from "@/lib/api/keys/key-options";
import { CertificateChain } from "./certificate-chain";

/** Preview the same explicit assignment or key fallback used by the signing resolver. */
export function SigningKeyChain({ keys, slots, logicalKeyId, trustSystem, type }: {
  keys: PlatformKey[]; slots: IdentityKeySlot[]; logicalKeyId?: string | null;
  trustSystem: string; type: "CREDENTIAL_SIGNING" | "PRESENTATION_SIGNING";
}) {
  if (trustSystem === "Switzerland") return null;
  const matching = slots.filter((slot) => slot.type === type && (slot.trustSystem ?? "Default") === trustSystem);
  const candidates = matching.length ? matching : trustSystem === "Custom" ? slots.filter((slot) => slot.type === type && (slot.trustSystem ?? "Default") === "Default") : [];
  const slot = candidates.find((slot) => !logicalKeyId || keys.find((key) => key.id === slot.keyId)?.keyId === logicalKeyId);
  const key = keys.find((key) => key.id === slot?.keyId);
  const version = key?.versions.find((version) => version.id === key.activeVersionId);
  if (!key || !version) return null;
  const purpose = trustSystem === "EUDI" && type === "PRESENTATION_SIGNING" ? "ACCESS" : "CREDENTIAL_SIGNING";
  const options = certificateOptions([key], purpose, trustSystem);
  const selectedId = slot?.certificateId ?? (options.length === 1 ? options[0]?.id : undefined);
  const certificate = version.certificates.find((certificate) => certificate.id === selectedId);
  if (certificate) return <CertificateChain chain={certificate.chain} anchor={certificate.anchor} details={certificate.chainDetails} />;
  if (options.length > 1) return <p className="text-sm text-destructive">Choose a chain in this identity’s signing key assignment.</p>;
  return null;
}
