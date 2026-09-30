// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconAlertTriangle,
  IconCloudUpload,
  IconFileCheck,
  IconList,
  IconPencil,
  IconPlus,
  IconRefresh,
} from "@tabler/icons-react";
import { useQuery, useSuspenseQueries } from "@tanstack/react-query";
import { createFileRoute, notFound, redirect, useBlocker } from "@tanstack/react-router";
import {
  type Edge,
  type Node,
  useEdgesState,
  useNodesState,
} from "@xyflow/react";
import { useAtomValue } from "jotai";
import { lazy, Suspense, useEffect, useMemo, useState } from "react";
import { FormattedMessage } from "react-intl";
import { Loading } from "@/components/common/loading";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { getSwissVerificationQueries } from "@/lib/api/identity-config/api";
import {
  usePublishProofSchemaToTrustRegistryMutation,
  useUpdateProofSchemaMutation,
} from "@/lib/api/proof-schemas/mutations";
import { proofSchemaOptions } from "@/lib/api/proof-schemas/query-options";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";
import { selectedTenantAtom } from "@/lib/atoms";
import { generateValidationLogicPlaceholder } from "@/lib/hooks/generateValidationLogicPlaceholder";
import { useUser } from "@/lib/hooks/use-user";
import { reconstructPossumFlow } from "@/lib/reconstruct-possum-flow";
import { isEditor } from "@/lib/utils/user";
import type { DetailProofSchemasPageTabValues } from "@/types/common";
import { findPresentationProfile } from "@/types/ecosystem-profile";
import type { ProofSchema, SwissVerificationQuery } from "@/types/proof-schema";
import { AddProofSchema } from "./-components/add-proof-schema";
import { CreateOrUpdateDialog } from "./-components/create-or-update-dialog";
import { TrustedAuthorities } from "./-components/trusted-authorities";
import { VerifierInfos } from "./-components/verifier-infos";

const ValidationTab = lazy(() =>
  import("./-components/validation-tab").then((mod) => ({
    default: mod.ValidationTab,
  })),
);

export const Route = createFileRoute("/_authenticated/proof-schemas/$proofSchemaId")({
  beforeLoad: ({ context }) => {
    if (!isEditor(context.user)) {
      throw redirect({ to: "/" });
    }
  },
  loader: async ({ context: { queryClient }, params: { proofSchemaId } }) => {
    const schema = await queryClient.ensureQueryData(
      proofSchemaOptions({ schemaId: proofSchemaId }),
    );
    if (!schema) {
      throw notFound();
    }
    return { crumb: schema.title };
  },
  validateSearch: (
    search: Record<string, unknown>,
  ): {
    tab?: DetailProofSchemasPageTabValues;
  } => {
    return {
      tab:
        (search.tab as DetailProofSchemasPageTabValues) ||
        ("credentials" as DetailProofSchemasPageTabValues),
    };
  },
  component: RouteComponent,
});

function RouteComponent() {
  const user = useUser();
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const tenantId = selectedTenant || user.tenantId;
  const { proofSchemaId } = Route.useParams();
  const { tab } = Route.useSearch();
  const navigate = Route.useNavigate();
  const [{ data: proofSchema }, { data: settings }] = useSuspenseQueries({
    queries: [
      proofSchemaOptions({ schemaId: proofSchemaId }),
      settingsForOrganisationOptions(tenantId),
    ],
  });
  const { mutate: updateSchema } = useUpdateProofSchemaMutation();
  const { mutate: publishToTrustRegistry } =
    usePublishProofSchemaToTrustRegistryMutation();
  const verifierIdentityId = proofSchema.verifierIdentity?.id;
  const { data: identityVqps = [] } = useQuery({
    queryKey: ["swiss-vqps", tenantId ?? "platform", verifierIdentityId],
    queryFn: () => getSwissVerificationQueries({
      tenantId,
      issuerId: verifierIdentityId!,
    }),
    enabled: Boolean(verifierIdentityId),
  });
  const currentSwissVqp = identityVqps.find(
    (query) => query.jwt === proofSchema.swissVerificationQueryStatement,
  );
  const swissVqps: SwissVerificationQuery[] = currentSwissVqp
    ? [currentSwissVqp]
    : proofSchema.swissVerificationQueryStatement
      ? [{ jwt: proofSchema.swissVerificationQueryStatement }]
      : [];
  const hasVerifierInfoFallback =
    (proofSchema.verifierInfos?.length ?? 0) > 0;
  const missingSwissVerificationStatement =
    findPresentationProfile(proofSchema.presentationProfileId)?.family ===
      "swiss" &&
    !proofSchema.swissVerificationQueryStatement?.trim();

  const [selectedCredentialSchemas, setSelectedCredentialSchemas] = useState<
    SelectedCredentialSchema[]
  >(proofSchema.credentialSchemes);
  const [validationLogic, setValidationLogic] = useState(
    proofSchema.validationLogic,
  );
  const [validationMode, setValidationMode] = useState(
    proofSchema.validationMode ?? "DISABLED",
  );
  const [redirectUri, setRedirectUri] = useState(proofSchema.redirectUri ?? "");
  const [trustedAuthorities, setTrustedAuthorities] = useState(
    proofSchema.trustedAuthorities ?? [],
  );
  const [verifierInfos, setVerifierInfos] = useState(
    proofSchema.verifierInfos ?? [],
  );
  const [swissVerificationQueryEnabled, setSwissVerificationQueryEnabled] = useState(
    proofSchema.swissVerificationQueryEnabled ?? true,
  );
  const [alwaysIncludeDcqlQuery, setAlwaysIncludeDcqlQuery] = useState(
    proofSchema.alwaysIncludeDcqlQuery ?? false,
  );
  const defaultRedirectUri = "";

  const initialFlow = useMemo(
    () =>
      reconstructPossumFlow(
        proofSchema.validationLogic,
        proofSchema.credentialSchemes,
      ),
    [proofSchema],
  );
  const nodesState = useNodesState<Node>(initialFlow.nodes);
  const edgesState = useEdgesState<Edge>(initialFlow.edges);

  useEffect(() => {
    // TODO: improve. needed so that ui updates wehenever this query gets invalidated (query result needs to be in state to allow for editing of schema)
    setSelectedCredentialSchemas(proofSchema.credentialSchemes);
    setTrustedAuthorities(proofSchema.trustedAuthorities ?? []);
    setVerifierInfos(proofSchema.verifierInfos ?? []);
    setSwissVerificationQueryEnabled(proofSchema.swissVerificationQueryEnabled ?? true);
    setAlwaysIncludeDcqlQuery(proofSchema.alwaysIncludeDcqlQuery ?? false);
  }, [proofSchema]);

  useEffect(() => {
    if (
      validationLogic ||
      !selectedCredentialSchemas.some((s) => !!s.attributes.length)
    ) {
      return;
    }
    const logicPlaceholder = generateValidationLogicPlaceholder(
      selectedCredentialSchemas,
    );
    setValidationLogic(logicPlaceholder);
  }, [selectedCredentialSchemas]);

  const [isDirty, setIsDirty] = useState(false);

  const { proceed, status, reset } = useBlocker({
    shouldBlockFn: () => isDirty,
    withResolver: true,
  });

  function update() {
    updateSchema({
      ...proofSchema,
      credentialSchemes: selectedCredentialSchemas.map((s) => ({
        id: s.id,
        attributes: s.attributes.map((a) => a.id),
      })),
      validationLogic: validationLogic,
      validationMode,
      redirectUri: redirectUri,
      trustedAuthorities: trustedAuthorities.flatMap((authority) => {
        const type = authority.type.trim();
        const values = authority.values.map((value) => value.trim()).filter(Boolean);
        return type && values.length > 0
          ? [{ ...authority, type, values, credentialId: authority.credentialId || null }]
          : [];
      }),
      verifierInfos,
      swissVerificationQueryEnabled,
      alwaysIncludeDcqlQuery: swissVerificationQueryEnabled
        ? alwaysIncludeDcqlQuery
        : true,
    });
  }

  return (
    <>
      <header className="grid grid-cols-1 gap-3 lg:grid-cols-3">
        <Card className="cn flex justify-between gap-3 lg:col-span-2">
          <div>
            <h1 className="text-3xl font-semibold">{proofSchema.title}</h1>
            <h2 className="text-xl">{proofSchema.purpose}</h2>
          </div>
          <CreateOrUpdateDialog
            proofSchema={proofSchema}
            defaultValues={{
              purpose: proofSchema.purpose,
              title: proofSchema?.title,
            }}
          >
            <Button variant="outline" size="icon">
              <IconPencil />
            </Button>
          </CreateOrUpdateDialog>
        </Card>
        <Card className="flex flex-col gap-2">
          <Button
            disabled={!isDirty}
            onClick={() => {
              update();
              setIsDirty(false);
            }}
          >
            <FormattedMessage
              id="common.saveChanges"
              defaultMessage="Save Changes"
            />
          </Button>
          {settings.trustRegistries.length > 0 && (
            <Button
              disabled={isDirty}
              variant="success"
              onClick={() => {
                publishToTrustRegistry({ proofSchemaId });
              }}
            >
              <IconCloudUpload />
              <FormattedMessage
                id="common.publishToTrustRegistry"
                defaultMessage="Publish To Trust Registry"
              />
            </Button>
          )}
        </Card>
      </header>
      {missingSwissVerificationStatement && (
        <div
          role="alert"
          className="mt-4 flex items-start gap-2 rounded-md border border-current/25 bg-infocard-warning-light p-3 text-sm text-infocard-warning"
        >
          <IconAlertTriangle className="mt-0.5 size-4 shrink-0" />
          {hasVerifierInfoFallback ? (
            <FormattedMessage
              id="pages.proofSchemas.warning.missingSwissVerificationStatementWithFallback"
              defaultMessage="No Swiss verification trust statement is configured. This proof schema can still be used with the configured verifier infos fallback, but it does not have a trusted verification query statement."
            />
          ) : (
            <FormattedMessage
              id="pages.proofSchemas.warning.missingSwissVerificationStatement"
              defaultMessage="No Swiss verification trust statement is configured. This proof schema can still be used, but wallets will not receive a trusted verification query until the statement is requested from the Swiss Trust Registry."
            />
          )}
        </div>
      )}
      <Tabs
        value={tab}
        onValueChange={(value) => {
          navigate({
            search: { tab: value as DetailProofSchemasPageTabValues },
            replace: true,
            ignoreBlocker: true,
          });
        }}
        className="mt-9 flex flex-1 flex-col"
      >
        <TabsList>
          <TabsTrigger value="credentials">
            <IconList className="size-4" />
            <FormattedMessage
              id="pages.proofSchemas.tabs.proofRequest"
              defaultMessage="Proof Request"
            />
          </TabsTrigger>
          <TabsTrigger value="validation">
            <IconFileCheck className="size-4" />
            <FormattedMessage
              id="pages.proofSchemas.tabs.validation"
              defaultMessage="Validation"
            />
          </TabsTrigger>
        </TabsList>
        <TabsContent value="credentials" className="flex-1">
          <Card className="mt-3">
            <h3 className="mb-4 text-2xl font-semibold">
              <FormattedMessage
                id="pages.proofSchemas.tabs.proofRequest"
                defaultMessage="Proof Request"
              />
            </h3>
            <AddProofSchema
              setIsDirty={setIsDirty}
              selectedCredentialSchemas={selectedCredentialSchemas}
              setSelectedCredentialSchemas={setSelectedCredentialSchemas}
              presentationProfileId={proofSchema.presentationProfileId}
            />
            <div className="mt-8 flex min-h-10 flex-col gap-1">
              <Label htmlFor="input-redirectUri" className="leading-5">
                <FormattedMessage
                  id="pages.proofSchemas.tabs.proofRequest.redirectUri"
                  defaultMessage="Redirect Uri"
                />
              </Label>
              <div className="flex flex-wrap gap-2">
                <Input
                  id="input-redirectUri"
                  value={redirectUri}
                  onChange={(e) => {
                    if (!isDirty) {
                      setIsDirty(true);
                    }
                    setRedirectUri(e.target.value);
                  }}
                  name="title"
                  placeholder="https://example.com/redirect"
                  required
                  className="min-w-sm flex-1"
                />
                {defaultRedirectUri !== redirectUri && (
                  <Button
                    variant="outline"
                    onClick={() => {
                      setIsDirty(true);
                      setRedirectUri(defaultRedirectUri);
                    }}
                  >
                    {redirectUri ? <IconRefresh /> : <IconPlus />}
                    {redirectUri ? (
                      <FormattedMessage
                        id="pages.proofSchemas.tabs.proofRequest.resetDefault"
                        defaultMessage="Reset to Default"
                      />
                    ) : (
                      <FormattedMessage
                        id="pages.proofSchemas.tabs.proofRequest.setDefault"
                        defaultMessage="Set to Default"
                      />
                    )}
                  </Button>
                )}
              </div>
            </div>
            <TrustedAuthorities
              value={trustedAuthorities}
              credentialSchemas={selectedCredentialSchemas}
              onChange={(authorities) => {
                setTrustedAuthorities(authorities);
                setIsDirty(true);
              }}
            />
            <VerifierInfos
              value={verifierInfos}
              swissQueries={swissVqps}
              swissVerificationQueryEnabled={swissVerificationQueryEnabled}
              alwaysIncludeDcqlQuery={alwaysIncludeDcqlQuery}
              onChange={(infos) => {
                setVerifierInfos(infos);
                setIsDirty(true);
              }}
              onSwissVerificationQueryEnabledChange={(enabled) => {
                setSwissVerificationQueryEnabled(enabled);
                if (!enabled) setAlwaysIncludeDcqlQuery(true);
                setIsDirty(true);
              }}
              onAlwaysIncludeDcqlQueryChange={(enabled) => {
                setAlwaysIncludeDcqlQuery(enabled);
                setIsDirty(true);
              }}
            />
          </Card>
        </TabsContent>
        <TabsContent value="validation" className="flex-1">
          <Suspense fallback={<Loading />}>
            <ValidationTab
              validationLogic={validationLogic}
              setValidationLogic={setValidationLogic}
              validationMode={validationMode}
              setValidationMode={setValidationMode}
              setIsDirty={setIsDirty}
              nodesState={nodesState}
              edgesState={edgesState}
            />
          </Suspense>
        </TabsContent>
      </Tabs>
      <Dialog open={status === "blocked"}>
        <DialogContent className="space-y-4" onEscapeKeyDown={reset}>
          <DialogHeader>
            <DialogTitle>
              <FormattedMessage
                id="common.unsavedChanges.title"
                defaultMessage="Unsaved changes"
              />
            </DialogTitle>
            <DialogDescription>
              <FormattedMessage
                id="common.unsavedChanges.description"
                defaultMessage="You have unsaved changes. Please save or discard them in order to leave this page."
              />
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2">
            <DialogClose asChild>
              <Button onClick={reset} variant="secondary" className="px-4">
                <FormattedMessage id="common.cancel" defaultMessage="Cancel" />
              </Button>
            </DialogClose>
            <Button variant="destructive" onClick={proceed}>
              <FormattedMessage
                id="common.unsavedChanges.discard"
                defaultMessage="Discard changes"
              />
            </Button>
            <Button
              onClick={() => {
                update();
                proceed?.();
              }}
            >
              <FormattedMessage id="common.save" defaultMessage="Save" />
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

export type SelectedCredentialSchema = Pick<
  ProofSchema["credentialSchemes"][number],
  | "id"
  | "attributes"
  | "displayName"
  | "version"
  | "credentialIdentifier"
  | "issuerSettings"
>;
