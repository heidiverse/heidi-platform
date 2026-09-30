// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  HWCLightbox,
} from "@heidiverse/heidi-web-components/react";
import {
  IconChevronDown,
  IconRubberStamp,
  IconUserScan,
} from "@tabler/icons-react";
import { useQuery, useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute, getRouteApi } from "@tanstack/react-router";
import { useAtomValue } from "jotai";
import {
  type ComponentProps,
  type ComponentType,
  useEffect,
  useState,
} from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { toast } from "sonner";
import { CredentialSchemaCombobox } from "@/components/common/credential-schema-combobox";
import { PageHeader } from "@/components/common/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import {
  schemaListOptions,
  schemaOptions,
} from "@/lib/api/credential-schemas/query-options";
import { proofSchemaListOptions } from "@/lib/api/proof-schemas/query-options";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";
import { createTestingProcess } from "@/lib/api/testing/api";
import { localeAtom, selectedTenantAtom } from "@/lib/atoms";
import { DEFAULT_LOCALE } from "@/lib/constants";
import { defaultValuesForType } from "@/lib/credential-attributes";
import { getExtensionPresentationTestCases } from "@/lib/extensions";
import { useUser } from "@/lib/hooks/use-user";
import { argbToHex } from "@/lib/utils/color";
import { selectNewestSchemas } from "@/lib/utils/credential-schemas";
import { getLocalizedValue } from "@/lib/utils/localized";
import {
  type CredentialSchemaLite,
  CredentialSchemaState,
  TextColor,
} from "@/types/credential-schema";

type TestingLightboxProps = Omit<ComponentProps<typeof HWCLightbox>, "token"> & {
  clientInteractionToken?: string;
  onRetry?: () => void | Promise<void>;
};

const TestingLightbox =
  HWCLightbox as unknown as ComponentType<TestingLightboxProps>;

export const Route = createFileRoute("/_authenticated/testing/")({
  component: RouteComponent,
});

const routeApi = getRouteApi("/_authenticated/testing");

function RouteComponent() {
  const { crumb } = routeApi.useLoaderData();
  return (
    <div className="flex flex-col gap-3">
      <PageHeader heading={crumb} />
      <IssuanceTestCase />
      <PresentationTestCase />
    </div>
  );
}

function PresentationTestCase() {
  const presentationTestCases = getExtensionPresentationTestCases();
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const { $t } = useIntl();
  const { data: proofSchemas } = useSuspenseQuery({
    ...proofSchemaListOptions(),
    select(data) {
      return data.filter((schema) => schema.tenantId === selectedTenant);
    },
  });
  const [clientInteractionToken, setClientInteractionToken] = useState<string>();
  const [isLightboxOpen, setIsLightboxOpen] = useState(false);
  const [selectedProofSchemaId, setSelectedProofSchemaId] = useState<
    string | undefined
  >(proofSchemas[0]?.uuid);
  const [selectedCaseIndex, setSelectedCaseIndex] = useState(0);
  const [includeTransactionData, setIncludeTransactionData] = useState(false);
  const selectedTestCase = presentationTestCases[selectedCaseIndex];
  useEffect(() => {
    setSelectedProofSchemaId(proofSchemas[0]?.uuid);
  }, [selectedTenant]);
  const locale = useAtomValue(localeAtom);

  async function startPresentation() {
    if (!selectedProofSchemaId) return;

    setClientInteractionToken(undefined);
    setIsLightboxOpen(false);

    try {
      const { clientInteractionToken } = await createTestingProcess({
        action: "presentation",
        ...(includeTransactionData && selectedTestCase?.extensionData),
        presentationData: {
          proofSchemeId: selectedProofSchemaId,
          presentationProfileId: proofSchemas.find(
            (schema) => schema.uuid === selectedProofSchemaId,
          )?.presentationProfileId,
          ...(includeTransactionData && selectedTestCase && {
            transactionData: selectedTestCase.transactionData.map(
              (t) => btoa(JSON.stringify(t)),
            ),
          }),
        },
      });
      setClientInteractionToken(clientInteractionToken);
      setIsLightboxOpen(true);
    } catch (error) {
      console.error("Failed to initialize presentation", error);
      toast.error(
        $t({
          id: "pages.testing.process.error",
          defaultMessage: "Could not start the test process.",
        }),
        { description: (error as Error).message },
      );
    }
  }

  return (
    <Card className="flex flex-col justify-between p-0 md:flex-row md:flex-wrap">
      <CardHeader>
        <CardTitle className="text-xl leading-none">
          <FormattedMessage
            id="common.presentation"
            defaultMessage="Presentation"
          />
        </CardTitle>
        <CardDescription>
          <FormattedMessage
            id="pages.testing.presentation.description"
            defaultMessage="Start a presentation (with optional Transaction Data)"
          />
          {presentationTestCases.length > 0 && (
            <label className="mt-1 flex w-fit items-center gap-2 place-self-start rounded-lg border px-1.5 py-1 shadow-xs">
              <FormattedMessage
                id="pages.testing.includeTransactionData"
                defaultMessage="Include Transaction Data"
              />
              <Switch
                size="sm"
                checked={includeTransactionData}
                onCheckedChange={setIncludeTransactionData}
              />
            </label>
          )}
        </CardDescription>
      </CardHeader>
      <CardFooter className="flex-1 flex-wrap items-end gap-3 md:justify-end md:pt-6 lg:flex-nowrap">
        {includeTransactionData && presentationTestCases.length > 0 && (
          <Select
            value={selectedCaseIndex.toString()}
            onValueChange={(value) => {
              setSelectedCaseIndex(Number.parseInt(value, 10));
            }}
          >
            <Label className="flex flex-col gap-1.5">
              <FormattedMessage
                id="pages.testing.transactionData"
                defaultMessage="Transaction Data"
              />
              <SelectTrigger className="min-w-48 gap-2 text-left">
                <SelectValue
                  placeholder={$t({
                    id: "pages.testing.chooseMockCase",
                    defaultMessage: "Choose test case",
                  })}
                />
              </SelectTrigger>
            </Label>
            <SelectContent>
              {presentationTestCases.map((testCase, i) => (
                <SelectItem key={testCase.id} value={i.toString()}>
                  {$t(testCase.label)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}
        <Select
          value={selectedProofSchemaId}
          onValueChange={setSelectedProofSchemaId}
        >
          <Label className="flex flex-col gap-1.5">
            <FormattedMessage
              id="common.proofSchema"
              defaultMessage="Proof Schema"
            />
            <SelectTrigger className="w-fit gap-2 text-left">
              <SelectValue
                placeholder={$t({
                  id: "pages.testing.chooseProofSchema",
                  defaultMessage: "Choose Proof Schema",
                })}
              />
            </SelectTrigger>
          </Label>
          <SelectContent>
            {proofSchemas.map((schema) => {
              return (
                <SelectItem key={schema.uuid} value={schema.uuid}>
                  {schema.title}
                  <span className="text-xs text-muted-foreground">
                    {" "}
                    {schema.credentialSchemes
                      .map((s) => s.displayName)
                      .join(", ")}
                  </span>
                </SelectItem>
              );
            })}
          </SelectContent>
        </Select>
        <Button onClick={startPresentation}>
          <IconUserScan />
          <FormattedMessage id="common.verify" defaultMessage="Verify" />
        </Button>
      </CardFooter>
      {isLightboxOpen && clientInteractionToken && (
        <TestingLightbox
          locale={locale}
          clientInteractionToken={clientInteractionToken}
          open
          onClose={() => {
            setIsLightboxOpen(false);
          }}
          onRetry={startPresentation}
        />
      )}
    </Card>
  );
}

function IssuanceTestCase() {
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const user = useUser();
  const { $t } = useIntl();
  const locale = useAtomValue(localeAtom);
  const tenantId = selectedTenant ?? user.tenantId;
  const [clientInteractionToken, setClientInteractionToken] = useState<string>();
  const [txCode, setTxCode] = useState<string>();
  const { data: credentialSchemas } = useSuspenseQuery({
    ...schemaListOptions({
      statesToExclude: [
        CredentialSchemaState.Archived,
        CredentialSchemaState.Created,
      ],
      includeImages: false,
    }),
    select(data: CredentialSchemaLite[]) {
      return data.filter((s) => s.tenantId === selectedTenant);
    },
  });
  const [selectedSchema, setSelectedSchema] = useState(
    () => selectNewestSchemas(credentialSchemas)[0],
  );
  const [isLightboxOpen, setIsLightboxOpen] = useState(false);
  const [isComboboxOpen, setIsComboboxOpen] = useState(false);
  const [includeTxCode, setIncludeTxCode] = useState(false);
  const { data: detailSchema, isPending } = useQuery({
    ...schemaOptions({ schemaId: selectedSchema?.id ?? "" }),
    enabled: !!selectedSchema?.id,
  });
  const { data: settings } = useQuery(
    settingsForOrganisationOptions(tenantId),
  );
  useEffect(() => {
    setSelectedSchema(selectNewestSchemas(credentialSchemas)[0]);
  }, [selectedTenant]);

  async function startIssuance() {
    if (!detailSchema) return;
    setClientInteractionToken(undefined);
    setTxCode(undefined);
    setIsLightboxOpen(false);

    try {
      const { clientInteractionToken, txCode } = await createTestingProcess({
        action: "pre_auth_issuance",
        preAuthIssuanceData: {
          values: Object.fromEntries(
            detailSchema.attributes.map((a) => [
              a.name,
              a.isArray
                ? [
                    defaultValuesForType[a.type] ??
                      getLocalizedValue(
                        a.displayName,
                        settings?.defaultLanguage ?? DEFAULT_LOCALE,
                      ) ??
                      a.name,
                  ]
                : (defaultValuesForType[a.type] ??
                  getLocalizedValue(
                    a.displayName,
                    settings?.defaultLanguage ?? DEFAULT_LOCALE,
                  ) ??
                  a.name),
            ]),
          ),
          schemaIdentifier: {
            credentialIdentifier: detailSchema.credentialIdentifier,
            version: detailSchema.version,
          },
          issuanceProfileId: detailSchema.issuerSettings.issuanceProfileId,
          includeTxCode,
        },
      });
      setClientInteractionToken(clientInteractionToken);
      txCode && setTxCode(txCode);
      setIsLightboxOpen(true);
    } catch (error) {
      console.error("Failed to initialize issuance", error);
      toast.error(
        $t({
          id: "pages.testing.process.error",
          defaultMessage: "Could not start the test process.",
        }),
        { description: (error as Error).message },
      );
    }
  }

  return (
    <Card className="flex flex-col justify-between p-0 md:flex-row md:flex-wrap">
      <CardHeader>
        <CardTitle className="text-xl leading-none">
          <FormattedMessage id="common.issuance" defaultMessage="Issuance" />
        </CardTitle>
        <CardDescription>
          <FormattedMessage
            id="pages.testing.issuance.description"
            defaultMessage="Issue a pre-authorized credential"
          />
          <label className="mt-1 flex w-fit items-center gap-2 place-self-start rounded-lg border px-1.5 py-1 shadow-xs">
            <FormattedMessage
              id="pages.testing.includeTxCode"
              defaultMessage="Include Tx-Code"
            />
            <Switch
              size="sm"
              checked={includeTxCode}
              onCheckedChange={setIncludeTxCode}
            />
          </label>
        </CardDescription>
      </CardHeader>
      <CardFooter className="flex-1 flex-wrap items-end gap-2 md:justify-end md:pt-6">
        <Label className="flex flex-col gap-1 overflow-hidden">
          <FormattedMessage
            id="common.credentialSchema"
            defaultMessage="Credential Schema"
          />
          <CredentialSchemaCombobox
            popoverProps={{
              open: isComboboxOpen,
              onOpenChange: setIsComboboxOpen,
            }}
            schemas={credentialSchemas}
            buttonProps={{
              variant: "outline",
              className: "bg-surface font-normal normal-case text-left pl-2",
              asChild: true,
            }}
            commandItemProps={(schema) => ({
              onSelect: () => {
                setSelectedSchema(schema);
                setIsComboboxOpen(false);
              },
            })}
          >
            {selectedSchema ? (
              <div className="flex w-full items-center gap-3">
                <img
                  src={
                    selectedSchema.credentialSchemeStyleDetails[0]?.style
                      .textColor === TextColor.Dark
                      ? "/assets/card-overlay-light.svg"
                      : "/assets/card-overlay-dark.svg"
                  }
                  alt="Card Overlay"
                  style={{
                    backgroundColor: `#${argbToHex(selectedSchema.credentialSchemeStyleDetails[0]?.style.cardColor)}`,
                  }}
                  className="ml-1 h-6 shrink-0 rounded-md"
                />
                <span className="truncate">
                  {selectedSchema.displayName ??
                    selectedSchema.credentialIdentifier}
                </span>
                <Badge variant="secondary">V {selectedSchema.version}</Badge>
                <IconChevronDown className="size-4 text-muted-foreground" />
              </div>
            ) : (
              <FormattedMessage
                id="common.select.withValue"
                defaultMessage="Select {value}"
                values={{
                  value: $t({
                    id: "common.credentialSchema",
                    defaultMessage: "Credential Schema",
                  }),
                }}
              />
            )}
          </CredentialSchemaCombobox>
        </Label>
        <Button disabled={isPending} onClick={startIssuance}>
          <IconRubberStamp />
          <FormattedMessage id="common.issue" defaultMessage="Issue" />
        </Button>
      </CardFooter>
      {txCode && isLightboxOpen && (
        <Card className="fixed top-4 left-1/2 z-[99999] -translate-x-1/2 rounded-xl px-3 py-1.5 text-xl font-medium tracking-widest tabular-nums shadow-2xl">
          {txCode}
        </Card>
      )}
      {isLightboxOpen && clientInteractionToken && (
        <TestingLightbox
          locale={locale}
          clientInteractionToken={clientInteractionToken}
          open
          onClose={() => {
            setIsLightboxOpen(false);
          }}
          onRetry={startIssuance}
        />
      )}
    </Card>
  );
}
