// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { Edge, Node } from "@xyflow/react";
import type { Comparator } from "@/components/nodes/comparator-node";
import type { LogicGates } from "@/components/nodes/logic-node";
import { possumVariableName } from "@/lib/possum-utils";
import type { ProofSchema } from "@/types/proof-schema";

type IssuanceScheme = ProofSchema["credentialSchemes"][number];
type CredentialAttribute = IssuanceScheme["attributes"][number];
type AttributeSource = {
  credentialScheme: IssuanceScheme;
  attribute: CredentialAttribute;
};

type Definition = { name: string; expression: string };

export function reconstructPossumFlow(
  possum: string,
  credentialSchemes: IssuanceScheme[],
) {
  const nodes = credentialNodes(credentialSchemes);
  const edges: Edge[] = [];
  const statements = splitStatements(possum);
  const definitions = statements
    .slice(0, -1)
    .map(parseDefinition)
    .filter((definition): definition is Definition => definition !== null);
  const resultExpression = statements.at(-1)?.trim() ?? "";

  const attributeSources = new Map<string, AttributeSource>();
  for (const credentialScheme of credentialSchemes) {
    for (const attribute of credentialScheme.attributes) {
      attributeSources.set(
        possumVariableName(credentialScheme.credentialIdentifier, attribute.name),
        { credentialScheme, attribute },
      );
    }
  }

  const graphNodeIds = new Set<string>();
  let comparatorIndex = 0;
  let logicIndex = 0;

  for (const definition of definitions) {
    if (attributeSources.has(definition.name)) {
      continue;
    }

    const comparison = parseComparison(definition.expression);
    if (comparison) {
      const comparatorId = definition.name;
      nodes.push({
        id: comparatorId,
        type: "comparator",
        position: { x: 160 + comparatorIndex * 300, y: 300 },
        data: { type: comparison.operator },
      });
      graphNodeIds.add(comparatorId);
      connectOperand({
        operand: comparison.left,
        side: "left",
        targetId: comparatorId,
        index: comparatorIndex,
        nodes,
        edges,
        attributeSources,
        graphNodeIds,
      });
      connectOperand({
        operand: comparison.right,
        side: "right",
        targetId: comparatorId,
        index: comparatorIndex,
        nodes,
        edges,
        attributeSources,
        graphNodeIds,
      });
      comparatorIndex += 1;
      continue;
    }

    const logic = parseLogic(definition.expression);
    if (logic?.operands.every((operand) => graphNodeIds.has(operand))) {
      nodes.push({
        id: definition.name,
        type: "logic",
        position: { x: 160 + logicIndex * 300, y: 470 },
        data: { type: logic.operator },
      });
      graphNodeIds.add(definition.name);
      logic.operands.forEach((source, index) => {
        edges.push({
          id: `reconstructed-${source}-${definition.name}-${index}`,
          source,
          target: definition.name,
        });
      });
      logicIndex += 1;
    }
  }

  const resultSource = resolveSource(resultExpression, attributeSources, graphNodeIds);
  if (resultSource) {
    const resultId = "reconstructed_result";
    nodes.push({
      id: resultId,
      type: "result",
      position: { x: 160, y: logicIndex ? 650 : 470 },
      data: {},
    });
    edges.push({
      id: `reconstructed-${resultSource.source}-result`,
      source: resultSource.source,
      sourceHandle: resultSource.sourceHandle,
      target: resultId,
      targetHandle: "result",
    });
  }

  return {
    nodes,
    edges,
    reconstructed:
      resultSource !== null || resultExpression === "true" || !possum.trim(),
  };
}

function credentialNodes(credentialSchemes: IssuanceScheme[]): Node[] {
  return credentialSchemes.map((credentialScheme, index) => ({
    id: credentialScheme.id,
    type: "credential",
    position: { x: 16 + index * 272, y: 16 },
    data: { credentialSchema: credentialScheme },
    deletable: false,
  }));
}

function parseDefinition(statement: string): Definition | null {
  const match = /^let\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([\s\S]+)$/.exec(
    statement.trim(),
  );
  return match?.[1] && match[2]
    ? { name: match[1], expression: match[2].trim() }
    : null;
}

function parseComparison(expression: string) {
  const match =
    /^([A-Za-z_][A-Za-z0-9_]*|"(?:[^"\\]|\\.)*"|-?\d+(?:\.\d+)?)\s*(==|!=|>=|<=|>|<)\s*([A-Za-z_][A-Za-z0-9_]*|"(?:[^"\\]|\\.)*"|-?\d+(?:\.\d+)?)$/.exec(
      expression.trim(),
    );
  if (!match?.[1] || !match[2] || !match[3]) return null;
  return {
    left: match[1],
    operator: match[2] as Comparator,
    right: match[3],
  };
}

function parseLogic(expression: string) {
  const match = /^([A-Za-z_][A-Za-z0-9_]*)([\s\S]*)$/.exec(expression.trim());
  if (!match?.[1]) return null;
  const remainder = match[2] ?? "";
  const operators = [...remainder.matchAll(/\s+(and|or)\s+/g)].map(
    (operator) => operator[1],
  );
  if (!operators.length || !operators.every((operator) => operator === operators[0])) {
    return null;
  }
  const operands = expression.trim().split(/\s+(?:and|or)\s+/);
  if (!operands.every((operand) => /^[A-Za-z_][A-Za-z0-9_]*$/.test(operand))) {
    return null;
  }
  return { operator: operators[0] as LogicGates, operands };
}

function connectOperand({
  operand,
  side,
  targetId,
  index,
  nodes,
  edges,
  attributeSources,
  graphNodeIds,
}: {
  operand: string;
  side: "left" | "right";
  targetId: string;
  index: number;
  nodes: Node[];
  edges: Edge[];
  attributeSources: Map<string, AttributeSource>;
  graphNodeIds: Set<string>;
}) {
  const source = resolveSource(operand, attributeSources, graphNodeIds);
  if (source) {
    edges.push({
      id: `reconstructed-${source.source}-${targetId}-${side}`,
      source: source.source,
      sourceHandle: source.sourceHandle,
      target: targetId,
      targetHandle: `comparator:${side}`,
    });
    return;
  }

  const literal = parseLiteral(operand);
  if (!literal) return;
  const inputId = `${targetId}_${side}_input`;
  nodes.push({
    id: inputId,
    type: "dataInput",
    position: {
      x: 10 + index * 300 + (side === "right" ? 300 : 0),
      y: 170,
    },
    data: { type: literal.type, value: literal.value },
  });
  edges.push({
    id: `reconstructed-${inputId}-${targetId}-${side}`,
    source: inputId,
    target: targetId,
    targetHandle: `comparator:${side}`,
  });
}

function resolveSource(
  operand: string,
  attributeSources: Map<string, AttributeSource>,
  graphNodeIds: Set<string>,
) {
  const attributeSource = attributeSources.get(operand.trim());
  if (attributeSource) {
    return {
      source: attributeSource.credentialScheme.id,
      sourceHandle: `attr:${attributeSource.attribute.name}`,
    };
  }
  if (graphNodeIds.has(operand.trim())) {
    return { source: operand.trim(), sourceHandle: undefined };
  }
  return null;
}

function parseLiteral(value: string) {
  if (/^-?\d+(?:\.\d+)?$/.test(value)) {
    return { type: "number", value };
  }
  if (value.startsWith('"') && value.endsWith('"')) {
    try {
      return { type: "text", value: String(JSON.parse(value)) };
    } catch {
      return null;
    }
  }
  return null;
}

function splitStatements(possum: string) {
  const statements: string[] = [];
  let start = 0;
  let braceDepth = 0;
  let quote: '"' | "'" | null = null;
  let escaped = false;

  for (let index = 0; index < possum.length; index += 1) {
    const character = possum[index];
    if (quote) {
      if (escaped) {
        escaped = false;
      } else if (character === "\\") {
        escaped = true;
      } else if (character === quote) {
        quote = null;
      }
      continue;
    }
    if (character === '"' || character === "'") {
      quote = character;
    } else if (character === "{") {
      braceDepth += 1;
    } else if (character === "}") {
      braceDepth -= 1;
    } else if (character === ";" && braceDepth === 0) {
      statements.push(possum.slice(start, index).trim());
      start = index + 1;
    }
  }

  const remainder = possum.slice(start).trim();
  if (remainder) statements.push(remainder);
  return statements.filter(Boolean);
}
