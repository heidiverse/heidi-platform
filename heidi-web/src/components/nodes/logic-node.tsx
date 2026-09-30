// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconLogicAnd,
  IconLogicOr,
  type TablerIcon,
} from "@tabler/icons-react";
import {
  Handle,
  type Node,
  type NodeProps,
  Position,
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

export type LogicGates = "and" | "or";

type LogicNode = Node<
  {
    type: LogicGates;
  },
  "custom"
>;

const HEADER_HEIGHT = 40;

const logicGates: {
  type: LogicGates;
  label: MessageDescriptor;
  icon: TablerIcon;
}[] = [
  {
    type: "and",
    label: {
      id: "pages.proofSchemas.visualBuilder.logicGate.and",
      defaultMessage: "AND",
    },
    icon: IconLogicAnd,
  },
  {
    type: "or",
    label: {
      id: "pages.proofSchemas.visualBuilder.logicGate.or",
      defaultMessage: "OR",
    },
    icon: IconLogicOr,
  },
];

export function LogicNode({ id, data }: NodeProps<LogicNode>) {
  const { updateNodeData } = useReactFlow();
  const { $t } = useIntl();

  return (
    <Card className="min-w-56 p-0">
      <div
        className="flex items-center gap-2 border-b p-3"
        style={{ height: HEADER_HEIGHT }}
      >
        <IconLogicOr className="size-5" />
        <h3 className="text-md font-medium">
          {$t({
            id: "pages.proofSchemas.visualBuilder.node.logicGate",
            defaultMessage: "Logic Gate",
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
          {logicGates.map((gate) => (
            <SelectItem value={gate.type} key={gate.type}>
              <div className="flex items-center gap-2 pr-2 [&_svg]:size-4">
                <gate.icon /> {$t(gate.label)}
              </div>
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Handle
        className="size-2! border-border! after:absolute after:-inset-3"
        type="target"
        position={Position.Top}
      />
      <Handle
        className="size-2! border-border! after:absolute after:-inset-3"
        type="source"
        position={Position.Bottom}
      />
    </Card>
  );
}
