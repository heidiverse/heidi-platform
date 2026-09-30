// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { ComparatorNode } from "@/components/nodes/comparator-node";
import { CredentialNode } from "@/components/nodes/credential-node";
import { InputNode } from "@/components/nodes/input-node";
import { LogicNode } from "@/components/nodes/logic-node";
import { ResultNode } from "@/components/nodes/result-node";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useDnD } from "@/lib/dnd-context";
import { convertFlowToPossum } from "@/lib/flow-utils";
import "@xyflow/react/dist/style.css";
import {
  IconFilter,
  IconForms,
  IconGitCompare,
  IconLogicOr,
  IconTools,
} from "@tabler/icons-react";
import {
  addEdge,
  Background,
  BackgroundVariant,
  Controls,
  type Edge,
  type Node,
  type OnConnect,
  ReactFlow,
  type useEdgesState,
  type useNodesState,
  useReactFlow,
} from "@xyflow/react";
import { useCallback, useEffect, useRef } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { toast } from "sonner";

const nodeTypes = {
  credential: CredentialNode,
  comparator: ComparatorNode,
  logic: LogicNode,
  dataInput: InputNode,
  result: ResultNode,
};

const nodeTypeDraggables = [
  {
    type: "comparator",
    icon: IconGitCompare,
    label: {
      id: "pages.proofSchemas.visualBuilder.node.comparison",
      defaultMessage: "Comparison",
    },
    defaultType: "==",
  },
  {
    type: "logic",
    icon: IconLogicOr,
    label: {
      id: "pages.proofSchemas.visualBuilder.node.logicGate",
      defaultMessage: "Logic Gate",
    },
    defaultType: "and",
  },
  {
    type: "dataInput",
    icon: IconForms,
    label: {
      id: "pages.proofSchemas.visualBuilder.node.dataInput",
      defaultMessage: "Data Input",
    },
    defaultType: "text",
  },
  {
    type: "result",
    icon: IconFilter,
    label: {
      id: "pages.proofSchemas.visualBuilder.node.result",
      defaultMessage: "Result",
    },
    defaultType: undefined,
  },
];

let id = 0;
function getId(type: string) {
  return `${type}_${id++}`;
}

export function VisualBuilder({
  nodesState,
  edgesState,
  setValidationLogic,
  setIsDirty,
  onGeneratedLogic,
}: {
  nodesState: ReturnType<typeof useNodesState>;
  edgesState: ReturnType<typeof useEdgesState>;
  setValidationLogic: (v: string) => void;
  setIsDirty: (v: boolean) => void;
  onGeneratedLogic?: (logic: string) => void;
}) {
  const { formatMessage } = useIntl();
  const [nodes, setNodes, onNodesChange] = nodesState;
  const [edges, setEdges, onEdgesChange] = edgesState;
  const { screenToFlowPosition, getZoom } = useReactFlow();
  const graphSignatureRef = useRef(graphSignature(nodes, edges));

  const [type, setType] = useDnD();

  useEffect(() => {
    const signature = graphSignature(nodes, edges);
    if (signature === graphSignatureRef.current) {
      return;
    }
    graphSignatureRef.current = signature;
    setIsDirty(true);
    try {
      const possumCode = convertFlowToPossum(
        (id) => nodes.find((node) => node.id === id),
        edges,
      );
      setValidationLogic(possumCode);
      onGeneratedLogic?.(possumCode);
    } catch {
      // An incomplete graph is still a user change, but has no valid Possum equivalent yet.
    }
  }, [nodes, edges, onGeneratedLogic, setIsDirty, setValidationLogic]);

  const onConnect: OnConnect = useCallback(
    (params) => setEdges((eds) => addEdge(params, eds)),
    [setEdges],
  );
  const onDragOver = useCallback((event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = "move";
  }, []);

  const onDrop = useCallback(
    (event: React.DragEvent<HTMLDivElement>) => {
      event.preventDefault();

      if (!type) {
        return;
      }

      const zoom = getZoom();

      const position = screenToFlowPosition({
        x: event.clientX - (type === "result" ? 50 : 110 * zoom),
        y: event.clientY - (type === "result" ? 20 : 40 * zoom),
      });

      const defaultType = nodeTypeDraggables.find(
        (n) => n.type === type,
      )?.defaultType;

      const newNode: Node = {
        id: getId(type),
        type,
        position,
        data: defaultType ? { type: defaultType } : {},
      };

      setNodes((nds) => nds.concat(newNode));
    },
    [screenToFlowPosition, type, getZoom],
  );

  return (
    <div className="flex size-full">
      <div className="flex h-full flex-col gap-2 border-r bg-background/75 p-3">
        <div className="mb-1 px-1">
          <h4 className="text-lg leading-none font-semibold">
            <FormattedMessage
              id="pages.proofSchemas.visualBuilder.nodes"
              defaultMessage="Nodes"
            />
          </h4>
          <p className="mt-1.5 text-xs text-muted-foreground">
            <FormattedMessage
              id="pages.proofSchemas.visualBuilder.description"
              defaultMessage="Drag and Drop Nodes To Canvas"
            />
          </p>
        </div>
        {nodeTypeDraggables.map(
          ({ icon: Icon, label, type }) =>
            !(nodes.some((n) => n.type === "result") && type === "result") && (
              <Card
                className="flex h-10 skew-x-0 cursor-grab items-center gap-2 rounded-xl p-3 font-medium"
                draggable
                onDragStart={(event) => {
                  setType(type);
                  event.dataTransfer.effectAllowed = "move";
                }}
                key={type}
              >
                <Icon className="size-5 shrink-0" />
                {formatMessage(label)}
              </Card>
            ),
        )}
        <Button
          onClick={() => {
            try {
              const possumCode = convertFlowToPossum(
                (id) => nodes.find((node) => node.id === id),
                edges,
              );
              setValidationLogic(possumCode);
              setIsDirty(true);
              onGeneratedLogic?.(possumCode);
              toast.success(
                formatMessage({
                  id: "pages.proofSchemas.visualBuilder.generated",
                  defaultMessage: "Validation Logic Generated",
                }),
              );
            } catch (error) {
              if (error instanceof Error) {
                toast.error(error.message);
              }
            }
          }}
          className="mt-auto min-w-0"
        >
          <IconTools className="shrink-0" />
          <FormattedMessage
            id="pages.proofSchemas.visualBuilder.generateCode"
            defaultMessage="Generate Code"
          />
        </Button>
      </div>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onDragOver={onDragOver}
        onDrop={onDrop}
        nodeTypes={nodeTypes}
      >
        <Controls position="top-right" showInteractive={false} />
        <Background variant={BackgroundVariant.Dots} gap={12} size={1} />
      </ReactFlow>
    </div>
  );
}

function graphSignature(nodes: Node[], edges: Edge[]) {
  return JSON.stringify({
    nodes: nodes
      .map(({ id, type, data }) => ({ id, type, data }))
      .sort((left, right) => left.id.localeCompare(right.id)),
    edges: [...edges]
      .map(({ id, source, sourceHandle, target, targetHandle }) => ({
        id,
        source,
        sourceHandle,
        target,
        targetHandle,
      }))
      .sort((left, right) => left.id.localeCompare(right.id)),
  });
}
