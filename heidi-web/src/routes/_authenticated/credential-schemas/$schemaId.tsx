// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconClipboardList,
  IconCloudUpload,
  IconColorSwatch,
  IconCornerDownRight,
  IconEye,
  IconList,
  IconLoader2,
  IconPencil,
  IconSettings,
} from "@tabler/icons-react";
import { useStore } from "@tanstack/react-form";
import {
  useQueries,
  useQuery,
  useQueryClient,
  useSuspenseQueries,
  useSuspenseQuery,
} from "@tanstack/react-query";
import { createFileRoute, Link, useBlocker } from "@tanstack/react-router";
import { useAtomValue, useSetAtom } from "jotai";
import { useEffect, useState } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { compare, inc, type ReleaseType, valid } from "semver";
import { z } from "zod";
import { Badge } from "@/components/ui/badge";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  useCreateNewVersionMutation,
  useUpdateSchemaMutation,
} from "@/lib/api/credential-schemas/mutations";
import {
  schemaListOptions,
  schemaOptions,
} from "@/lib/api/credential-schemas/query-options";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";
import {
  templateLibraryListOptions,
  templateOptions,
} from "@/lib/api/templates/query-options";
import { contentLanguageAtom, selectedTenantAtom } from "@/lib/atoms";
import { tabNavigation } from "@/lib/credential-schema-tabs";
import { useUser } from "@/lib/hooks/use-user";
import { getDefaultValuesForSchema } from "@/lib/utils/credential-schemas";
import { isEditor } from "@/lib/utils/user";
import { AttributeOverrides } from "@/routes/_authenticated/credential-schemas/-components/attribute-overrides";
import { useAppForm } from "@/routes/_authenticated/credential-schemas/-form/form-context";
import type { DetailPageTabValues } from "@/types/common";
import {
  type CredentialSchema,
  type CredentialSchemaAttribute,
  CredentialSchemaState,
} from "@/types/credential-schema";
import { Attributes } from "./-components/attributes";
import { CardPreview } from "./-components/card-preview";
import { CardStyleCard } from "./-components/card-style-card";
import { IssuingSettingsTab } from "./-components/issuing-settings-tab";
import { OrderedProperties } from "./-components/ordered-properties";
import { PublishDialog } from "./-components/publish-dialog";
import {
  type CredentialSchemaForm,
  createCredentialSchemaFormSchema,
} from "./-components/schemas";
import { UpdateCredentialSchemaDialog } from "./-components/update-credential-schema-dialog";

export const Route = createFileRoute(
  "/_authenticated/credential-schemas/$schemaId",
)({
  loader: async ({ context, params: { schemaId } }) => {
    const [schema] = await Promise.all([
      context.queryClient.ensureQueryData(schemaOptions({ schemaId })),
      context.queryClient.ensureQueryData(
        schemaListOptions({
          statesToExclude: [CredentialSchemaState.Archived],
          includeImages: false,
        }),
      ),
    ]);
    return { crumb: schema.displayName || schema.credentialIdentifier };
  },
  validateSearch: z.object({
    tab: z
      .enum(["attributes", "style", "issuing-settings"])
      .default("attributes"),
  }),
  component: RouteComponent,
});

function RouteComponent() {
  const [publishDialogOpen, setPublishDialogOpen] = useState(false);

  const { tab } = Route.useSearch();
  const { schemaId } = Route.useParams();
  const navigate = Route.useNavigate();
  const intl = useIntl();

  const user = useUser();
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const tenantId = selectedTenant || user.tenantId;
  const canEdit = isEditor(user);

  const queryClient = useQueryClient();
  const { data: schema } = useSuspenseQuery(schemaOptions({ schemaId }));
  const [{ data: allSchemas }, { data: settings }] = useSuspenseQueries({
    queries: [
      schemaListOptions({
        statesToExclude: [CredentialSchemaState.Archived],
        includeImages: false,
        credentialIdentifier: schema.credentialIdentifier,
      }),
      settingsForOrganisationOptions(tenantId),
    ],
  });

  const displayNameLanguages = getDisplayNameLanguages(
    schema.attributes,
    settings.translations,
  );
  const credentialSchemaFormSchema = createCredentialSchemaFormSchema(intl);
  const defaultValues = getDefaultValuesForSchema(schema, displayNameLanguages);
  const setContentLanguage = useSetAtom(contentLanguageAtom);

  useEffect(() => {
    setContentLanguage(settings.defaultLanguage);
  }, [setContentLanguage, settings.defaultLanguage]);

  const form = useAppForm({
    validators: {
      onChange: credentialSchemaFormSchema,
    },
    defaultValues,
    async onSubmit({ value }) {
      await update(value);
    },
    canSubmitWhenInvalid: true,
    onSubmitInvalid({ formApi }) {
      const firstErrorPath = Object.entries(formApi.getAllErrors().fields).find(
        ([, field]) => field.errors.length > 0,
      )?.[0];
      const firstErrorField = firstErrorPath?.split(/[.[\]]/, 1)[0];

      const fieldToTabMap: Partial<
        Record<keyof CredentialSchemaForm, DetailPageTabValues>
      > = {
        attributes: "attributes",
        metaAttributes: "attributes",
        style: "style",
        issuingSettings: "issuing-settings",
      };

      for (const field in fieldToTabMap) {
        if (firstErrorField === field) {
          navigate({
            search: {
              tab: fieldToTabMap[firstErrorField as keyof CredentialSchemaForm],
            },
            replace: true,
            ignoreBlocker: true,
          });
          break;
        }
      }
    },
  });

  useEffect(() => {
    form.reset();
  }, [schema]);

  const templateId = useStore(form.store, (state) => state.values.templateId);
  const [{ data: template }, { data: libraries }] = useQueries({
    queries: [
      {
        ...templateOptions(templateId!),
        enabled: !!templateId,
      },
      {
        ...templateLibraryListOptions(),
        enabled: !!templateId,
      },
    ],
  });

  const { proceed, status, reset } = useBlocker({
    shouldBlockFn: () => !form.state.isDefaultValue,
    withResolver: true,
  });

  const { mutate: createDraft, isPending: isCreating } =
    useCreateNewVersionMutation();
  const { mutateAsync: update, isPending: isUpdating } =
    useUpdateSchemaMutation({
      onSuccess: proceed,
    });

  const isPublished = schema.state === CredentialSchemaState.Published;

  const draft = allSchemas.find(
    (s) => s.state === CredentialSchemaState.Created,
  );

  const publishedSchemaId = isPublished
    ? undefined
    : allSchemas
        .filter((s) => s.state === CredentialSchemaState.Published)
        .sort((a, b) => compare(a.version, b.version))
        .pop()?.id;

  const { data } = useQuery({
    ...schemaOptions({ schemaId: publishedSchemaId! }),
    enabled: !!publishedSchemaId,
  });

  const publishedSchema = isPublished ? schema : data;

  const releaseType = publishedSchema
    ? determineReleaseType(schema, publishedSchema)
    : null;

  const nextMinVersion =
    publishedSchema && releaseType
      ? inc(publishedSchema.version, releaseType)
      : valid(schema.version)
        ? schema.version
        : "1.0.0";

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        void form.handleSubmit();
      }}
    >
      <header className="grid grid-cols-1 gap-3 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <div className="flex justify-between gap-3">
            <div>
              <h1 className="text-3xl font-semibold text-balance hyphens-auto">
                {schema.displayName}
              </h1>
              <h2 className="text-xl text-balance">
                {schema.credentialIdentifier}
              </h2>
            </div>

            {!isPublished && (
              <UpdateCredentialSchemaDialog defaultValues={form.state.values}>
                <Button variant="outline" className="size-10 shrink-0 p-0">
                  <IconPencil className="size-4" />
                </Button>
              </UpdateCredentialSchemaDialog>
            )}
          </div>
          {templateId && (
            <div className="mt-2 flex items-start justify-between gap-6 rounded-xl border border-fuchsia-200 bg-fuchsia-50 p-4 text-fuchsia-600">
              <div className="flex gap-4">
                <IconCornerDownRight />
                <div className="flex flex-col gap-1.5">
                  <h3 className="leading-none font-bold">
                    {template?.displayName ?? (
                      <>
                        <FormattedMessage
                          id="common.loading"
                          defaultMessage="Loading"
                        />
                        ...
                      </>
                    )}
                  </h3>
                  <p className="leading-none">
                    <FormattedMessage
                      id="common.source"
                      defaultMessage="Source"
                    />
                    :{" "}
                    {libraries?.find((l) => l.key === template?.libraryKey)
                      ?.displayName ?? (
                      <>
                        <FormattedMessage
                          id="common.loading"
                          defaultMessage="Loading"
                        />
                        ...
                      </>
                    )}
                  </p>
                </div>
              </div>
              <Badge className="bg-fuchsia-600">
                <FormattedMessage
                  id="common.template"
                  defaultMessage="Template"
                />
              </Badge>
            </div>
          )}
        </Card>
        <Card className="flex flex-col gap-4">
          <div className="flex justify-between">
            <div className="flex items-center gap-2">
              {isPublished ? <IconEye /> : <IconClipboardList />}
              <span className="text-xl font-semibold">
                {isPublished ? (
                  <FormattedMessage
                    id="common.published"
                    defaultMessage="Published"
                  />
                ) : (
                  <FormattedMessage id="common.draft" defaultMessage="Draft" />
                )}
              </span>
            </div>
            {isPublished && (
              <Badge variant="secondary">V {schema.version}</Badge>
            )}
          </div>
          {!canEdit ? null : isPublished ? (
            draft ? (
              <Button variant="tertiary" asChild>
                <Link
                  to="/credential-schemas/$schemaId"
                  params={{
                    schemaId: draft.id,
                  }}
                >
                  <IconPencil />
                  <FormattedMessage
                    id="common.draft.edit"
                    defaultMessage="Edit Draft"
                  />
                </Link>
              </Button>
            ) : (
              <Button
                type="button"
                variant="tertiary"
                onClick={() => createDraft(form.state.values)}
                disabled={isCreating}
              >
                <FormattedMessage
                  id="pages.credentialSchemas.createNewVersion"
                  defaultMessage="Create new Version"
                />
              </Button>
            )
          ) : (
            <div className="flex flex-wrap gap-2">
              <form.Subscribe
                selector={(state) => ({
                  isDefaultValue: state.isDefaultValue,
                  isSubmitting: state.isSubmitting,
                  isValid: state.isValid,
                  submissionAttempts: state.submissionAttempts,
                })}
              >
                {({
                  isDefaultValue,
                  isSubmitting,
                  isValid,
                  submissionAttempts,
                }) => {
                  const isSaving = isSubmitting || isUpdating;
                  const showValidationError =
                    submissionAttempts > 0 && !isValid;

                  return (
                    <>
                      {showValidationError && (
                        <p
                          className="basis-full rounded-xl border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive"
                          role="alert"
                        >
                          <FormattedMessage
                            id="credentialSchema.validation.submit"
                            defaultMessage="Please fix the highlighted fields before saving."
                          />
                        </p>
                      )}
                      <Button
                        type="submit"
                        className="flex-1"
                        value="save"
                        disabled={isDefaultValue || isSaving}
                      >
                        {isSaving && <IconLoader2 className="animate-spin" />}
                        <FormattedMessage
                          id="common.saveChanges"
                          defaultMessage="Save Changes"
                        />
                      </Button>
                      <Button
                        type="button"
                        variant="success"
                        className="flex-1"
                        onClick={() => setPublishDialogOpen(true)}
                        disabled={isSaving || !isDefaultValue}
                      >
                        <IconCloudUpload className="size-4" />
                        <span>
                          <FormattedMessage
                            id="common.publish"
                            defaultMessage="Publish"
                          />
                        </span>
                      </Button>
                    </>
                  );
                }}
              </form.Subscribe>
            </div>
          )}
        </Card>
      </header>
      <Tabs
        value={tab}
        onValueChange={(value) => {
          navigate(tabNavigation(value as DetailPageTabValues));
        }}
        className="mt-9"
      >
        <TabsList>
          <TabsTrigger value="attributes">
            <IconList className="size-4" />
            <FormattedMessage
              id="pages.credentialSchemas.tabs.attributes"
              defaultMessage="Attributes"
            />
          </TabsTrigger>
          <TabsTrigger value="style">
            <IconColorSwatch className="size-4" />
            <FormattedMessage
              id="pages.credentialSchemas.tabs.style"
              defaultMessage="Style"
            />
          </TabsTrigger>
          <TabsTrigger value="issuing-settings">
            <IconSettings className="size-4" />
            <FormattedMessage
              id="pages.credentialSchemas.tabs.issuingSettings"
              defaultMessage="Issuing Settings"
            />
          </TabsTrigger>
        </TabsList>

        <TabsContent value="attributes">
          <Card className="flex flex-col gap-6 sm:p-6">
            <Tabs defaultValue="definitions">
              <div>
                <div className="flex items-center justify-between gap-6">
                  <h3 className="text-2xl leading-none font-semibold">
                    <FormattedMessage
                      id="pages.credentialSchemas.tabs.attributes"
                      defaultMessage="Attributes"
                    />
                  </h3>
                  <TabsList variant="segment">
                    <TabsTrigger value="definitions" variant="segment">
                      <FormattedMessage
                        id="pages.credentialSchemas.tabs.attributes.definitions"
                        defaultMessage="Definitions"
                      />
                    </TabsTrigger>
                    <TabsTrigger value="overrides" variant="segment">
                      <FormattedMessage
                        id="pages.credentialSchemas.tabs.attributes.overrides"
                        defaultMessage="Overrides"
                      />
                    </TabsTrigger>
                  </TabsList>
                </div>
                <TabsContent value="definitions">
                  <div className="space-y-4">
                    <Attributes
                      form={form}
                      displayNameLanguages={displayNameLanguages}
                      template={template}
                    />
                  </div>
                </TabsContent>
                <TabsContent value="overrides">
                  <AttributeOverrides form={form} />
                </TabsContent>
              </div>
            </Tabs>
            <div className="-mx-6 h-px bg-border" />
            <div className="space-y-4">
              <h3 className="text-2xl font-semibold">
                <FormattedMessage
                  id="pages.credentialSchemas.metaAttributes"
                  defaultMessage="Meta Attributes"
                />
              </h3>
              <Attributes
                form={form}
                displayNameLanguages={displayNameLanguages}
                isMetaAttribute
              />
            </div>
          </Card>
        </TabsContent>
        <TabsContent value="style">
          <Card className="@container space-y-4 p-4 sm:space-y-6 sm:p-6">
            <h3 className="text-2xl leading-none font-semibold">
              <FormattedMessage
                id="pages.credentialSchemas.tabs.style"
                defaultMessage="Style"
              />
            </h3>
            <div className="flex gap-8 @max-3xl:flex-col-reverse">
              <div className="flex-1 space-y-8">
                <div className="space-y-3">
                  <h3 className="text-xl leading-none">
                    <FormattedMessage
                      id="pages.credentialSchemas.cardStyle"
                      defaultMessage="Card Style"
                    />
                  </h3>
                  <CardStyleCard form={form} />
                </div>
                <div className="space-y-3">
                  <h3 className="text-xl leading-none">
                    <FormattedMessage
                      id="pages.credentialSchemas.orderedProperties"
                      defaultMessage="Ordered Properties"
                    />
                  </h3>
                  <OrderedProperties form={form} />
                </div>
              </div>
              <div className="space-y-3 sm:w-auto">
                <h3 className="text-xl leading-none">
                  <FormattedMessage
                    id="pages.credentialSchemas.cardPreview"
                    defaultMessage="Card Preview"
                  />
                </h3>
                <CardPreview form={form} />
              </div>
            </div>
          </Card>
        </TabsContent>
        <TabsContent value="issuing-settings">
          <IssuingSettingsTab form={form} />
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
            <Button
              variant="destructive"
              onClick={() => {
                queryClient.invalidateQueries(schemaOptions({ schemaId }));
                proceed?.();
              }}
            >
              <FormattedMessage
                id="common.unsavedChanges.discard"
                defaultMessage="Discard changes"
              />
            </Button>
            <Button type="button" onClick={form.handleSubmit}>
              <FormattedMessage id="common.save" defaultMessage="Save" />
            </Button>
          </div>
        </DialogContent>
      </Dialog>
      {nextMinVersion && (
        <PublishDialog
          key={nextMinVersion}
          schemaId={schema.id}
          open={publishDialogOpen}
          defaultVersion={nextMinVersion}
          onClose={() => setPublishDialogOpen(false)}
        />
      )}
    </form>
  );
}

function determineReleaseType(
  newValues: CredentialSchema,
  oldValues: CredentialSchema,
) {
  let releaseType: ReleaseType = "minor";
  const newAttributes = normalizeAttributes(newValues.attributes);
  const oldAttributes = normalizeAttributes(oldValues.attributes);
  if (JSON.stringify(newAttributes) !== JSON.stringify(oldAttributes)) {
    releaseType = "major";
  }
  return releaseType;
}

function normalizeAttributes(attributes: CredentialSchemaAttribute[]) {
  return attributes
    .sort((a, b) => a.name.localeCompare(b.name))
    .map(({ id, displayName, ...a }) => a);
}

function getDisplayNameLanguages(
  formAttributes:
    | CredentialSchemaForm["attributes"]
    | CredentialSchemaForm["metaAttributes"],
  translations: string[],
) {
  const langs = new Set(translations);
  for (const attr of formAttributes) {
    for (const lang of Object.keys(attr.displayName)) {
      langs.add(lang);
    }
  }
  return Array.from(langs).sort();
}
