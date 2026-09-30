// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconLanguage } from "@tabler/icons-react";
import { useForm } from "@tanstack/react-form";
import { useSuspenseQueries } from "@tanstack/react-query";
import { createFileRoute, getRouteApi } from "@tanstack/react-router";
import ISO6391 from "iso-639-1";
import { useAtomValue } from "jotai";
import { useEffect, useState } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { toast } from "sonner";
import { z } from "zod";
import { PageHeader } from "@/components/common/page-header";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Field,
  FieldError,
  FieldGroup,
  FieldLabel,
  FieldLegend,
  FieldSet,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
} from "@/components/ui/select";
import { useUpdateOrganisationFeaturesMutation } from "@/lib/api/organisation-features/mutations";
import { organisationFeaturesOptions } from "@/lib/api/organisation-features/query-options";
import { useUpdateSettingsMutation } from "@/lib/api/settings/mutations";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";
import { selectedTenantAtom } from "@/lib/atoms";
import { UserRole } from "@/lib/auth/identity";
import { DEFAULT_LOCALE } from "@/lib/constants";
import { useUser } from "@/lib/hooks/use-user";
import {
  getOrganisationFeatureGroups,
  normalizeOrganisationFeatures,
  type OrganisationFeatures,
} from "@/lib/organisation-features";
import { isLanguageTag } from "@/lib/utils/localized";

export const Route = createFileRoute("/_authenticated/settings/")({
  component: RouteComponent,
});

const routeApi = getRouteApi("/_authenticated/settings");

const iso6391Names = ISO6391.getAllNames();

function languageName(code: string) {
  try {
    return new Intl.DisplayNames(["en"], { type: "language" }).of(code) ?? code;
  } catch {
    return code;
  }
}

const organisationFeaturesSchema = z.object({
  credentialSchemas: z.boolean(),
  proofSchemas: z.boolean(),
  integrations: z.boolean(),
  apiDocs: z.boolean(),
  user: z.boolean(),
  organisation: z.boolean(),
  settings: z.boolean(),
}).catchall(z.boolean());

type FormValues = {
  translations: string[];
  defaultLanguage: string;
  features: z.infer<typeof organisationFeaturesSchema>;
};

function buildSettingsFormValues({
  settings,
  features,
}: {
  settings: {
    translations: string[];
    defaultLanguage?: string;
  };
  features: OrganisationFeatures;
}): FormValues {
  return {
    translations: settings.translations,
    defaultLanguage:
      settings.defaultLanguage ?? settings.translations[0] ?? DEFAULT_LOCALE,
    features: normalizeOrganisationFeatures(features),
  };
}

function RouteComponent() {
  const { crumb } = routeApi.useLoaderData();
  const user = useUser();
  const { $t } = useIntl();
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const [newLanguage, setNewLanguage] = useState("");
  const tenantId = user.roles.includes(UserRole.SuperAdmin)
    ? selectedTenant || user.tenantId
    : user.tenantId;
  const [{ data: settings }, { data: organisationFeatures }] =
    useSuspenseQueries({
      queries: [
        settingsForOrganisationOptions(tenantId),
        organisationFeaturesOptions(tenantId),
      ],
    });
  const hasForcedFeatureMismatch =
    !organisationFeatures.organisation || !organisationFeatures.settings;
  const { mutateAsync: updateSettings } = useUpdateSettingsMutation({
    showToast: false,
  });
  const { mutateAsync: updateOrganisationFeatures } =
    useUpdateOrganisationFeaturesMutation({ showToast: false });
  const formSchema = z.object({
    translations: z
      .array(z.string())
      .refine((value) => value.some((item) => item), {
        message: $t({
          id: "pages.settings.validation.translationsRequired",
        }),
      }),
    defaultLanguage: z.string().min(1),
    features: organisationFeaturesSchema,
  }).refine(
    ({ translations, defaultLanguage }) => translations.includes(defaultLanguage),
    { path: ["defaultLanguage"], message: $t({
      id: "pages.settings.validation.defaultLanguageRequired",
    }) },
  );

  const form = useForm({
    defaultValues: buildSettingsFormValues({
      settings,
      features: organisationFeatures,
    }),
    validators: {
      onSubmit: formSchema,
    },
    async onSubmit({ value, formApi }) {
      const shouldUpdateTranslations =
        !formApi.getFieldMeta("translations")?.isDefaultValue;
      const shouldUpdateDefaultLanguage =
        !formApi.getFieldMeta("defaultLanguage")?.isDefaultValue;
      const shouldUpdateFeatures =
        !formApi.getFieldMeta("features")?.isDefaultValue;

      if (
        !shouldUpdateTranslations &&
        !shouldUpdateDefaultLanguage &&
        !shouldUpdateFeatures
      ) {
        return;
      }

      try {
        await Promise.all(
          [
            (shouldUpdateTranslations || shouldUpdateDefaultLanguage) &&
              updateSettings({
                tenantId: settings.tenantId,
                translations: value.translations,
                defaultLanguage: value.defaultLanguage,
                thumbnail: settings.thumbnail,
              }),
            shouldUpdateFeatures &&
              updateOrganisationFeatures({
                tenantId: settings.tenantId,
                features: normalizeOrganisationFeatures(value.features),
              }),
          ].filter(Boolean),
        );

        toast.success(
          $t({
            id: "pages.settings.toast.update.success",
            defaultMessage: "Settings updated.",
          }),
        );
      } catch (error) {
        toast.error(
          $t({
            id: "pages.settings.toast.update.error",
            defaultMessage: "Update was not successful.",
          }),
          {
            description: (error as Error).message,
          },
        );
      }
    },
  });

  useEffect(() => {
    form.reset(
      buildSettingsFormValues({
        settings,
        features: organisationFeatures,
      }),
    );
  }, [form, organisationFeatures, settings]);

  return (
    <>
      <PageHeader heading={crumb} />
      <Card>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            form.handleSubmit();
          }}
        >
          <div>
            <h2 className="text-2xl font-semibold">
              <FormattedMessage
                id="pages.settings.cockpit"
                defaultMessage="Cockpit"
              />
            </h2>
          </div>
          <div className="mt-6 flex flex-col gap-3">
            <form.Field name="translations" mode="array">
              {(field) => {
                const isInvalid =
                  field.state.meta.isTouched && !field.state.meta.isValid;
                return (
                  <FieldSet>
                    <FieldLegend variant="label">
                      <FormattedMessage
                        id="pages.settings.translations"
                      />
                    </FieldLegend>
                    <Select
                      onValueChange={(value) => {
                        const langCode = ISO6391.getCode(value);
                        if (!field.state.value.includes(langCode)) {
                          field.pushValue(langCode);
                        }
                      }}
                    >
                      <SelectTrigger asChild>
                        <Button
                          variant="outline"
                          className="self-start bg-card"
                        >
                          <IconLanguage />
                          <FormattedMessage
                            id="pages.settings.addLanguage"
                          />
                        </Button>
                      </SelectTrigger>
                      <SelectContent>
                        {iso6391Names.map((name) => (
                          <SelectItem
                            withIndicator={false}
                            value={name}
                            key={name}
                          >
                            {name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <div className="flex max-w-md gap-2">
                      <Input
                        value={newLanguage}
                        placeholder={$t({
                          id: "pages.settings.languageCode.placeholder",
                        })}
                        aria-label={$t({
                          id: "pages.settings.languageCode",
                        })}
                        onChange={(event) => setNewLanguage(event.target.value)}
                      />
                      <Button
                        type="button"
                        variant="outline"
                        disabled={!isLanguageTag(newLanguage)}
                        onClick={() => {
                          const language = new Intl.Locale(newLanguage.trim()).toString();
                          if (!field.state.value.includes(language)) {
                            field.pushValue(language);
                          }
                          setNewLanguage("");
                        }}
                      >
                        <FormattedMessage
                          id="pages.settings.addLanguage"
                        />
                      </Button>
                    </div>
                    <FieldGroup
                      data-slot="checkbox-group"
                      className="flex-row flex-wrap *:w-auto"
                    >
                      {field.state.value.map((langCode) => (
                        <Field
                          key={langCode}
                          orientation="inline"
                          data-invalid={isInvalid}
                        >
                          <Checkbox
                            id={`translations-${langCode}`}
                            name={field.name}
                            aria-invalid={isInvalid}
                            checked={field.state.value.includes(langCode)}
                            disabled={
                              langCode === form.state.values.defaultLanguage
                            }
                            onCheckedChange={(checked) => {
                              if (checked) {
                                field.pushValue(langCode);
                              } else {
                                const index =
                                  field.state.value.indexOf(langCode);
                                if (index > -1) {
                                  field.removeValue(index);
                                }
                              }
                            }}
                          />
                          <FieldLabel
                            htmlFor={`translations-${langCode}`}
                            className="font-normal whitespace-nowrap"
                          >
                            {languageName(langCode)} ({langCode})
                          </FieldLabel>
                        </Field>
                      ))}
                    </FieldGroup>
                    {isInvalid && (
                      <FieldError errors={field.state.meta.errors} />
                    )}
                  </FieldSet>
                );
              }}
            </form.Field>
            <form.Field name="defaultLanguage">
              {(field) => (
                <FieldSet>
                  <FieldLegend variant="label">
                    <FormattedMessage
                      id="pages.settings.defaultLanguage"
                    />
                  </FieldLegend>
                  <Select
                    value={field.state.value}
                    onValueChange={field.handleChange}
                  >
                    <SelectTrigger className="max-w-md">
                      {languageName(field.state.value)} ({field.state.value})
                    </SelectTrigger>
                    <SelectContent>
                      {form.state.values.translations.map((langCode) => (
                        <SelectItem value={langCode} key={langCode}>
                          {languageName(langCode)} ({langCode})
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {field.state.meta.isTouched && !field.state.meta.isValid && (
                    <FieldError errors={field.state.meta.errors} />
                  )}
                </FieldSet>
              )}
            </form.Field>
          </div>
          <hr className="my-4" />
          <form.Field name="features">
            {(field) => (
              <FieldSet>
                <FieldLegend variant="label">
                  <FormattedMessage
                    id="pages.settings.features"
                    defaultMessage="Features"
                  />
                </FieldLegend>
                <div className="grid lg:grid-cols-2 xl:grid-cols-3">
                  {getOrganisationFeatureGroups().map((group) => {
                    const featureKeys = [
                      ...new Set(group.features.map((feature) => feature.key)),
                    ];
                    const writableFeatureKeys = [
                      ...new Set(
                        group.features
                          .filter((feature) => !feature.readOnly)
                          .map((feature) => feature.key),
                      ),
                    ];
                    const allChecked = featureKeys.every(
                      (key) => field.state.value[key],
                    );
                    const someChecked = featureKeys.some(
                      (key) => field.state.value[key],
                    );

                    return (
                      <div key={group.key} className="flex flex-col gap-3 p-4">
                        <Field orientation="inline">
                          <Checkbox
                            id={`feature-group-${group.key}`}
                            checked={
                              allChecked
                                ? true
                                : someChecked
                                  ? "indeterminate"
                                  : false
                            }
                            disabled={writableFeatureKeys.length === 0}
                            onCheckedChange={(checked) => {
                              const nextValue = checked === true;
                              field.handleChange((prev) => ({
                                ...prev,
                                ...Object.fromEntries(
                                  writableFeatureKeys.map((key) => [
                                    key,
                                    nextValue,
                                  ]),
                                ),
                              }));
                            }}
                          />
                          <FieldLabel
                            htmlFor={`feature-group-${group.key}`}
                            className="font-semibold"
                          >
                            <FormattedMessage
                              id={group.labelId}
                              defaultMessage={group.defaultMessage}
                            />
                          </FieldLabel>
                        </Field>
                        <div className="flex flex-col gap-3 pl-7">
                          {group.features.map((feature) => (
                            <Field
                              key={`${group.key}-${feature.key}`}
                              orientation="inline"
                            >
                              <Checkbox
                                id={`feature-${group.key}-${feature.key}`}
                                checked={field.state.value[feature.key]}
                                disabled={feature.readOnly}
                                onCheckedChange={(checked) => {
                                  if (feature.readOnly) {
                                    return;
                                  }
                                  field.handleChange((prev) => ({
                                    ...prev,
                                    [feature.key]: checked === true,
                                  }));
                                }}
                              />
                              <FieldLabel
                                htmlFor={`feature-${group.key}-${feature.key}`}
                                className="font-normal"
                              >
                                <FormattedMessage
                                  id={feature.labelId}
                                  defaultMessage={feature.defaultMessage}
                                />
                              </FieldLabel>
                            </Field>
                          ))}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </FieldSet>
            )}
          </form.Field>
          <div className="-mx-4 mt-4 -mb-4 flex border-t px-4 py-4">
            <form.Subscribe selector={(state) => state.isDefaultValue}>
              {(isDefaultValue) => (
                <Button
                  type="submit"
                  className="ml-auto"
                  disabled={isDefaultValue && !hasForcedFeatureMismatch}
                >
                  <FormattedMessage id="common.save" defaultMessage="Save" />
                </Button>
              )}
            </form.Subscribe>
          </div>
        </form>
      </Card>
    </>
  );
}
