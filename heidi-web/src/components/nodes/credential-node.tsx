// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from "@tanstack/react-query";
import { Handle, type Node, type NodeProps, Position } from "@xyflow/react";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { schemaOptions } from "@/lib/api/credential-schemas/query-options";
import { argbToHex } from "@/lib/utils/color";
import { fieldTypes, TextColor } from "@/types/credential-schema";
import type { ProofSchema } from "@/types/proof-schema";

type CredentialNode = Node<
  { credentialSchema: ProofSchema["credentialSchemes"][number] },
  "custom"
>;

const HEADER_HEIGHT = 56;
const ATTRIBUTE_HEIGHT = 48;

export function CredentialNode({ data }: NodeProps<CredentialNode>) {
  const { data: schema } = useQuery(
    schemaOptions({ schemaId: data.credentialSchema.id }),
  );

  return (
    <>
      <Card className="min-w-64 divide-y p-0">
        <div
          className="flex items-center gap-2.5 px-3"
          style={{ height: HEADER_HEIGHT }}
        >
          <img
            src={
              schema?.credentialSchemeStyleDetails[0]?.style.textColor ===
              TextColor.Dark
                ? "/assets/card-overlay-light.svg"
                : "/assets/card-overlay-dark.svg"
            }
            alt="Card Overlay"
            style={{
              backgroundColor: `#${argbToHex(schema?.credentialSchemeStyleDetails[0]?.style.cardColor)}`,
            }}
            className="h-7 shrink-0 rounded-md"
          />

          <h3 className="text-lg font-semibold">
            {data.credentialSchema.displayName}
          </h3>
        </div>
        {data.credentialSchema.attributes.map((a) => (
          <div
            key={a.name}
            style={{ height: ATTRIBUTE_HEIGHT }}
            className="flex items-center justify-between gap-2 p-3"
          >
            {a.name}
            <Badge variant="secondary" className="border-border">
              {fieldTypes[a.type]}
            </Badge>
          </div>
        ))}
      </Card>
      {data.credentialSchema.attributes.map((a, i) => (
        <Handle
          className="size-2! border-border! after:absolute after:-inset-3"
          key={a.name}
          id={`attr:${a.name}`}
          type="source"
          position={Position.Right}
          style={{
            right: 0.5,
            top:
              HEADER_HEIGHT + (i + 1) * ATTRIBUTE_HEIGHT - ATTRIBUTE_HEIGHT / 2,
          }}
        />
      ))}
    </>
  );
}
