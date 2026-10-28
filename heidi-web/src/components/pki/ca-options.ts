// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { IssuingSubca, RootCertificateOptions } from "../../lib/api/keys/issuing-pki";

export interface RootPathChoice {
  mode: "" | RootCertificateOptions["pathLengthMode"];
  limit: string;
}

export const MAX_PATH_LENGTH = 2_147_483_647;

export function rootPathOptions(choice: RootPathChoice): RootCertificateOptions | undefined {
  if (choice.mode === "UNLIMITED") return { pathLengthMode: choice.mode, pathLength: null };
  if (choice.mode !== "LIMITED" || !/^\d+$/.test(choice.limit)) return undefined;

  const limit = Number(choice.limit);
  if (!Number.isSafeInteger(limit) || limit > MAX_PATH_LENGTH) return undefined;
  return { pathLengthMode: choice.mode, pathLength: limit };
}

/** Every ancestor can restrict the number of further intermediate levels. */
export function remainingDepth(ca: IssuingSubca): number | null | undefined {
  if (!ca.certificateChain.length || ca.chainDetails?.length !== ca.certificateChain.length) return undefined;
  let remaining: number | null = null;
  for (const [index, certificate] of ca.chainDetails.entries()) {
    if (certificate.pathLength === undefined) return undefined;
    if (certificate.pathLength === null) continue;
    const available = Math.max(0, certificate.pathLength - index);
    remaining = remaining === null ? available : Math.min(remaining, available);
  }
  return remaining;
}

export function depthLabel(ca: IssuingSubca) {
  const depth = remainingDepth(ca);
  if (depth === undefined) return "Path limit unavailable";
  if (depth === null) return "Unlimited intermediate levels";
  if (depth === 0) return "No further intermediates";
  return `${depth} intermediate ${depth === 1 ? "level" : "levels"} remaining`;
}

export function canAddIntermediate(ca: IssuingSubca) {
  const depth = remainingDepth(ca);
  return depth === null || (depth !== undefined && depth > 0);
}

export function isTerminalCa(ca: IssuingSubca, cas: IssuingSubca[]) {
  return !cas.some((child) => child.parentCaId === ca.id);
}
