// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconApi,
  IconBrandHtml5,
  IconBrandNodejs,
  IconBrandReact,
  IconChevronDown,
  IconCopy,
  IconExternalLink,
  IconEye,
  IconEyeOff,
  IconLoader2,
  type TablerIcon,
} from "@tabler/icons-react";
import {
  useQuery,
  useSuspenseQueries,
  useSuspenseQuery,
} from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { type ReactNode, useState } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { Light as SyntaxHighlighter } from "react-syntax-highlighter";
import bash from "react-syntax-highlighter/dist/esm/languages/hljs/bash";
import js from "react-syntax-highlighter/dist/esm/languages/hljs/javascript";
import json from "react-syntax-highlighter/dist/esm/languages/hljs/json";
import { github } from "react-syntax-highlighter/dist/esm/styles/hljs";
import { compare } from "semver";
import { toast } from "sonner";
import { useCopyToClipboard } from "usehooks-ts";
import {
  ComboboxSchemaItem,
  CredentialSchemaCombobox,
} from "@/components/common/credential-schema-combobox";
import { PageHeader } from "@/components/common/page-header";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  SegmentedControl,
  SegmentedControlItem,
} from "@/components/ui/segmented-control";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  schemaListOptions,
  schemaOptions,
} from "@/lib/api/credential-schemas/query-options";
import { integrationOptions } from "@/lib/api/integrations/query-options";
import { issuerDefinitionListOptions } from "@/lib/api/issuer-definitions/query-options";
import { proofSchemaListOptions } from "@/lib/api/proof-schemas/query-options";
import { runtimeConfig } from "@/lib/runtime-config";
import { CredentialSchemaState } from "@/types/credential-schema";

SyntaxHighlighter.registerLanguage("json", json);
SyntaxHighlighter.registerLanguage("js", js);
SyntaxHighlighter.registerLanguage("bash", bash);

type IntegrationProcessBody = Record<string, unknown>;

const INTEGRATOR_API_DOCS_HREF = import.meta.env.PROD
  ? "/api-docs"
  : "/api-docs.html";

export function IntegrationDetail({
  integrationId,
  action,
  heading,
}: {
  integrationId: string;
  action: "issuance" | "presentation";
  heading: string;
}) {
  const { $t } = useIntl();

  const { data: integration } = useSuspenseQuery(
    integrationOptions({ id: integrationId }),
  );
  const { data: issuerDefinitions } = useSuspenseQuery(
    issuerDefinitionListOptions({ tenantId: integration.tenantId }),
  );

  const navigate = useNavigate({ from: "/integrations/$integrationId" });

  const [{ data: filteredProofSchemas }] = useSuspenseQueries({
    queries: [
      proofSchemaListOptions({
        credentialIdentifiers: integration?.credentialIdentifiers,
      }),
    ],
  });

  const credentialSchemas = useSuspenseQueries({
    queries:
      integration?.credentialIdentifiers.map((credentialIdentifier) =>
        schemaListOptions({
          credentialIdentifier,
          includeImages: false,
          statesToExclude: [
            CredentialSchemaState.Archived,
            CredentialSchemaState.Created,
          ],
        }),
      ) ?? [],
    combine(result) {
      return result.flatMap((r) => r.data);
    },
  });

  const [isComboboxOpen, setIsComboboxOpen] = useState(false);
  const [selectedSchema, setSelectedSchema] = useState(
    credentialSchemas.reduce((prev, schema) => {
      if (
        schema.credentialIdentifier ===
          credentialSchemas[0]?.credentialIdentifier &&
        (!prev || compare(schema.version, prev.version) > 0)
      ) {
        prev = schema;
      }
      return prev;
    }),
  );
  const [selectedProofSchema, setSelectedProofSchema] = useState(
    filteredProofSchemas[0],
  );
  const { data: selectedDetailSchema, isPending } = useQuery({
    ...schemaOptions({ schemaId: selectedSchema!.id }),
    enabled: Boolean(selectedSchema),
  });

  const issuerSlug = issuerDefinitions.find(
    (i) => i.id === selectedDetailSchema?.issuerSettings.id,
  )?.slug;

  function requestBody() {
    switch (action) {
      case "issuance":
        if (!selectedDetailSchema) {
          return "";
        }
        return JSON.stringify(
          {
            action: "pre_auth_issuance",
            preAuthIssuanceData: {
              schemaIdentifier: {
                credentialIdentifier: selectedDetailSchema.credentialIdentifier,
                version: selectedDetailSchema.version,
              },
              values: Object.fromEntries(
                selectedDetailSchema.attributes.map((a) => [
                  a.name,
                  a.isArray ? ["<replace-with-value>"] : "<replace-with-value>",
                ]),
              ),
              issuanceProfileId:
                selectedDetailSchema.issuerSettings.issuanceProfileId,
              ...(issuerSlug && { issuerSlug }),
            },
          } as IntegrationProcessBody,
          null,
          2,
        );
      case "presentation":
        if (!selectedProofSchema) {
          return "";
        }
        return JSON.stringify(
          {
            action: "presentation",
            presentationData: {
              proofSchemeId: selectedProofSchema.uuid,
              presentationProfileId: selectedProofSchema.presentationProfileId,
              useDcApi: false,
              oid4vpVersion: "VERSION_ONE_DOT_ZERO",
            },
          } as IntegrationProcessBody,
          null,
          2,
        );
    }
  }

  const [, copyTextToClipboard] = useCopyToClipboard();
  const codeSnippets = getCodeSnippets($t);
  const [componentType, setComponentType] = useState<ComponentType>(
    "webcomponents-browser",
  );
  const [apiKeyVisible, setApiKeyVisible] = useState(false);
  const integrationBaseUrl = runtimeConfig.heidiApiBaseUrl.replace(
    /\/$/,
    "",
  );
  const initializeCurl = `curl --request POST "${integrationBaseUrl}/integration/v1/processes" \\
  --header "Authorization: ApiKey <API_KEY>" \\
  --header "Content-Type: application/json" \\
  --data @initialize.json`;
  const startCurl = `curl --request POST "${integrationBaseUrl}/integration/v1/processes/<PROCESS_ID>/start" \\
  --header "Authorization: ApiKey <API_KEY>" \\
  --header "Content-Type: application/json" \\
  --data '{"processToken":"<PROCESS_TOKEN>"}'`;
  const resultCurl = `curl "${integrationBaseUrl}/integration/v1/processes/<PROCESS_ID>/result" \\
  --header "Authorization: ApiKey <API_KEY>"`;

  return (
    <>
      <PageHeader
        heading={
          <div className="flex items-center gap-3">
            {heading}
          </div>
        }
      >
        {action === "issuance" && (
          <div className="flex flex-wrap gap-2">
            <CredentialSchemaCombobox
              schemas={credentialSchemas}
              buttonProps={{
                variant: "outline",
                className:
                  "bg-input w-full sm:w-auto font-normal normal-case text-left",
              }}
              popoverProps={{
                open: isComboboxOpen,
                onOpenChange: setIsComboboxOpen,
              }}
              commandProps={{
                defaultValue: selectedSchema?.id,
              }}
              commandItemProps={(schema) => ({
                onSelect: () => {
                  setSelectedSchema(schema);
                  setIsComboboxOpen(false);
                },
              })}
            >
              <ComboboxSchemaItem hideIdentifier schema={selectedSchema} />
              <IconChevronDown />
            </CredentialSchemaCombobox>
          </div>
        )}
        {action === "presentation" && (
          <Select
            value={selectedProofSchema?.uuid}
            onValueChange={(id) =>
              setSelectedProofSchema(
                filteredProofSchemas.find((s) => s.uuid === id),
              )
            }
          >
            <SelectTrigger className="w-fit min-w-40 gap-2">
              {selectedProofSchema?.title}
            </SelectTrigger>
            <SelectContent>
              {filteredProofSchemas.map((proofSchema) => (
                <SelectItem key={proofSchema.uuid} value={proofSchema.uuid}>
                  <div className="flex flex-col items-start">
                    <p>{proofSchema.title}</p>
                    <p className="text-xs text-muted-foreground">
                      {proofSchema.purpose}
                    </p>
                  </div>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
      </PageHeader>

      <Card className="mt-3 flex flex-col gap-4">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="max-w-3xl">
            <h2 className="text-xl font-semibold">
              <FormattedMessage
                id="pages.integrations.flow"
                defaultMessage="Integration flow"
              />
            </h2>
            <p className="mt-1 text-muted-foreground">
              <FormattedMessage
                id="pages.integrations.flow.description"
                defaultMessage="Your backend initializes and starts the process, then gives the browser only the short-lived client interaction token. The browser handles the wallet interaction. Your backend retrieves the result."
              />
            </p>
          </div>
          <Button variant="outline" asChild>
            <a href={INTEGRATOR_API_DOCS_HREF} target="_blank" rel="noopener">
              <FormattedMessage
                id="pages.integrations.apiReference"
                defaultMessage="Integrator API reference"
              />
              <IconExternalLink />
            </a>
          </Button>
        </div>
        <div className="grid gap-3 md:grid-cols-4">
          <FlowStep
            number="1"
            title={
              <FormattedMessage
                id="pages.integrations.flow.initialize"
                defaultMessage="Initialize"
              />
            }
            description={
              <FormattedMessage
                id="pages.integrations.flow.initialize.description"
                defaultMessage="Backend · API key"
              />
            }
          />
          <FlowStep
            number="2"
            title={
              <FormattedMessage
                id="pages.integrations.flow.start"
                defaultMessage="Start"
              />
            }
            description={
              <FormattedMessage
                id="pages.integrations.flow.start.description"
                defaultMessage="Backend · process token"
              />
            }
          />
          <FlowStep
            number="3"
            title={
              <FormattedMessage
                id="pages.integrations.flow.interact"
                defaultMessage="Interact"
              />
            }
            description={
              <FormattedMessage
                id="pages.integrations.flow.interact.description"
                defaultMessage="Browser · client interaction token"
              />
            }
          />
          <FlowStep
            number="4"
            title={
              <FormattedMessage
                id="pages.integrations.flow.result"
                defaultMessage="Result"
              />
            }
            description={
              <FormattedMessage
                id="pages.integrations.flow.result.description"
                defaultMessage="Backend · API key"
              />
            }
          />
        </div>
      </Card>

      <Card className="mt-3 flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h2 className="text-xl font-semibold">
            <FormattedMessage id="common.apiKey" defaultMessage="API Key" />
          </h2>
          <h3 className="text-sm text-muted-foreground">
            <FormattedMessage
              id="pages.integrations.addToHeaders"
              defaultMessage="Add to headers"
            />
            :{" "}
            <span className="rounded-lg border bg-background px-1 py-0.5 whitespace-nowrap">
              Authorization: ApiKey {"<API_KEY>"}
            </span>
          </h3>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Input
            type={apiKeyVisible ? "text" : "password"}
            className="font-mono tracking-widest *:bg-background sm:min-w-[36ch]"
            value={
              apiKeyVisible ? integration.apiKey : Array(36).fill("*").join("")
            }
            readOnly
          />
          <Button
            size="icon"
            variant="outline"
            onClick={() => {
              setApiKeyVisible(!apiKeyVisible);
            }}
          >
            {apiKeyVisible ? <IconEyeOff /> : <IconEye />}
          </Button>
          <Button
            size="icon"
            variant="outline"
            onClick={() => {
              toast.promise(copyTextToClipboard(integration.apiKey), {
                success: $t({
                  id: "common.copied",
                  defaultMessage: "Copied to clipboard",
                }),
                error: $t({
                  id: "common.copied.error",
                  defaultMessage: "Failed to copy to clipboard",
                }),
              });
            }}
          >
            <IconCopy />
          </Button>
        </div>
      </Card>

      <section className="mt-10 flex flex-col gap-2">
        <div className="mb-2 flex flex-col gap-1">
          <h2 className="text-xl font-semibold">
            <FormattedMessage
              id="pages.integrations.createProcess"
              defaultMessage="1. Initialize the Process – Backend"
            />
          </h2>
          <h3 className="text-muted-foreground">
            <FormattedMessage
              id="pages.integrations.createProcess.description"
              defaultMessage="Send the request from your backend. The response contains <code>processId</code> and a backend-only <code>processToken</code>. Store both on the backend; never expose the API key or process token to the browser."
              values={{
                code: (chunks) => <code>{chunks}</code>,
              }}
            />
          </h3>
        </div>
        {filteredProofSchemas.length > 0 && (
          <SegmentedControl
            className="w-full sm:w-auto"
            value={action}
            onValueChange={(value) => {
              navigate({
                search: { action: value as "issuance" | "presentation" },
                replace: true,
              });
            }}
          >
            <SegmentedControlItem value="issuance">
              <FormattedMessage
                id="common.issuance"
                defaultMessage="Issuance"
              />
            </SegmentedControlItem>
            {filteredProofSchemas.length > 0 && (
              <SegmentedControlItem value="presentation">
                <FormattedMessage
                  id="common.presentation"
                  defaultMessage="Presentation"
                />
              </SegmentedControlItem>
            )}
          </SegmentedControl>
        )}
        <CodeCard language="bash" snippet={initializeCurl} />
        {isPending && (
          <Card className="grid min-h-72 place-items-center">
            <IconLoader2 className="animate animate-spin" />
          </Card>
        )}
        {selectedDetailSchema && (
          <Card
            className="relative"
            ref={(ref) => {
              for (const span of (ref?.querySelectorAll("span[style]") ??
                []) as HTMLSpanElement[]) {
                if (span.innerText.includes("replace-with-value")) {
                  span.className =
                    "bg-green-200 text-green-800! p-0.5 -m-0.5 rounded";
                }
              }
            }}
          >
            <SyntaxHighlighter
              PreTag={({ children }) => (
                <pre className="bg-transparent p-0 text-sm">{children}</pre>
              )}
              wrapLongLines
              style={github}
              language="json"
            >
              {requestBody()}
            </SyntaxHighlighter>
            <Button
              size="icon"
              variant="outline"
              className="absolute top-4 right-4"
              onClick={() => {
                toast.promise(copyTextToClipboard(requestBody()), {
                  success: $t({
                    id: "common.copied",
                    defaultMessage: "Copied to clipboard",
                  }),
                  error: $t({
                    id: "common.copied.error",
                    defaultMessage: "Failed to copy to clipboard",
                  }),
                });
              }}
            >
              <IconCopy />
            </Button>
          </Card>
        )}
      </section>

      <section className="mt-12 flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <h2 className="text-xl font-semibold">
            <FormattedMessage
              id="pages.integrations.startProcess"
              defaultMessage="2. Start the Process – Backend"
            />
          </h2>
          <p className="text-muted-foreground">
            <FormattedMessage
              id="pages.integrations.startProcess.description"
              defaultMessage="Exchange the process token from your backend. Send only the returned <code>clientInteractionToken</code> to the browser."
              values={{ code: (chunks) => <code>{chunks}</code> }}
            />
          </p>
        </div>
        <CodeCard language="bash" snippet={startCurl} />
      </section>

      <section className="mt-12 flex flex-col gap-4">
        <div className="flex flex-wrap items-end justify-between gap-4 @3xl:flex-nowrap">
          <div className="flex w-full min-w-0 flex-col gap-1">
            <h2 className="text-xl font-semibold">
              <FormattedMessage
                id="pages.integrations.useClientInteractionToken"
                defaultMessage="3. Handle Wallet Interaction – Browser"
              />
            </h2>
            {codeSnippets[componentType].description}
          </div>
          <Select
            value={componentType}
            onValueChange={(v) => setComponentType(v as ComponentType)}
          >
            <SelectTrigger className="gap-2 @3xl:w-auto">
              <SelectValue />
            </SelectTrigger>
            <SelectContent align="end" collisionPadding={16}>
              {Object.entries(codeSnippets).map(([item, value]) => (
                <SelectItem value={item} key={item}>
                  <div className="flex items-center gap-1.5 whitespace-nowrap">
                    <value.icon className="size-5 shrink-0" />
                    {value.title}
                  </div>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {codeSnippets[componentType].snippet && (
          <Card className="relative">
            <SyntaxHighlighter
              PreTag={({ children }) => (
                <pre className="bg-transparent p-0 text-sm">{children}</pre>
              )}
              wrapLongLines
              style={github}
              language={
                componentType.includes("webcomponents")
                  ? "html"
                  : componentType === "custom"
                    ? "bash"
                    : "js"
              }
            >
              {codeSnippets[componentType].snippet}
            </SyntaxHighlighter>
            <Button
              size="icon"
              variant="outline"
              className="absolute top-4 right-4"
              onClick={() => {
                toast.promise(
                  copyTextToClipboard(codeSnippets[componentType].snippet),
                  {
                    success: $t({
                      id: "common.copied",
                      defaultMessage: "Copied to clipboard",
                    }),
                    error: $t({
                      id: "common.copied.error",
                      defaultMessage: "Failed to copy to clipboard",
                    }),
                  },
                );
              }}
            >
              <IconCopy />
            </Button>
          </Card>
        )}
      </section>

      <section className="mt-12 flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <h2 className="text-xl font-semibold">
            <FormattedMessage
              id="pages.integrations.getResult"
              defaultMessage="4. Retrieve the Result – Backend"
            />
          </h2>
          <p className="text-muted-foreground">
            <FormattedMessage
              id="pages.integrations.getResult.description"
              defaultMessage="Poll with the integration API key until the connection state is terminal. Presentation disclosures are returned here. A raw VP token is returned only when initialization requested <code>includeVpToken: true</code>."
              values={{ code: (chunks) => <code>{chunks}</code> }}
            />
          </p>
        </div>
        <CodeCard language="bash" snippet={resultCurl} />
      </section>
    </>
  );
}

function FlowStep({
  number,
  title,
  description,
}: {
  number: string;
  title: ReactNode;
  description: ReactNode;
}) {
  return (
    <div className="rounded-xl border bg-background p-3">
      <p className="text-xs font-semibold text-muted-foreground">{number}</p>
      <p className="font-semibold">{title}</p>
      <p className="text-sm text-muted-foreground">{description}</p>
    </div>
  );
}

function CodeCard({
  language,
  snippet,
}: {
  language: "bash" | "json";
  snippet: string;
}) {
  const { $t } = useIntl();
  const [, copyTextToClipboard] = useCopyToClipboard();

  return (
    <Card className="relative">
      <SyntaxHighlighter
        PreTag={({ children }) => (
          <pre className="bg-transparent p-0 text-sm">{children}</pre>
        )}
        wrapLongLines
        style={github}
        language={language}
      >
        {snippet}
      </SyntaxHighlighter>
      <Button
        size="icon"
        variant="outline"
        className="absolute top-4 right-4"
        onClick={() => {
          toast.promise(copyTextToClipboard(snippet), {
            success: $t({
              id: "common.copied",
              defaultMessage: "Copied to clipboard",
            }),
            error: $t({
              id: "common.copied.error",
              defaultMessage: "Failed to copy to clipboard",
            }),
          });
        }}
      >
        <IconCopy />
      </Button>
    </Card>
  );
}

type ComponentType =
  | "webcomponents-build"
  | "webcomponents-browser"
  | "react"
  | "custom";

function getCodeSnippets(
  $t: ReturnType<typeof useIntl>["$t"],
): Record<
  ComponentType,
  { title: string; snippet: string; icon: TablerIcon; description?: ReactNode }
> {
  return {
    "webcomponents-browser": {
      icon: IconBrandHtml5,
      title: $t({
        id: "pages.integrations.componentType.webComponentsBrowser",
        defaultMessage: "Web Components (Browser)",
      }),
      snippet: `<html>
  <head>
    <!-- initialize config -->
    <script type="module">
      import { config } from "https://cdn.jsdelivr.net/npm/@heidiverse/heidi-web-components/dist/config.js";
      config.init({
        baseUrl: "${runtimeConfig.heidiApiBaseUrl}",
      });
    </script>

    <!-- load webcomponents -->
    <script
      type="module"
      src="https://cdn.jsdelivr.net/npm/@heidiverse/heidi-web-components/dist/main.js"
    ></script>
  </head>

  <body>
    <!-- set properties here or in js (see below) -->
    <hwc-button 
      id="hwc-component"
      client-interaction-token="your-client-interaction-token"
      locale="en" <!-- (optional, default is de, values: de, en, fr, it) -->
    >
      <!-- optionally render custom button like this: -->  
      <button slot="lightbox-trigger" style="background-color:red; color:white">
        My Custom Button
      </button>
      <!-- or use the default button with just text: -->
      Open Lightbox
    </hwc-button>
    <!-- or -->
    <hwc-lightbox 
      id="hwc-component"
      open
      client-interaction-token=...
    ></hwc-lightbox>
    <!-- or -->
    <hwc-wallet-interaction-view 
      id="hwc-component"
      client-interaction-token=...
    ></hwc-wallet-interaction-view>

    <script>
      // Your server first calls POST /integration/v1/processes, then
      // POST /integration/v1/processes/:processId/start with its processToken.
      const clientInteractionToken = yourServerSideStartProcessCall();
      const hwcComponentOfYourChoice = document.getElementById("hwc-component");
      hwcComponentOfYourChoice.clientInteractionToken = clientInteractionToken;
      hwcComponentOfYourChoice.locale = "en";
      // if you choose to use the lightbox, you can control the open state like this
      hwcComponentOfYourChoice.open = true // or \`false\` to close
    </script>
  </body>
</html>`,
    },
    "webcomponents-build": {
      icon: IconBrandNodejs,
      title: $t({
        id: "pages.integrations.componentType.webComponentsBuild",
        defaultMessage: "Web Components (With Build Step)",
      }),
      description: <InstallHWC />,
      snippet: `// main.(js|ts)
// initialize config before importing or mounting components
import { config } from "@heidiverse/heidi-web-components/config";
config.init({ 
  baseUrl: "${runtimeConfig.heidiApiBaseUrl}",
});


// app.(js|ts)
// import webcomponent source
import "@heidiverse/heidi-web-components";

//index.html (or vue/svelte/etc. component)
<body>
  <!-- set properties here or in js (see below) -->
  <hwc-button 
    id="hwc-component"
    client-interaction-token="your-client-interaction-token"
    locale="en" <!-- (optional, default is de, values: de, en, fr, it) -->
  >
    <!-- optionally render custom button using the slot attribute -->  
    <button slot="lightbox-trigger" style="background-color:red; color:white">
      My Custom Button
    </button>
    <!-- or use the default button with just text: -->
    Open Lightbox
  </hwc-button>
  <!-- or -->
  <hwc-lightbox 
    id="hwc-component"
    open
    client-interaction-token=...
  ></hwc-lightbox>
  <!-- or -->
  <hwc-wallet-interaction-view 
    id="hwc-component"
    client-interaction-token=...
  ></hwc-wallet-interaction-view>

  <script>
    // Your server first calls POST /integration/v1/processes, then
    // POST /integration/v1/processes/:processId/start with its processToken.
    const clientInteractionToken = yourServerSideStartProcessCall();
    const hwcComponentOfYourChoice = document.getElementById("hwc-component");
    hwcComponentOfYourChoice.clientInteractionToken = clientInteractionToken;
    hwcComponentOfYourChoice.locale = "en";
    // if you choose to use the lightbox, you can control the open state like this
    hwcComponentOfYourChoice.open = true // or \`false\` to close
  </script>
</body>`,
    },
    react: {
      icon: IconBrandReact,
      title: $t({
        id: "pages.integrations.componentType.react",
        defaultMessage: "React Components",
      }),
      description: <InstallHWC />,
      snippet: `// main.(jsx|tsx)
// initialize config before mounting components
import { config } from "@heidiverse/heidi-web-components/config";
config.init({
  baseUrl: "${runtimeConfig.heidiApiBaseUrl}",
});

// app.(jsx|tsx)
import { HWCButton } from "@heidiverse/heidi-web-components/react";
// or use other components
import { HWCLightbox } from "@heidiverse/heidi-web-components/react";
import { HWCWalletInteractionView } from "@heidiverse/heidi-web-components/react";

function AppWithButton() {
  // Your server first calls POST /integration/v1/processes, then
  // POST /integration/v1/processes/:processId/start with its processToken.
  const { data: clientInteractionToken } = useQuery({ queryKey: ["clientInteractionToken"], queryFn: yourServerSideStartProcessCall });

  return (
    <HWCButton
      clientInteractionToken={clientInteractionToken}
      // optional
      locale="en"
    >
      // optionally render custom button using the slot attribute
      <button slot="lightbox-trigger" style={{ backgroundColor: "red", color: "white" }}>
        My Custom Button
      </button>
      // or use the default button with just text:
      Open Lightbox
    </HWCButton>
  );
}

function AppWithLighbox() {
  // Your server first calls POST /integration/v1/processes, then
  // POST /integration/v1/processes/:processId/start with its processToken.
  const { data: clientInteractionToken } = useQuery({ queryKey: ["clientInteractionToken"], queryFn: yourServerSideStartProcessCall });
  const [open, setOpen] = useState(false);

  return (
    <HWCLightbox
      open={open}
      clientInteractionToken={clientInteractionToken}
      // optional
      locale="en"
    />
  );
}

function AppWithInteractionView() {
  // Your server first calls POST /integration/v1/processes, then
  // POST /integration/v1/processes/:processId/start with its processToken.
  const { data: clientInteractionToken } = useQuery({ queryKey: ["clientInteractionToken"], queryFn: yourServerSideStartProcessCall });

  return ( 
    <HWCWalletInteractionView 
      clientInteractionToken={clientInteractionToken}
      // optional
      locale="en" 
    />
  ); 
}`,
    },
    custom: {
      icon: IconApi,
      title: $t({
        id: "pages.integrations.componentType.custom",
        defaultMessage: "Custom",
      }),
      description: (
        <Card>
          <h3 className="text-muted-foreground">
            <FormattedMessage
              id="pages.integrations.custom.processDescription"
              defaultMessage="Call <code>GET /interaction/v1/processes/current</code> with the client interaction token. The response contains browser-safe state, wallet hand-off data, client configuration, and allowlisted display claims."
              values={{
                code: (chunks) => <code>{chunks}</code>,
              }}
            />
          </h3>
        </Card>
      ),
      snippet: `curl "${runtimeConfig.heidiApiBaseUrl}interaction/v1/processes/current" \\
  --header "Authorization: Bearer <CLIENT_INTERACTION_TOKEN>"`,
    },
  };
}

function InstallHWC() {
  const managers = [
    { name: "pnpm", installCmd: "add" },
    { name: "npm", installCmd: "install" },
    { name: "yarn", installCmd: "add" },
    { name: "bun", installCmd: "add" },
  ];
  const { $t } = useIntl();
  return (
    <div className="text-muted-foreground">
      <h3>
        <FormattedMessage
          id="pages.integrations.installHWC"
          defaultMessage="Install the Heidi Web Components with your package manager of choice"
        />
      </h3>
      <Tabs
        defaultValue="pnpm"
        className="mt-1 overflow-hidden rounded-2xl border bg-card"
      >
        <TabsList>
          {managers.map(({ name }) => (
            <TabsTrigger key={name} value={name}>
              {name}
            </TabsTrigger>
          ))}
        </TabsList>
        {managers.map(({ name, installCmd }) => {
          const command = `${name} ${installCmd} @heidiverse/heidi-web-components`;
          return (
            <TabsContent
              className="relative mt-0 flex items-center pr-7"
              key={name}
              value={name}
            >
              <Button
                type="button"
                className="absolute right-0.5 size-auto p-1.5"
                size="icon"
                variant="ghost"
                onClick={() => {
                  toast.promise(navigator.clipboard.writeText(command), {
                    success: $t({
                      id: "common.copied",
                      defaultMessage: "Copied to clipboard",
                    }),
                    error: $t({
                      id: "common.copied.error",
                      defaultMessage: "Failed to copy to clipboard",
                    }),
                  });
                }}
              >
                <IconCopy />
              </Button>
              <div className="flex overflow-x-auto px-3 py-2">
                <pre className="text-sm">{command}</pre>
              </div>
            </TabsContent>
          );
        })}
      </Tabs>
    </div>
  );
}
