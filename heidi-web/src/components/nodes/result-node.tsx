// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconFilter } from "@tabler/icons-react";
import {
  Handle,
  type HandleProps,
  type Node,
  type NodeProps,
  Position,
  useNodeConnections,
} from "@xyflow/react";
import { useIntl } from "react-intl";
import { Card } from "@/components/ui/card";

type ResultNode = Node;

const HEADER_HEIGHT = 40;

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

export function ResultNode({ data: _data }: NodeProps<ResultNode>) {
  const { $t } = useIntl();

  return (
    <Card className="p-0">
      <div
        className="flex items-center gap-2 p-3"
        style={{ height: HEADER_HEIGHT }}
      >
        <IconFilter className="size-5" />
        <h3 className="text-md font-medium">
          {$t({
            id: "pages.proofSchemas.visualBuilder.node.result",
            defaultMessage: "Result",
          })}
        </h3>
      </div>
      <CustomHandle
        className="size-2! border-border! after:absolute after:-inset-3"
        type="target"
        id="result"
        position={Position.Top}
      />
    </Card>
  );
}
