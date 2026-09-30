// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { runtimeConfig } from "@/lib/runtime-config";
import type { ProofSchema } from "@/types/proof-schema";

type IssuanceScheme = ProofSchema["credentialSchemes"][number];
type CredentialAttribute = IssuanceScheme["attributes"][number];

const FORMAT_SUFFIXES = {
  SD_JWT: "_dc__sd-jwt",
  MSO_MDOC: "_mso_mdoc",
  ZKP_VC: "_bbs-termwise",
  W3C_VCDM: "_w3c-vcdm",
  OPENBADGES: "_open-badges",
} as const;

type CredentialFormat = keyof typeof FORMAT_SUFFIXES;

export function possumVariableName(
  credentialIdentifier: string,
  attributeName: string,
) {
  const value = `${credentialIdentifier}_${attributeName}`.replace(
    /[^A-Za-z0-9_]/g,
    "_",
  );
  return /^[A-Za-z_]/.test(value) ? value : `_${value}`;
}

export function possumAttributeExpression(
  credentialScheme: IssuanceScheme,
  attribute: CredentialAttribute,
) {
  const formats = supportedFormats(credentialScheme);
  const paths = formats.map((format) =>
    attributePath(credentialScheme, attribute, format),
  );

  return paths.reduceRight<string>((fallback, path) => {
    if (!fallback) {
      return possumPath(path);
    }
    const queryRoot = possumPath([path[0]!]);
    return `if(${queryRoot}) {
  ${possumPath(path)}
} else {
${indent(fallback, 2)}
}`;
  }, "");
}

function supportedFormats(credentialScheme: IssuanceScheme) {
  const configured = credentialScheme.issuerSettings?.supportedCredentialTypes as
    | string[]
    | undefined;
  if (!configured?.length) {
    return Object.keys(FORMAT_SUFFIXES) as CredentialFormat[];
  }
  return configured.filter(
    (format): format is CredentialFormat => format in FORMAT_SUFFIXES,
  );
}

function attributePath(
  credentialScheme: IssuanceScheme,
  attribute: CredentialAttribute,
  format: CredentialFormat,
) {
  const identifier = credentialScheme.credentialIdentifier;
  const queryId = `${identifier}${FORMAT_SUFFIXES[format]}`;
  const overrides = attribute.attributeNameOverrides as
    | Record<string, string | undefined>
    | undefined;

  switch (format) {
    case "SD_JWT": {
      const claimName = overrides?.SD_JWT || attribute.name;
      return [queryId, ...claimName.split(".")];
    }
    case "MSO_MDOC":
      return [
        queryId,
        credentialScheme.issuerSettings?.namespace ||
          runtimeConfig.heidiDefaultNamespace,
        overrides?.MSO_MDOC || attribute.name,
      ];
    case "ZKP_VC":
      return [queryId, overrides?.BBS || `http://schema.org/${attribute.name}`];
    case "W3C_VCDM":
      return [
        queryId,
        "credentialSubject",
        overrides?.W3C_VCDM || attribute.name,
      ];
    case "OPENBADGES":
      return [
        queryId,
        "credentialSubject",
        overrides?.OPEN_BADGES || attribute.name,
      ];
  }
}

function possumPath(segments: string[]) {
  return `$${segments.map((segment) => `.${JSON.stringify(segment)}`).join("")}`;
}

function indent(value: string, spaces: number) {
  const prefix = " ".repeat(spaces);
  return value
    .split("\n")
    .map((line) => `${prefix}${line}`)
    .join("\n");
}
