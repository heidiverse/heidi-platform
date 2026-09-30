// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconPlus, IconTrash } from "@tabler/icons-react";
import { useRef } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import type { SelectedCredentialSchema } from "@/routes/_authenticated/proof-schemas/$proofSchemaId";
import type { TrustedAuthorityQuery } from "@/types/proof-schema";

const authorityTypes = [
  { value: "aki", label: "Authority Key Identifier (aki)" },
  { value: "etsi_tl", label: "ETSI Trusted List (etsi_tl)" },
  { value: "openid_federation", label: "OpenID Federation" },
] as const;

const credentialFormats = [
  { type: "SD_JWT", suffix: "_dc__sd-jwt", label: "SD-JWT VC" },
  { type: "MSO_MDOC", suffix: "_mso_mdoc", label: "mDoc" },
  { type: "ZKP_VC", suffix: "_bbs-termwise", label: "BBS" },
  { type: "W3C_VCDM", suffix: "_w3c-vcdm", label: "W3C VCDM" },
  { type: "OPENBADGES", suffix: "_open-badges", label: "Open Badges" },
] as const;

function credentialQueryOptions(schemas: SelectedCredentialSchema[]) {
  return schemas.flatMap((schema) => {
    const supportedTypes = schema.issuerSettings?.supportedCredentialTypes;
    return credentialFormats
      .filter(
        ({ type }) =>
          !supportedTypes?.length || supportedTypes.some((item) => item === type),
      )
      .map(({ suffix, label }) => ({
        id: `${schema.credentialIdentifier}${suffix}`,
        label: `${schema.displayName || schema.credentialIdentifier} · ${label}`,
      }));
  });
}

export function TrustedAuthorities({
  value,
  credentialSchemas,
  onChange,
}: {
  value: TrustedAuthorityQuery[];
  credentialSchemas: SelectedCredentialSchema[];
  onChange: (value: TrustedAuthorityQuery[]) => void;
}) {
  const rowKeys = useRef<string[]>([]);
  const credentialOptions = credentialQueryOptions(credentialSchemas);

  function rowKey(index: number) {
    rowKeys.current[index] ??= crypto.randomUUID();
    return rowKeys.current[index];
  }

  function update(index: number, authority: TrustedAuthorityQuery) {
    onChange(value.map((current, currentIndex) =>
      currentIndex === index ? authority : current,
    ));
  }

  return (
    <div className="mt-8 space-y-4">
      <div>
        <h4 className="font-semibold">Trusted authorities</h4>
        <p className="text-muted-foreground text-sm">
          Help the wallet select credentials from accepted issuers or trust
          frameworks. Leave the credential ID empty to apply an authority to
          every credential query.
        </p>
      </div>
      {value.map((authority, index) => {
        const isStandard = authorityTypes.some(
          ({ value: type }) => type === authority.type,
        );
        return (
          <div
            className="space-y-4 rounded-3xl border bg-background p-4"
            key={rowKey(index)}
          >
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1">
                <Label>Type</Label>
                <Select
                  value={isStandard ? authority.type : "custom"}
                  onValueChange={(type) =>
                    update(index, {
                      ...authority,
                      type: type === "custom" ? "" : type,
                    })
                  }
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select an authority type" />
                  </SelectTrigger>
                  <SelectContent>
                    {authorityTypes.map(({ value: type, label }) => (
                      <SelectItem key={type} value={type}>
                        {label}
                      </SelectItem>
                    ))}
                    <SelectItem value="custom">Custom</SelectItem>
                  </SelectContent>
                </Select>
                {!isStandard && (
                  <Input
                    aria-label="Custom trusted authority type"
                    value={authority.type}
                    placeholder="e.g. did"
                    onChange={(event) =>
                      update(index, { ...authority, type: event.target.value })
                    }
                  />
                )}
              </div>
              <div className="space-y-1">
                <Label>Credential ID (optional)</Label>
                <Select
                  value={authority.credentialId || "all"}
                  onValueChange={(credentialId) =>
                    update(index, {
                      ...authority,
                      credentialId: credentialId === "all" ? null : credentialId,
                    })
                  }
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All credential queries</SelectItem>
                    {credentialOptions.map(({ id, label }) => (
                      <SelectItem key={id} value={id}>
                        {label} ({id})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="space-y-1">
              <Label htmlFor={`trusted-authority-values-${index}`}>
                Values
              </Label>
              <p className="text-muted-foreground text-sm">
                One value per line. Custom types can use profile-specific values,
                such as issuer DIDs.
              </p>
              <Textarea
                id={`trusted-authority-values-${index}`}
                value={authority.values.join("\n")}
                placeholder={authority.type === "aki"
                  ? "s9tIpPmhxdiuNkHMEWNpYim8S8Y"
                  : authority.type === "etsi_tl" ||
                      authority.type === "openid_federation"
                    ? "https://trustanchor.example.com"
                    : "did:tdw:..."}
                onChange={(event) =>
                  update(index, {
                    ...authority,
                    values: event.target.value.split(/\r?\n/),
                  })
                }
              />
            </div>
            <div className="flex justify-end">
              <Button
                aria-label="Remove trusted authority"
                size="icon"
                variant="outline"
                onClick={() => {
                  rowKeys.current.splice(index, 1);
                  onChange(value.filter((_, item) => item !== index));
                }}
              >
                <IconTrash className="size-4" />
              </Button>
            </div>
          </div>
        );
      })}
      <Button
        type="button"
        variant="tertiary"
        onClick={() => {
          rowKeys.current.push(crypto.randomUUID());
          onChange([...value, { type: "aki", values: [], credentialId: null }]);
        }}
      >
        <IconPlus />
        Add trusted authority
      </Button>
    </div>
  );
}
