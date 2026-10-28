// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconDownload } from "@tabler/icons-react";
import { Button } from "@/components/ui/button";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import type { KeyCertificate } from "@/lib/api/keys/api";
import { downloadPem } from "@/lib/certificate-pem";

/** Export the certificate's saved path, including any historical CA certificates. */
export function CertificateDownload({ certificate }: { certificate: KeyCertificate }) {
  return <DropdownMenu>
    <DropdownMenuTrigger asChild>
      <Button variant="ghost" size="icon" className="size-8 shrink-0" disabled={!certificate.chain.length}
        aria-label={`Download certificate ${certificate.chainDetails?.[0]?.subjectDn ?? certificate.id}`}>
        <IconDownload />
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuContent align="end">
      <DropdownMenuItem onSelect={() => downloadPem(certificate.chain.slice(0, 1), `certificate-${certificate.id}.pem`)}>Leaf certificate (.pem)</DropdownMenuItem>
      <DropdownMenuItem onSelect={() => downloadPem(certificate.chain, `chain-${certificate.id}.pem`)}>Full chain including leaf (.pem)</DropdownMenuItem>
    </DropdownMenuContent>
  </DropdownMenu>;
}
