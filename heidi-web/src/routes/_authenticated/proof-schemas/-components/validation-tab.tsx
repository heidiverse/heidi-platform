// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  ReactFlowProvider,
  type useEdgesState,
  type useNodesState,
} from "@xyflow/react";
import { useRef } from "react";
import { FormattedMessage } from "react-intl";
import { Card } from "@/components/ui/card";
import {
  SegmentedControl,
  SegmentedControlItem,
} from "@/components/ui/segmented-control";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { DnDProvider } from "@/lib/dnd-context";
import { reconstructPossumFlow } from "@/lib/reconstruct-possum-flow";
import { CodeEditor } from "@/routes/_authenticated/proof-schemas/-components/code-editor";
import { VisualBuilder } from "@/routes/_authenticated/proof-schemas/-components/visual-builder";
import type { ProofSchema } from "@/types/proof-schema";

export function ValidationTab({
  validationLogic,
  setValidationLogic,
  validationMode,
  setValidationMode,
  setIsDirty,
  nodesState,
  edgesState,
}: {
  validationLogic: string;
  setValidationLogic: (v: string) => void;
  validationMode: "DISABLED" | "ENABLED" | "ENFORCED";
  setValidationMode: (v: "DISABLED" | "ENABLED" | "ENFORCED") => void;
  setIsDirty: (v: boolean) => void;
  nodesState: ReturnType<typeof useNodesState>;
  edgesState: ReturnType<typeof useEdgesState>;
}) {
  const [nodes, setNodes] = nodesState;
  const [, setEdges] = edgesState;
  const lastGraphLogic = useRef(validationLogic);

  return (
    <Tabs
      defaultValue={import.meta.env.MODE.includes("prod") ? "code" : "flow"}
      onValueChange={(value) => {
        if (value !== "flow" || lastGraphLogic.current === validationLogic) {
          return;
        }
        const credentialSchemes = nodes
          .filter((node) => node.type === "credential")
          .map(
            (node) =>
              node.data.credentialSchema as ProofSchema["credentialSchemes"][number],
          );
        const reconstructed = reconstructPossumFlow(
          validationLogic,
          credentialSchemes,
        );
        if (!reconstructed.reconstructed) {
          return;
        }
        setNodes(reconstructed.nodes);
        setEdges(reconstructed.edges);
        lastGraphLogic.current = validationLogic;
      }}
      className="flex h-full flex-col overflow-hidden"
      asChild
    >
      <Card>
        <div className="-mx-4 flex flex-wrap items-center justify-between gap-6 border-b px-4 pb-4">
          <h3 className="text-2xl font-semibold">
            <FormattedMessage
              id="pages.proofSchemas.tabs.validation"
              defaultMessage="Validation"
            />
          </h3>
          {/* TODO: add back once prod ready */}
          {!import.meta.env.MODE.includes("prod") && (
            <TabsList variant="segment">
              <TabsTrigger value="flow" variant="segment">
                <FormattedMessage
                  id="pages.proofSchemas.tabs.validation.visualBuilder"
                  defaultMessage="Visual Builder"
                />
              </TabsTrigger>
              <TabsTrigger value="code" variant="segment">
                <FormattedMessage
                  id="pages.proofSchemas.tabs.validation.codeEditor"
                  defaultMessage="Code Editor"
                />
              </TabsTrigger>
            </TabsList>
          )}
        </div>
        <div className="border-b pb-4">
          <SegmentedControl
            value={validationMode}
            onValueChange={(value) => {
              setValidationMode(value as "DISABLED" | "ENABLED" | "ENFORCED");
              setIsDirty(true);
            }}
            aria-label="Possum validation mode"
          >
            <SegmentedControlItem value="DISABLED">
              <FormattedMessage
                id="pages.proofSchemas.tabs.validation.mode.disabled"
                defaultMessage="Disabled"
              />
            </SegmentedControlItem>
            <SegmentedControlItem value="ENABLED">
              <FormattedMessage
                id="pages.proofSchemas.tabs.validation.mode.enabled"
                defaultMessage="Enabled"
              />
            </SegmentedControlItem>
            <SegmentedControlItem value="ENFORCED">
              <FormattedMessage
                id="pages.proofSchemas.tabs.validation.mode.enforced"
                defaultMessage="Enforced"
              />
            </SegmentedControlItem>
          </SegmentedControl>
        </div>
        {/* TODO: add back once prod ready */}
        {!import.meta.env.MODE.includes("prod") && (
          <TabsContent value="flow" className="-mx-4 mt-0 -mb-4 flex-1">
            <ReactFlowProvider>
              <DnDProvider>
                <VisualBuilder
                  nodesState={nodesState}
                  edgesState={edgesState}
                  setValidationLogic={setValidationLogic}
                  setIsDirty={setIsDirty}
                  onGeneratedLogic={(logic) => {
                    lastGraphLogic.current = logic;
                  }}
                />
              </DnDProvider>
            </ReactFlowProvider>
          </TabsContent>
        )}
        <TabsContent value="code" className="mt-0 -mb-4 flex-1">
          <CodeEditor
            validationLogic={validationLogic}
            setValidationLogic={setValidationLogic}
            setIsDirty={setIsDirty}
          />
        </TabsContent>
      </Card>
    </Tabs>
  );
}
