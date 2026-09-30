// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { Edge, Node } from "@xyflow/react";
import type { Comparator } from "@/components/nodes/comparator-node";
import type { LogicGates } from "@/components/nodes/logic-node";
import {
  possumAttributeExpression,
  possumVariableName,
} from "@/lib/possum-utils";
import type { ProofSchema } from "@/types/proof-schema";

export function convertFlowToPossum(
  getNode: (id: string) => Node | undefined,
  edges: Edge[],
) {
  const usedAttributes = edges.reduce(
    (prev, edge) => {
      const node = getNode(edge.source);
      if (!node) return prev;
      const [identifier, value] = edge.sourceHandle?.split(":") ?? [];
      if (identifier === "attr" && node.type === "credential") {
        const credentialSchema = node.data
          .credentialSchema as ProofSchema["credentialSchemes"][number];
        const { credentialIdentifier } = credentialSchema;
        if (!prev[credentialIdentifier]) {
          prev[credentialIdentifier] = [];
        }
        if (value) {
          const attribute = credentialSchema.attributes.find(
            (candidate) => candidate.name === value,
          );
          if (
            attribute &&
            !prev[credentialIdentifier].some(
              (candidate) => candidate.attribute.name === attribute.name,
            )
          ) {
            prev[credentialIdentifier].push({ credentialSchema, attribute });
          }
        }
      }
      return prev;
    },
    {} as Record<
      string,
      {
        credentialSchema: ProofSchema["credentialSchemes"][number];
        attribute: ProofSchema["credentialSchemes"][number]["attributes"][number];
      }[]
    >,
  );

  const comparisons = edges.reduce(
    (prev, edge) => {
      const targetNode = getNode(edge.target);
      const sourceNode = getNode(edge.source);
      if (!targetNode || !sourceNode) return prev;

      const [targetHandleId, targetHandleValue] =
        edge.targetHandle?.split(":") ?? [];
      const [sourceHandleId, sourceHandleValue] =
        edge.sourceHandle?.split(":") ?? [];

      if (targetHandleId === "comparator" && targetNode.type === "comparator") {
        const key = `${targetNode.data.type as Comparator}:${targetNode.id}`;
        if (!prev[key]) {
          prev[key] = { left: "", right: "", varName: targetNode.id };
        }

        if (sourceHandleId === "attr") {
          const { credentialIdentifier, attributes } = sourceNode.data
            .credentialSchema as ProofSchema["credentialSchemes"][number];

          const { name: attributeName } =
            attributes.find((a) => a.name === sourceHandleValue) ?? {};

          prev[key][targetHandleValue as "left" | "right"] =
            possumVariableName(
              credentialIdentifier,
              attributeName ?? sourceHandleValue ?? "",
            );
        }

        if (sourceNode.type === "dataInput") {
          prev[key][targetHandleValue as "left" | "right"] =
            sourceNode.data.type === "number"
              ? (sourceNode.data.value as string)
              : `"${sourceNode.data.value as string}"`;
        }
      }
      return prev;
    },
    {} as Record<string, { left: string; right: string; varName: string }>,
  );

  const gates = edges.reduce(
    (prev, edge) => {
      const targetNode = getNode(edge.target);
      const sourceNode = getNode(edge.source);
      if (!targetNode || !sourceNode) return prev;

      if (targetNode.type === "logic") {
        const key = targetNode.data.type as LogicGates;
        if (!prev[key]) {
          prev[key] = [];
        }
        if (sourceNode.type === "credential") {
          prev[key].push(
            `${edge.target}:${generateVarName({ node: sourceNode, edge })}`,
          );
        } else {
          prev[key].push(`${edge.target}:${edge.source}`);
        }
      }

      return prev;
    },
    {} as { [key in LogicGates]: string[] },
  );

  const variableDefinitions = Object.entries(usedAttributes).flatMap(
    ([credentialIdentifier, attributes]) => {
      return attributes.map(({ credentialSchema, attribute }) => {
        return `let ${possumVariableName(credentialIdentifier, attribute.name)} = ${possumAttributeExpression(credentialSchema, attribute)};`;
      });
    },
  );

  const comparisonsCode = Object.entries(comparisons).flatMap(
    ([operator, { left, right, varName }]) => {
      return `let ${varName} = ${left} ${operator.split(":")[0]} ${right};`;
    },
  );

  const logicGatesCode = Object.entries(gates).flatMap(
    ([operator, varNames]) => {
      const [gateVarName] = (varNames[0] ?? "").split(":");
      return `let ${gateVarName} = ${varNames.map((v) => v.split(":")[1]).join(` ${operator} `)};`;
    },
  );

  const resultEdge = edges.find((e) => e.targetHandle === "result");

  if (!resultEdge) {
    throw new Error("Please connect your expressiont to the result node");
  }

  const resultNode = getNode(resultEdge.source);
  const resultLine = generateVarName({ node: resultNode, edge: resultEdge });

  const possumCode = `${variableDefinitions.join("\n\n")}

${comparisonsCode.join("\n")}

${logicGatesCode.join("\n")}

${resultLine}`.replace(/\n\n\n/g, "\n");

  return possumCode;
}

function generateVarName({ node, edge }: { node?: Node; edge: Edge }) {
  if (!node) {
    return "";
  }
  if (node.type !== "credential" || !edge.sourceHandle) {
    return node.id;
  }
  return possumVariableName(
    (node.data.credentialSchema as ProofSchema["credentialSchemes"][number])
      .credentialIdentifier,
    edge.sourceHandle.split(":")[1] ?? "",
  );
}
