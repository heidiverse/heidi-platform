// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  possumAttributeExpression,
  possumVariableName,
} from "@/lib/possum-utils";
import type { ProofSchema } from "@/types/proof-schema";

export function generateValidationLogicPlaceholder(
  credentialSchemes: ProofSchema["credentialSchemes"],
): string {
  const definitions = credentialSchemes.flatMap((credentialScheme) =>
    credentialScheme.attributes.map(
      (attribute) =>
        `let ${possumVariableName(credentialScheme.credentialIdentifier, attribute.name)} = ${possumAttributeExpression(credentialScheme, attribute)};`,
    ),
  );

  return `${definitions.join("\n\n")}\n\ntrue`;
}
