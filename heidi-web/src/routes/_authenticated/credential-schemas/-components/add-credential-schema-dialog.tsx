// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconFileImport } from "@tabler/icons-react";
import { useForm, useStore } from "@tanstack/react-form";
import { useQueries, useSuspenseQuery } from "@tanstack/react-query";
import { useAtomValue } from "jotai";
import { customAlphabet } from "nanoid";
import { type ReactNode, useState } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import slugify from "slugify";
import { toast } from "sonner";
import { z } from "zod";
import { EcosystemProfileLabel } from "@/components/common/ecosystem-profile-label";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useCreateSchemaMutation } from "@/lib/api/credential-schemas/mutations";
import { issuerDefinitionListOptions } from "@/lib/api/issuer-definitions/query-options";
import type { Settings } from "@/lib/api/settings/api";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";
import { getTemplate } from "@/lib/api/templates/api";
import {
  templateLibraryListOptions,
  templateListOptions,
} from "@/lib/api/templates/query-options";
import { selectedTenantAtom } from "@/lib/atoms";
import { useUser } from "@/lib/hooks/use-user";
import { credentialSchemaValidationMessages } from "@/lib/translations";
import { cn } from "@/lib/utils";
import { argbToHex } from "@/lib/utils/color";
import { getDefaultValuesForSchema } from "@/lib/utils/credential-schemas";
import { getLocalizedValue } from "@/lib/utils/localized";
import type { CredentialSchemaForm } from "@/routes/_authenticated/credential-schemas/-components/schemas";
import {
  CredentialOfferType,
  type CredentialSchema,
  IssuerKeyTypes,
  OcaVersion,
  SupportedCredentialTypes,
  TextColor,
} from "@/types/credential-schema";
import {
  compatibleIssuanceProfiles,
  type IssuanceProfileId,
  type ProfileOption,
  trustSystemsForIdentity,
} from "@/types/ecosystem-profile";
import { normalizeSigningKeyIds } from "@/types/trust-system";

const nanoid = customAlphabet("1234567890abcdefghijklmnopqrstuvwxyz", 5);

export function AddCredentialSchemaDialog({
  children,
}: {
  children: ReactNode;
}) {
  const user = useUser();
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const tenantId = selectedTenant || user.tenantId;
  const { data: settings } = useSuspenseQuery(
    settingsForOrganisationOptions(tenantId),
  );
  const { data: issuerDefinitions } = useSuspenseQuery(
    issuerDefinitionListOptions({ tenantId }),
  );
  const configuredIssuers = issuerDefinitions.filter((issuer) =>
    settings.issuerIds.includes(issuer.id),
  );
  const [selectedIssuerId, setSelectedIssuerId] = useState<number | undefined>(
    configuredIssuers.length === 1 ? configuredIssuers[0]?.id : undefined,
  );
  const selectedIssuer = configuredIssuers.find(
    (issuer) => issuer.id === selectedIssuerId,
  );
  const profileOptions = selectedIssuer
    ? compatibleIssuanceProfiles(trustSystemsForIdentity(selectedIssuer))
    : [];
  const [selectedProfileId, setSelectedProfileId] = useState<
    IssuanceProfileId | undefined
  >(profileOptions.length === 1 ? profileOptions[0]?.id : undefined);
  const selectedIssuanceProfile = profileOptions.some(
    (option) => option.id === selectedProfileId,
  )
    ? selectedProfileId
    : profileOptions.length === 1
      ? profileOptions[0]?.id
      : undefined;
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const { $t } = useIntl();

  const { mutate: create } = useCreateSchemaMutation();

  function selectIssuer(issuerId: number | undefined) {
    setSelectedIssuerId(issuerId);
    const issuer = configuredIssuers.find((candidate) => candidate.id === issuerId);
    const profiles = issuer
      ? compatibleIssuanceProfiles(trustSystemsForIdentity(issuer))
      : [];
    setSelectedProfileId(profiles.length === 1 ? profiles[0]?.id : undefined);
  }

  async function handleSubmit(values: FormSchema | CredentialSchemaForm) {
    if (selectedIssuerId === undefined) {
      toast.error("Select an issuer identity before creating the schema.");
      return;
    }
    if (!selectedIssuanceProfile) {
      toast.error("Select an ecosystem profile before creating the schema.");
      return;
    }
    const issuerId = selectedIssuerId;
    const isImport = "id" in values && Boolean(values.id);
    const isFromTemplate = "templateId" in values && Boolean(values.templateId);
    const credentialIdentifier = `${slugify(values.displayName, { strict: true, lower: true, locale: "de" })}-${nanoid()}`;
    if (!isFromTemplate || isImport) {
      create(
        {
          displayName: values.displayName,
          maxBatchSize:
            isImport && "maxBatchSize" in values ? values.maxBatchSize : 1,
          credentialIdentifier,
          id: "",
          version: "",
          attributes: isImport ? values.attributes : [],
          metaAttributes: isImport ? values.metaAttributes : [],
          style: isImport
            ? values.style
            : {
                backgroundImage: "",
                cardColor: "#000000",
                textColor: TextColor.Light,
                cardSubtitle: "",
                cardTitle: "",
                orderedProperties: [],
                frontOverlays: [],
                ocaVersion: OcaVersion.Legacy,
              },
          issuingSettings: {
            keyType: IssuerKeyTypes.SOFTWARE_NO_AUTH,
            issuerId,
            issuanceProfileId: selectedIssuanceProfile,
            supportedCredentialTypes: [SupportedCredentialTypes.SdJwt],
            signingKeyIds: {},
            credentialOfferType: CredentialOfferType.Value,
            ...(isImport && {
              ...values.issuingSettings,
              issuerId,
              issuanceProfileId: selectedIssuanceProfile,
              defaultTrustSystem: undefined,
            }),
          },
          ...(isImport && { templateId: values.templateId }),
        },
        { onSuccess: () => setIsDialogOpen(false) },
      );
    } else {
      const template = await getTemplate(values.templateId!);
      create(
        {
          displayName: values.displayName || template.displayName,
          maxBatchSize: 1,
          credentialIdentifier,
          id: "",
          version: "",
          attributes: template.attributes.map((a) => ({
            ...a,
            id: 0,
            isSensitive: a.isSensitive ?? false,
            isDisclosable: a.isDisclosable ?? true,
            isArray: a.isArray ?? false,
          })) as CredentialSchemaForm["attributes"],
          metaAttributes:
            template.metaAttributes?.map((a) => ({
              ...a,
              id: 0,
            })) ?? [],
          style: {
            backgroundImage: template.style?.backgroundCard ?? "",
            cardColor: template.style?.cardColor
              ? argbToHex(template.style?.cardColor)
              : "#000000",
            textColor: template.style?.textColor ?? TextColor.Light,
            cardSubtitle: template.style?.subtitle ?? "",
            cardTitle: template.style?.title ?? "",
            orderedProperties: template.style?.orderedProperties ?? [],
            frontOverlays: template.style?.frontOverlays ?? [],
            ocaVersion: OcaVersion.Legacy,
          },
          issuingSettings: {
            issuerId,
            issuanceProfileId: selectedIssuanceProfile,
            keyType: template.issuerSettings.issuerKeyType,
            doctype: template.issuerSettings.doctype,
            namespace: template.issuerSettings.namespace,
            vct: template.issuerSettings.vct,
            supportedCredentialTypes: template.issuerSettings
              .supportedCredentialTypes ?? [SupportedCredentialTypes.SdJwt],
            signingKeyIds: normalizeSigningKeyIds(
              template.issuerSettings.signingKeyIds,
            ),
            credentialOfferType:
              template.issuerSettings.credentialOfferType ??
              CredentialOfferType.Value,
          },
          templateId: values.templateId,
        },
        { onSuccess: () => setIsDialogOpen(false) },
      );
    }
  }

  return (
    <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="sm:max-w-[525px]">
        <DialogHeader>
          <DialogTitle>
            <FormattedMessage
              id="common.new.withValue"
              defaultMessage="New {value}"
              values={{
                pronoun: "neuter",
                value: $t({
                  id: "common.credentialSchema",
                  defaultMessage: "Credential Schema",
                }),
              }}
            />
          </DialogTitle>
          <DialogDescription>
            <FormattedMessage
              id="pages.credentialSchemas.createNew.description"
              defaultMessage="Create your own credential schema or choose a template"
            />
          </DialogDescription>
        </DialogHeader>
        <SchemaConfiguration
          issuers={configuredIssuers}
          selectedIssuer={selectedIssuer}
          selectedIssuerId={selectedIssuerId}
          onIssuerChange={selectIssuer}
          profiles={profileOptions}
          selectedProfileId={selectedIssuanceProfile}
          onProfileChange={setSelectedProfileId}
          language={settings.defaultLanguage}
        />
        <Tabs defaultValue="own">
          <TabsList variant="segment" className="w-full">
            <TabsTrigger variant="segment" className="w-full" value="own">
              <FormattedMessage
                id="pages.credentialSchemas.createNew.createOwn"
                defaultMessage="Create own"
              />
            </TabsTrigger>
            <TabsTrigger variant="segment" className="w-full" value="template">
              <FormattedMessage
                id="common.templates"
                defaultMessage="Templates"
              />
            </TabsTrigger>
            <TabsTrigger variant="segment" className="w-full" value="import">
              <FormattedMessage id="common.import" defaultMessage="Import" />
            </TabsTrigger>
          </TabsList>
          <TabsContent value="own" className="mt-2">
            <OwnForm onSubmit={handleSubmit} />
          </TabsContent>
          <TabsContent value="template">
            <TemplateForm onSubmit={handleSubmit} />
          </TabsContent>
          <TabsContent value="import">
            <ImportTab onSubmit={handleSubmit} settings={settings} />
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  );
}

function SchemaConfiguration({
  issuers,
  selectedIssuer,
  selectedIssuerId,
  onIssuerChange,
  profiles,
  selectedProfileId,
  onProfileChange,
  language,
}: {
  issuers: readonly {
    id: number;
    slug: string;
    displayName: Record<string, string>;
    customProfileName?: string | null;
  }[];
  selectedIssuer?: {
    customProfileName?: string | null;
  };
  selectedIssuerId?: number;
  onIssuerChange: (issuerId?: number) => void;
  profiles: readonly ProfileOption<IssuanceProfileId>[];
  selectedProfileId?: IssuanceProfileId;
  onProfileChange: (profileId: IssuanceProfileId) => void;
  language: string;
}) {
  return (
    <div className="grid gap-3 rounded-lg border bg-muted/20 p-3">
      <div className="grid grid-cols-[auto_1fr] items-center gap-3">
        <Label htmlFor="schema-issuer">Issuer identity</Label>
        {issuers.length > 0 ? (
          <Select
            value={selectedIssuerId?.toString() ?? ""}
            onValueChange={(value) => onIssuerChange(Number(value))}
          >
            <SelectTrigger id="schema-issuer">
              <SelectValue placeholder="Select identity" />
            </SelectTrigger>
            <SelectContent>
              {issuers.map((issuer) => (
                <SelectItem key={issuer.id} value={issuer.id.toString()}>
                  {getLocalizedValue(issuer.displayName, language) ?? issuer.slug}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : (
          <p className="text-sm text-destructive">No issuer identity is assigned.</p>
        )}
      </div>
      <div className="grid grid-cols-[auto_1fr] items-center gap-3">
        <Label htmlFor="schema-profile">Ecosystem profile</Label>
        {profiles.length > 0 ? (
          <Select
            value={selectedProfileId ?? ""}
            onValueChange={(value) => onProfileChange(value as IssuanceProfileId)}
          >
            <SelectTrigger id="schema-profile">
              <SelectValue placeholder="Select profile" />
            </SelectTrigger>
            <SelectContent>
              {profiles.map((profile) => (
                <SelectItem key={profile.id} value={profile.id}>
                  <EcosystemProfileLabel profile={profile} identity={selectedIssuer} />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : (
          <p className="text-sm text-muted-foreground">
            Select an issuer identity to see compatible profiles.
          </p>
        )}
      </div>
    </div>
  );
}

function ImportTab({
  onSubmit,
  settings,
}: {
  onSubmit: (data: FormSchema) => void;
  settings: Settings;
}) {
  const [schema, setSchema] = useState<CredentialSchema>();
  return (
    <form
      className="flex w-full flex-wrap items-end gap-2"
      onSubmit={async (e) => {
        e.preventDefault();
        if (!schema) {
          toast.error("No schema selected.");
          return;
        }
        const formData = new FormData(e.target as HTMLFormElement);
        const newDisplayName = formData.get("displayName");
        if (
          newDisplayName &&
          typeof newDisplayName === "string" &&
          schema.displayName !== newDisplayName
        ) {
          schema.displayName = newDisplayName;
        }
        try {
          const formSchema = getDefaultValuesForSchema(
            schema,
            settings.translations,
          );
          onSubmit(formSchema);
        } catch (error) {
          if (error instanceof Error) {
            toast.error("Error while importing schema. Is the file valid?", {
              description: error.message,
            });
          } else {
            console.error(error);
          }
        }
      }}
    >
      <Input
        onChange={async (e) => {
          const importFile = e.target.files?.[0];
          if (!importFile || importFile.size === 0 || !importFile.name) {
            toast.error("No file selected.");
            return;
          }

          if (!importFile.type.includes("json")) {
            toast.error("Only JSON files are allowed.");
            return;
          }

          const data = JSON.parse(await importFile.text()) as CredentialSchema;
          setSchema(data);
        }}
        type="file"
        name="import"
        accept=".json"
        className={cn("flex-1", schema && "min-w-full")}
      />
      {schema && (
        <Label className="mt-4 flex flex-1 flex-col gap-2">
          <FormattedMessage
            id="common.displayName"
            defaultMessage="Display Name"
          />
          <Input
            key={schema.displayName}
            type="text"
            name="displayName"
            defaultValue={schema.displayName}
          />
        </Label>
      )}
      <Button type="submit">
        <IconFileImport />
        <FormattedMessage id="common.import" defaultMessage="Import" />
      </Button>
    </form>
  );
}

function createOwnFormSchema(intl: ReturnType<typeof useIntl>) {
  return z.object({
    displayName: z.string().min(1, {
      error: () =>
        intl.$t(
          credentialSchemaValidationMessages.validationSchemaDisplayNameRequired,
        ),
    }),
  });
}

function createTemplateFormSchema(intl: ReturnType<typeof useIntl>) {
  return createOwnFormSchema(intl).extend({
    libraryKey: z.string(),
    templateId: z.string(),
  });
}

type OwnFormSchema = z.input<ReturnType<typeof createOwnFormSchema>>;
type TemplateFormSchema = z.input<ReturnType<typeof createTemplateFormSchema>>;
type FormSchema = OwnFormSchema | TemplateFormSchema;

function OwnForm({ onSubmit }: { onSubmit: (data: OwnFormSchema) => void }) {
  const intl = useIntl();
  const { $t } = intl;
  const ownFormSchema = createOwnFormSchema(intl);

  const form = useForm({
    defaultValues: { displayName: "" } as OwnFormSchema,
    validators: {
      onSubmit: ownFormSchema,
    },
    onSubmit({ value }) {
      onSubmit(value);
    },
  });

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        form.handleSubmit();
      }}
    >
      <div className="pt-4 pb-6">
        <form.Field name="displayName">
          {(field) => {
            const isInvalid =
              field.state.meta.isTouched && !field.state.meta.isValid;
            return (
              <Field orientation="horizontal" data-invalid={isInvalid}>
                <FieldLabel htmlFor={field.name}>
                  <FormattedMessage
                    id="common.displayName"
                    defaultMessage="Display Name"
                  />
                </FieldLabel>
                <Input
                  type="text"
                  id={field.name}
                  name={field.name}
                  value={field.state.value}
                  onChange={(e) => field.handleChange(e.target.value)}
                  onBlur={field.handleBlur}
                  aria-invalid={isInvalid}
                  placeholder={$t(
                    {
                      id: "common.enter.withValue",
                      defaultMessage: "Enter {value}",
                    },
                    {
                      value: $t({
                        id: "common.displayName",
                        defaultMessage: "Display Name",
                      }),
                    },
                  )}
                />
                {isInvalid && <FieldError errors={field.state.meta.errors} />}
              </Field>
            );
          }}
        </form.Field>
      </div>
      <FormFooter />
    </form>
  );
}

function TemplateForm({
  onSubmit,
}: {
  onSubmit: (data: TemplateFormSchema) => void;
}) {
  const intl = useIntl();
  const { $t } = intl;
  const templateFormSchema = createTemplateFormSchema(intl);
  const [{ data: libraries }, { data: templates }] = useQueries({
    queries: [templateLibraryListOptions(), templateListOptions()],
  });

  const form = useForm({
    defaultValues: {
      displayName: "",
      libraryKey: libraries && libraries.length === 1 ? libraries[0]?.key : "",
      templateId: "",
    } as TemplateFormSchema,
    validators: {
      onSubmit: templateFormSchema,
    },
    onSubmit({ value }) {
      onSubmit(value);
    },
  });

  const libraryKey = useStore(form.store, (state) => state.values.libraryKey);

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        form.handleSubmit();
      }}
    >
      <div className="flex flex-col gap-6 pt-2 pb-6">
        <form.Field name="displayName">
          {(field) => {
            const isInvalid =
              field.state.meta.isTouched && !field.state.meta.isValid;
            return (
              <Field orientation="horizontal" data-invalid={isInvalid}>
                <FieldLabel htmlFor={field.name}>
                  <FormattedMessage
                    id="common.displayName"
                    defaultMessage="Display Name"
                  />
                </FieldLabel>
                <Input
                  type="text"
                  id={field.name}
                  name={field.name}
                  value={field.state.value}
                  onChange={(e) => field.handleChange(e.target.value)}
                  onBlur={field.handleBlur}
                  aria-invalid={isInvalid}
                  placeholder={$t(
                    {
                      id: "common.enter.withValue",
                      defaultMessage: "Enter {value}",
                    },
                    {
                      value: $t({
                        id: "common.displayName",
                        defaultMessage: "Display Name",
                      }),
                    },
                  )}
                />
                {isInvalid && <FieldError errors={field.state.meta.errors} />}
              </Field>
            );
          }}
        </form.Field>
        <form.Field name="libraryKey">
          {(field) => {
            const isInvalid =
              field.state.meta.isTouched && !field.state.meta.isValid;
            return (
              <Field orientation="horizontal" data-invalid={isInvalid}>
                <FieldLabel htmlFor={field.name}>
                  <FormattedMessage
                    id="common.library"
                    defaultMessage="Library"
                  />
                </FieldLabel>
                <Select
                  value={field.state.value}
                  onValueChange={(v) => {
                    field.handleChange(v);
                    form.setFieldValue("templateId", "");
                  }}
                >
                  <SelectTrigger id={field.name} aria-invalid={isInvalid}>
                    <SelectValue
                      placeholder={$t(
                        {
                          id: "common.select.withValue",
                          defaultMessage: "Select {value}",
                        },
                        {
                          value: $t({
                            id: "common.library",
                            defaultMessage: "Library",
                          }),
                        },
                      )}
                    />
                  </SelectTrigger>
                  <SelectContent>
                    {libraries?.map((lib) => (
                      <SelectItem key={lib.key} value={lib.key}>
                        {lib.displayName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {isInvalid && <FieldError errors={field.state.meta.errors} />}
              </Field>
            );
          }}
        </form.Field>
        <form.Field name="templateId">
          {(field) => {
            const isInvalid =
              field.state.meta.isTouched && !field.state.meta.isValid;
            return (
              <Field orientation="horizontal" data-invalid={isInvalid}>
                <FieldLabel htmlFor={field.name}>
                  <FormattedMessage
                    id="common.template"
                    defaultMessage="Template"
                  />
                </FieldLabel>
                <Select
                  value={field.state.value}
                  onValueChange={field.handleChange}
                >
                  <SelectTrigger id={field.name} aria-invalid={isInvalid}>
                    <SelectValue
                      placeholder={$t(
                        {
                          id: "common.select.withValue",
                          defaultMessage: "Select {value}",
                        },
                        {
                          value: $t({
                            id: "common.template",
                            defaultMessage: "Template",
                          }),
                        },
                      )}
                    />
                  </SelectTrigger>
                  <SelectContent>
                    {templates
                      ?.filter((template) => template.libraryKey === libraryKey)
                      .map((template) => (
                        <SelectItem key={template.id} value={template.id}>
                          {template.displayName}
                        </SelectItem>
                      ))}
                  </SelectContent>
                </Select>
                {isInvalid && <FieldError errors={field.state.meta.errors} />}
              </Field>
            );
          }}
        </form.Field>
      </div>
      <FormFooter />
    </form>
  );
}

function FormFooter() {
  return (
    <DialogFooter>
      <DialogClose asChild>
        <Button type="button" variant="outline">
          <FormattedMessage id="common.cancel" defaultMessage="Cancel" />
        </Button>
      </DialogClose>
      <Button type="submit">
        <FormattedMessage id="common.save" defaultMessage="Save" />
      </Button>
    </DialogFooter>
  );
}
