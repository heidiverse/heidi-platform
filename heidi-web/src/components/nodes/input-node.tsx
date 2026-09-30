// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconArrowsMove,
  IconAt,
  IconCalendar,
  IconCalendarClock,
  IconClock,
  IconNumber,
  IconPhone,
  IconTypography,
  type TablerIcon,
} from "@tabler/icons-react";
import {
  Handle,
  type Node,
  type NodeProps,
  Position,
  useReactFlow,
} from "@xyflow/react";
import type { HTMLInputTypeAttribute } from "react";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

type InputTypes = Extract<
  HTMLInputTypeAttribute,
  "text" | "number" | "date" | "time" | "datetime-local" | "email" | "tel"
>;

const inputTypes: InputTypes[] = [
  "text",
  "number",
  "date",
  "time",
  "datetime-local",
  "email",
  "tel",
] as const;

type TextNode = Node<
  {
    type?: InputTypes;
    value?: string;
  },
  "custom"
>;

const HEADER_HEIGHT = 40;

const iconMap: Record<InputTypes, TablerIcon> = {
  date: IconCalendar,
  time: IconClock,
  "datetime-local": IconCalendarClock,
  number: IconNumber,
  text: IconTypography,
  email: IconAt,
  tel: IconPhone,
};

export function InputNode({
  id,
  data: { type = "text", value = "" },
}: NodeProps<TextNode>) {
  const { updateNodeData } = useReactFlow();

  return (
    <Card className="min-w-56 p-0">
      <div
        className="flex items-center gap-2 border-b pr-2"
        style={{ height: HEADER_HEIGHT }}
      >
        <Select
          value={type}
          onValueChange={(value) =>
            updateNodeData(id, { value: "", type: value })
          }
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
            className="text-md h-full rounded-xs rounded-tl-2xl border-none px-3 py-0 font-medium outline-1 outline-offset-0 focus:outline-glacier"
          >
            <SelectValue />
          </SelectTrigger>
          <SelectContent
            onKeyDown={(e) => e.stopPropagation()}
            className="w-full"
          >
            {inputTypes.map((inputType) => {
              const Icon = iconMap[inputType];
              return (
                <SelectItem value={inputType} key={inputType}>
                  <div className="flex items-center gap-2">
                    <Icon className="size-5" />
                    {inputType}
                  </div>
                </SelectItem>
              );
            })}
          </SelectContent>
        </Select>
        <IconArrowsMove className="text-muted-foreground" />
      </div>
      <div className="relative">
        <Input
          type={type}
          value={value}
          placeholder="Enter Value"
          inputClassName="rounded-t-xs rounded-b-2xl border-none focus-visible:outline-glacier outline-offset-0 outline-1"
          onChange={(e) => updateNodeData(id, { value: e.target.value })}
        />
        <Handle
          className="size-2! border-border! after:absolute after:-inset-3"
          type="source"
          isValidConnection={(c) => {
            return !c.target.includes("result");
          }}
          position={Position.Bottom}
          style={{ bottom: -0.5 }}
        />
      </div>
    </Card>
  );
}
