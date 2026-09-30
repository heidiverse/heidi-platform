// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconEqual,
  IconEqualNot,
  IconGitCompare,
  IconMathEqualGreater,
  IconMathEqualLower,
  IconMathGreater,
  IconMathLower,
  type TablerIcon,
} from "@tabler/icons-react";
import {
  Handle,
  type HandleProps,
  type Node,
  type NodeProps,
  Position,
  useNodeConnections,
  useReactFlow,
} from "@xyflow/react";
import type { MessageDescriptor } from "react-intl";
import { useIntl } from "react-intl";
import { Card } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

export type Comparator = "==" | "!=" | ">" | "<" | ">=" | "<=";

type ComparatorNode = Node<{ type: Comparator }, "custom">;

const HEADER_HEIGHT = 40;

const comparators: {
  type: Comparator;
  label: MessageDescriptor;
  icon: TablerIcon;
}[] = [
  {
    type: "==",
    label: {
      id: "pages.proofSchemas.visualBuilder.comparator.equal",
      defaultMessage: "Equal",
    },
    icon: IconEqual,
  },
  {
    type: "!=",
    label: {
      id: "pages.proofSchemas.visualBuilder.comparator.notEqual",
      defaultMessage: "Not Equal",
    },
    icon: IconEqualNot,
  },
  {
    type: ">",
    label: {
      id: "pages.proofSchemas.visualBuilder.comparator.greaterThan",
      defaultMessage: "Greater Than",
    },
    icon: IconMathGreater,
  },
  {
    type: "<",
    label: {
      id: "pages.proofSchemas.visualBuilder.comparator.lessThan",
      defaultMessage: "Less Than",
    },
    icon: IconMathLower,
  },
  {
    type: ">=",
    label: {
      id: "pages.proofSchemas.visualBuilder.comparator.greaterThanOrEqual",
      defaultMessage: "Greater Than or Equal",
    },
    icon: IconMathEqualGreater,
  },
  {
    type: "<=",
    label: {
      id: "pages.proofSchemas.visualBuilder.comparator.lessThanOrEqual",
      defaultMessage: "Less Than or Equal",
    },
    icon: IconMathEqualLower,
  },
];

function CustomHandle(props: HandleProps) {
  const connections = useNodeConnections({
    handleType: props.type,
  });

  return (
    <Handle
      {...props}
      isConnectable={!connections.some((c) => c.targetHandle === props.id)}
    />
  );
}

export function ComparatorNode({ id, data }: NodeProps<ComparatorNode>) {
  const { updateNodeData } = useReactFlow();
  const { $t } = useIntl();

  return (
    <Card className="min-w-56 p-0">
      <div
        className="flex items-center gap-2 border-b p-3"
        style={{ height: HEADER_HEIGHT }}
      >
        <IconGitCompare className="size-5" />
        <h3 className="text-md font-medium">
          {$t({
            id: "pages.proofSchemas.visualBuilder.node.comparison",
            defaultMessage: "Comparison",
          })}
        </h3>
      </div>
      <Select
        value={data.type}
        onValueChange={(value) => updateNodeData(id, { type: value })}
      >
        <SelectTrigger
          onKeyDown={(e) => {
            if (
              ["ArrowDown", "ArrowUp", "ArrowLeft", "ArrowRight"].includes(
                e.key,
              )
            ) {
              e.preventDefault();
            }
          }}
          className="z-10 rounded-t-xs rounded-b-2xl border-none outline-1 outline-offset-0 focus:outline-glacier"
        >
          <SelectValue
            placeholder={$t({
              id: "pages.proofSchemas.visualBuilder.selectOperator",
              defaultMessage: "Select Operator",
            })}
          />
        </SelectTrigger>
        <SelectContent onKeyDown={(e) => e.stopPropagation()}>
          {comparators.map((comparator) => (
            <SelectItem value={comparator.type} key={comparator.type}>
              <div className="flex items-center gap-2 pr-2 [&_svg]:size-4">
                <comparator.icon /> {$t(comparator.label)}
              </div>
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <CustomHandle
        className="size-2! border-border! after:absolute after:-inset-3"
        type="target"
        id="comparator:left"
        style={{ left: 0.5 }}
        position={Position.Left}
      />
      <CustomHandle
        className="size-2! border-border! after:absolute after:-inset-3"
        type="target"
        id="comparator:right"
        style={{ right: 0.5 }}
        position={Position.Right}
      />
      <Handle
        className="size-2! border-border! after:absolute after:-inset-3"
        style={{ bottom: 0.5 }}
        type="source"
        position={Position.Bottom}
      />
    </Card>
  );
}
