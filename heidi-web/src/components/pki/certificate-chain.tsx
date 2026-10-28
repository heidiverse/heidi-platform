// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { CertificateAnchor, CertificateDetail } from "@/lib/api/keys/api";

export function anchorLabel(anchor?: CertificateAnchor) {
  if (anchor?.origin === "PLATFORM") return "Platform-generated CA";
  if (anchor?.origin === "EXTERNAL") return "External CA";
  return "Unknown anchor · incomplete or unidentified chain";
}

/** Show the stored path, including the anchor omitted from EUDI signing output. */
export function CertificateChain({ chain, anchor, details, endLabel = "Leaf" }: {
  chain: string[]; anchor?: CertificateAnchor; details?: CertificateDetail[]; endLabel?: string;
}) {
  return <div className="space-y-2 rounded-md border p-3 text-sm">
    <p className="font-medium">Certificate chain · {anchorLabel(anchor)}</p>
    <ol className="space-y-2">
      {[...chain].reverse().map((certificate, index) => {
        const detail = details?.[chain.length - index - 1];
        return <li key={detail?.fingerprint ?? certificate} className="break-all border-l-2 pl-3">
          <p>{index === chain.length - 1 ? endLabel : index === 0 ? "Anchor" : "Intermediate"} · {detail?.subjectDn ?? `Certificate ${chain.length - index}`}</p>
          {detail && <p className="font-mono text-xs text-muted-foreground">SHA-256 {detail.fingerprint}</p>}
        </li>;
      })}
    </ol>
    {anchor?.subjectDn && <p className="break-all text-muted-foreground">Anchor: {anchor.subjectDn}</p>}
  </div>;
}
