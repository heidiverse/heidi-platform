// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconPhotoUp } from "@tabler/icons-react";
import { useForm } from "@tanstack/react-form";
import { useSuspenseQueries } from "@tanstack/react-query";
import { createFileRoute, getRouteApi } from "@tanstack/react-router";
import { useAtomValue } from "jotai";
import { useEffect } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { toast } from "sonner";
import { z } from "zod";
import { PageHeader } from "@/components/common/page-header";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  Field,
  FieldDescription,
  FieldError,
  FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useUpdateSettingsMutation } from "@/lib/api/settings/mutations";
import {
  settingsForOrganisationOptions,
  walletCatalogOptions,
} from "@/lib/api/settings/query-options";
import { selectedTenantAtom } from "@/lib/atoms";
import { UserRole } from "@/lib/auth/identity";
import { useUser } from "@/lib/hooks/use-user";
import { cn } from "@/lib/utils";
import { WalletConfigurationCard } from "@/routes/_authenticated/organisation/-components/wallet-configuration-card";

export const Route = createFileRoute("/_authenticated/organisation/")({
  component: RouteComponent,
});

const routeApi = getRouteApi("/_authenticated/organisation");

const formSchema = z.object({
  organisationName: z.string().min(1, "You have to enter a name."),
  thumbnail: z.string().nullable(),
});

type FormValues = z.infer<typeof formSchema>;

function buildOrganisationFormValues({
  organisationName,
  settings,
}: {
  organisationName: string;
  settings: {
    thumbnail: string | null;
  };
}): FormValues {
  return {
    organisationName,
    thumbnail: settings.thumbnail,
  };
}

function hasOrganisationSettingsChanges(
  value: FormValues,
  settings: {
    tenantId: string;
    displayName: string | null;
    thumbnail: string | null;
  },
) {
  return !(
    value.organisationName ===
      (settings.displayName ?? settings.tenantId) &&
    (value.thumbnail ?? "") === (settings.thumbnail ?? "")
  );
}

function RouteComponent() {
  const { crumb } = routeApi.useLoaderData();
  const user = useUser();
  const { $t } = useIntl();
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const tenantId = user.roles.includes(UserRole.SuperAdmin)
    ? selectedTenant || user.tenantId
    : user.tenantId;
  const [
    { data: settings },
    { data: walletCatalog },
  ] = useSuspenseQueries({
    queries: [
      settingsForOrganisationOptions(tenantId),
      walletCatalogOptions(),
    ],
  });
  const { mutateAsync: updateSettings } = useUpdateSettingsMutation({
    showToast: false,
  });

  const form = useForm({
    defaultValues: buildOrganisationFormValues({
      organisationName: settings.displayName ?? settings.tenantId,
      settings,
    }),
    validators: {
      onSubmit: formSchema,
    },
    async onSubmit({ value }) {
      const shouldUpdateSettings = hasOrganisationSettingsChanges(
        value,
        settings,
      );

      if (!shouldUpdateSettings) {
        return;
      }

      try {
        await updateSettings({
          tenantId: settings.tenantId,
          displayName: value.organisationName,
          thumbnail: value.thumbnail,
        });

        toast.success(
          $t({
            id: "pages.organisation.toast.update.success",
            defaultMessage: "Organisation updated.",
          }),
        );
      } catch (error) {
        toast.error(
          $t({
            id: "pages.organisation.toast.update.error",
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
      buildOrganisationFormValues({
        organisationName: settings.displayName ?? settings.tenantId,
        settings,
      }),
    );
  }, [form, settings]);

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
          <div className="flex justify-between gap-3">
            <div>
              <h2 className="text-2xl font-semibold">
                <FormattedMessage
                  id="pages.organisation.title"
                  defaultMessage="Organisation Profile"
                />
              </h2>
              <p className="mt-1.5 text-muted-foreground">
                <FormattedMessage
                  id="pages.organisation.lead"
                  defaultMessage="This information will be displayed in the wallet when users interact with you."
                />
              </p>
            </div>
            <form.Field name="thumbnail">
              {(field) => (
                <label
                  htmlFor={field.name}
                  className="grid size-12 shrink-0 rounded-lg border bg-white p-px"
                >
                  {field.state.value ? (
                    <img
                      src={field.state.value}
                      className="aspect-square rounded-md object-cover [grid-area:1/1]"
                      alt="Organisation Thumbnail"
                    />
                  ) : (
                    <div className="rounded-md bg-input [grid-area:1/1]" />
                  )}
                </label>
              )}
            </form.Field>
          </div>
          <div className="mt-6 flex flex-col gap-3">
            <form.Field name="organisationName">
              {(field) => {
                const isInvalid =
                  field.state.meta.isTouched && !field.state.meta.isValid;
                return (
                  <Field data-invalid={isInvalid}>
                    <FieldLabel htmlFor={field.name}>
                      <FormattedMessage
                        id="pages.setting.organisationName"
                        defaultMessage="Organisation Name"
                      />
                    </FieldLabel>
                    <Input
                      id={field.name}
                      name={field.name}
                      value={field.state.value}
                      onBlur={field.handleBlur}
                      onChange={(e) => field.handleChange(e.target.value)}
                      aria-invalid={isInvalid}
                      type="text"
                    />
                    {isInvalid && (
                      <FieldError errors={field.state.meta.errors} />
                    )}
                  </Field>
                );
              }}
            </form.Field>
            <form.Field name="thumbnail">
              {(field) => {
                const isInvalid =
                  field.state.meta.isTouched && !field.state.meta.isValid;
                return (
                  <Field data-invalid={isInvalid}>
                    <FieldLabel htmlFor={field.name}>
                      <FormattedMessage
                        id="pages.setting.thumbnail"
                        defaultMessage="Thumbnail"
                      />
                    </FieldLabel>

                    <Input
                      type="file"
                      accept="image/png, image/jpeg"
                      className="peer sr-only fixed"
                      id={field.name}
                      name={field.name}
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        if (file) {
                          const reader = new FileReader();
                          reader.onload = () => {
                            field.handleChange(reader.result as string);
                          };
                          reader.readAsDataURL(file);
                        }
                      }}
                      onBlur={field.handleBlur}
                      aria-invalid={isInvalid}
                    />
                    <Label
                      className={cn(
                        "flex h-10 items-center gap-2 rounded-full border p-3",
                        isInvalid && "border-destructive text-destructive",
                      )}
                      htmlFor={field.name}
                    >
                      <IconPhotoUp className="size-4 shrink-0" />
                      {field.state.value ? (
                        <FormattedMessage
                          id="pages.setting.thumbnail.chooseNew"
                          defaultMessage="Choose new thumbnail"
                        />
                      ) : (
                        <FormattedMessage
                          id="pages.setting.thumbnail.chooseFile"
                          defaultMessage="Choose a file"
                        />
                      )}
                    </Label>
                    <FieldDescription>
                      <FormattedMessage
                        id="pages.setting.thumbnail.recommendation"
                        defaultMessage="An image size of 240 x 240 px is recommended."
                      />
                    </FieldDescription>
                    {isInvalid && (
                      <FieldError errors={field.state.meta.errors} />
                    )}
                  </Field>
                );
              }}
            </form.Field>
          </div>
          <div className="-mx-4 mt-4 -mb-4 flex border-t px-4 py-4">
            <form.Subscribe selector={(state) => state.isDefaultValue}>
              {(isDefaultValue) => (
                <Button
                  type="submit"
                  className="ml-auto"
                  disabled={isDefaultValue}
                >
                  <FormattedMessage id="common.save" defaultMessage="Save" />
                </Button>
              )}
            </form.Subscribe>
          </div>
        </form>
      </Card>
      <WalletConfigurationCard settings={settings} catalog={walletCatalog} />
    </>
  );
}
