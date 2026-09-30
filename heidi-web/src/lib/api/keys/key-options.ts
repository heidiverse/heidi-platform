// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { IdentityKeySlotType } from "../identity-key-slots/api";
import type { PlatformKey } from "./api";

export function keyOptions(keys: PlatformKey[], type: IdentityKeySlotType) {
  return keys.flatMap((key) => {
    const active = key.versions.find((version) => version.id === key.activeVersionId);
    // Retained public material does not make a version eligible for new assignments.
    if (active?.status !== "ACTIVE") return [];
    const usages = active.usages;
    const eligible = type === "DECRYPTION"
      ? usages.includes("KEY_AGREEMENT") || usages.includes("UNWRAP")
      : usages.includes("SIGN");
    return eligible ? [{ id: key.id, label: `${key.keyId} · ${active.algorithm}` }] : [];
  });
}

export function certificateOptions(keys: PlatformKey[], profile: string, trustSystem: string, now = Date.now()) {
  return keys.flatMap((key) => {
    const version = key.versions.find((candidate) => candidate.id === key.activeVersionId);
    if (version?.status !== "ACTIVE" || !version.usages.includes("SIGN")) return [];
    return version.certificates.filter((certificate) => !certificate.retiredAt
      && certificate.profile === profile
      && certificate.trustSystem === trustSystem
      && certificate.notBefore && Date.parse(certificate.notBefore) <= now
      && certificate.notAfter && Date.parse(certificate.notAfter) > now)
      .map((certificate) => ({
        id: certificate.id, keyId: key.id,
        label: `${key.keyId} · ${certificate.eudiLeafProfile ? `${certificate.eudiLeafProfile} · ` : ""}${certificate.source} · expires ${certificate.notAfter?.slice(0, 10)} · ${certificate.id.slice(0, 8)}`,
      }));
  });
}
