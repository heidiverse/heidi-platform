// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconChevronDown, IconLoader2, IconX } from "@tabler/icons-react";
import { useForm } from "@tanstack/react-form";
import { useQuery } from "@tanstack/react-query";
import { useAtomValue } from "jotai";
import { type ReactNode, useEffect, useState } from "react";
import { flushSync } from "react-dom";
import { FormattedMessage, useIntl } from "react-intl";
import { lt, valid } from "semver";
import { z } from "zod";
import {
  ComboboxSchemaItem,
  CredentialSchemaCombobox,
} from "@/components/common/credential-schema-combobox";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
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
import {
  Field,
  FieldError,
  FieldGroup,
  FieldLabel,
  FieldLegend,
  FieldSet,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { schemaListOptions } from "@/lib/api/credential-schemas/query-options";
import type { IntegrationPayload } from "@/lib/api/integrations/api";
import {
  useCreateIntegrationMutation,
  useDeleteIntegrationMutation,
  useUpdateIntegrationMutation,
} from "@/lib/api/integrations/mutations";
import { selectedTenantAtom } from "@/lib/atoms";
import {
  type CredentialSchemaLite,
  CredentialSchemaState,
} from "@/types/credential-schema";

const scopes = ["issue", "verify"] as const;

function createIntegrationFormSchema(intl: ReturnType<typeof useIntl>) {
  return z.object({
    id: z.string(),
    displayName: z.string().min(1, {
      error: () =>
        intl.$t({
          id: "integration.validation.displayNameRequired",
          defaultMessage: "Display Name is required",
        }),
    }),
    tenantId: z.string(),
    credentialIdentifiers: z.array(z.string()).min(1, {
      error: () =>
        intl.$t({
          id: "integration.validation.credentialSchemaRequired",
          defaultMessage: "At least one Credential Schema is required",
        }),
    }),
    scopes: z.array(z.enum(scopes)).nullable(),
  });
}

type IntegrationFormSchema = z.input<
  ReturnType<typeof createIntegrationFormSchema>
>;

export function IntegrationDialog({
  children,
  defaultValues,
}: {
  children: ReactNode;
  defaultValues?: IntegrationPayload;
}) {
  const intl = useIntl();
  const { $t } = intl;
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const { mutate: createIntegration, isPending: isCreating } =
    useCreateIntegrationMutation();
  const { mutate: updateIntegration, isPending: isUpdating } =
    useUpdateIntegrationMutation();
  const { mutate: deleteIntegration, isPending: isDeleting } =
    useDeleteIntegrationMutation();

  const isPending = isCreating || isUpdating;

  const [isComboboxOpen, setIsComboboxOpen] = useState(false);
  const integrationFormSchema = createIntegrationFormSchema(intl);
  const form = useForm({
    defaultValues:
      defaultValues ??
      ({
        id: "",
        displayName: "",
        tenantId: "",
        credentialIdentifiers: [],
        scopes: ["issue"],
      } as IntegrationFormSchema),
    validators: {
      onSubmit: integrationFormSchema,
    },
    onSubmit({ value: data }) {
      if (defaultValues) {
        updateIntegration(data as IntegrationPayload, {
          onSuccess() {
            setIsDialogOpen(false);
          },
        });
      } else {
        createIntegration(
          {
            ...data,
            id: "",
            tenantId: selectedTenant || "",
          },
          {
            onSuccess() {
              setIsDialogOpen(false);
            },
          },
        );
      }
    },
  });
  const { data: schemasGroupedByIdentifier = [] } = useQuery({
    ...schemaListOptions({
      includeImages: false,
      statesToExclude: [
        CredentialSchemaState.Archived,
        CredentialSchemaState.Created,
      ],
    }),
    select(data) {
      const schemaMap = new Map<string, CredentialSchemaLite>();
      for (const schema of data) {
        const existingSchema = schemaMap.get(schema.credentialIdentifier);
        if (!existingSchema) {
          valid(schema.version) &&
            schemaMap.set(schema.credentialIdentifier, schema);
        } else if (
          valid(schema.version) &&
          valid(existingSchema.version) &&
          lt(existingSchema.version, schema.version)
        ) {
          schemaMap.set(schema.credentialIdentifier, schema);
        }
      }
      return Array.from(schemaMap.values());
    },
  });

  const credentialSchemas = selectedTenant
    ? schemasGroupedByIdentifier.filter(
        (schema) => schema.tenantId === selectedTenant,
      )
    : schemasGroupedByIdentifier;

  useEffect(() => {
    form.reset(defaultValues);
  }, [defaultValues, isDialogOpen]);

  return (
    <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            form.handleSubmit();
          }}
          className="min-w-0"
        >
          <DialogHeader>
            <DialogTitle>
              {defaultValues ? (
                <FormattedMessage
                  id="common.edit.withValue"
                  defaultMessage="Edit {value}"
                  values={{
                    value: $t({
                      id: "common.integration",
                      defaultMessage: "Integration",
                    }),
                  }}
                />
              ) : (
                <FormattedMessage
                  id="common.new.withValue"
                  defaultMessage="New {value}"
                  values={{
                    pronoun: "feminine",
                    value: $t({
                      id: "common.integration",
                      defaultMessage: "Integration",
                    }),
                  }}
                />
              )}
            </DialogTitle>
            <DialogDescription>
              {defaultValues ? (
                <FormattedMessage
                  id="integration.updateDialog.description"
                  defaultMessage="Update the name and Credential Schemas of your integration."
                />
              ) : (
                <FormattedMessage
                  id="integration.createDialog.description"
                  defaultMessage="Give your Integration a name and select which Credential Schemas should be associated with this integration."
                />
              )}
            </DialogDescription>
          </DialogHeader>
          <div className="my-4 flex flex-col gap-6">
            <form.Field name="displayName">
              {(field) => {
                const isInvalid =
                  field.state.meta.isTouched && !field.state.meta.isValid;
                return (
                  <Field data-invalid={isInvalid}>
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
                    {isInvalid && (
                      <FieldError errors={field.state.meta.errors} />
                    )}
                  </Field>
                );
              }}
            </form.Field>
            <form.Field name="credentialIdentifiers" mode="array">
              {(field) => {
                const isInvalid =
                  field.state.meta.isTouched && !field.state.meta.isValid;
                const [existingSelectedSchemaItems, deletedSelectedSchemaItems] =
                  field.state.value.reduce(
                    (acc, schemaIdentifier, index) => {
                      const schema = credentialSchemas.find(
                        (s) => s.credentialIdentifier === schemaIdentifier,
                      );
                      if (schema) {
                        acc[0].push({ index, schemaIdentifier, schema });
                      } else {
                        acc[1].push({ index, schemaIdentifier });
                      }
                      return acc;
                    },
                    [
                      [] as Array<{
                        index: number;
                        schemaIdentifier: string;
                        schema: CredentialSchemaLite;
                      }>,
                      [] as Array<{
                        index: number;
                        schemaIdentifier: string;
                      }>,
                    ],
                  );
                return (
                  <Field data-invalid={isInvalid}>
                    <FieldLabel htmlFor={field.name}>
                      <FormattedMessage
                        id="pages.credentialSchemas"
                        defaultMessage="Credential Schemas"
                      />
                    </FieldLabel>
                    <CredentialSchemaCombobox
                      hideVersion
                      schemas={credentialSchemas.filter(
                        (schema) =>
                          !field.state.value.find(
                            (s) => s === schema.credentialIdentifier,
                          ),
                      )}
                      buttonProps={{
                        id: field.name,
                        name: field.name,
                        onBlur: field.handleBlur,
                        "aria-invalid": isInvalid,
                        type: "button",
                        variant: "outline",
                        className:
                          "bg-input overflow-hidden text-muted-foreground justify-between font-normal normal-case",
                      }}
                      popoverProps={{
                        modal: true,
                        open: isComboboxOpen,
                        onOpenChange: setIsComboboxOpen,
                      }}
                      commandItemProps={(schema) => ({
                        onSelect: () => {
                          field.pushValue(schema.credentialIdentifier);
                          setIsComboboxOpen(false);
                        },
                      })}
                    >
                      <span className="truncate leading-normal">
                        <FormattedMessage
                          id={"integration.dialog.selectedSchemas"}
                          defaultMessage={`{count, plural,
                                  =0 {Select Credential Schemas}
                                  one {# Credential Schema selected}
                                  other {# Credential Schemas selected}
                                }`}
                          values={{
                            count: field.state.value.length,
                          }}
                        />
                      </span>
                      <IconChevronDown className="size-4! shrink-0!" />
                    </CredentialSchemaCombobox>
                    {isInvalid && (
                      <FieldError errors={field.state.meta.errors} />
                    )}
                    {field.state.value.length > 0 && (
                      <div className="flex max-h-84 flex-col gap-1 overflow-y-auto py-2">
                        {existingSelectedSchemaItems.map((schemaItem) => (
                          <div
                            key={schemaItem.schemaIdentifier}
                            className="flex w-full items-center gap-2"
                          >
                            <Button
                              type="button"
                              className="size-7 shrink-0 bg-input"
                              size="icon"
                              variant="outline"
                              onClick={(e) => {
                                flushSync(() => {
                                  field.removeValue(schemaItem.index);
                                });
                                const parent = e.currentTarget.parentElement;
                                const focusTarget =
                                  parent?.previousElementSibling?.querySelector(
                                    "button",
                                  ) ??
                                  parent?.nextElementSibling?.querySelector(
                                    "button",
                                  ) ??
                                  document.querySelector(
                                    "button[role=combobox]",
                                  );
                                (focusTarget as HTMLButtonElement)?.focus();
                              }}
                            >
                              <IconX className="size-4!" />
                            </Button>
                            <ComboboxSchemaItem
                              hideVersion
                              schema={schemaItem.schema}
                            />
                          </div>
                        ))}
                        {deletedSelectedSchemaItems.length > 0 && (
                          <p className="pt-2 text-sm font-medium text-muted-foreground">
                            <FormattedMessage
                              id="integration.dialog.deletedSchemas"
                              defaultMessage="Deleted credential schemas:"
                            />
                          </p>
                        )}
                        {deletedSelectedSchemaItems.map((schemaItem) => (
                          <div
                            key={`${schemaItem.schemaIdentifier}-deleted`}
                            className="flex w-full items-center gap-2"
                          >
                            <Button
                              type="button"
                              className="size-7 shrink-0 bg-input"
                              size="icon"
                              variant="outline"
                              onClick={() => {
                                field.removeValue(schemaItem.index);
                              }}
                            >
                              <IconX className="size-4!" />
                            </Button>
                            <div className="min-w-0 flex-1">
                              <p className="truncate text-sm font-medium">
                                {schemaItem.schemaIdentifier}
                              </p>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </Field>
                );
              }}
            </form.Field>
            <form.Field name="scopes" mode="array">
                {(field) => {
                  const isInvalid =
                    field.state.meta.isTouched && !field.state.meta.isValid;
                  return (
                    <FieldSet>
                      <FieldLegend variant="label">
                        <FormattedMessage
                          id="common.scopes"
                          defaultMessage="Scopes"
                        />
                      </FieldLegend>
                      <FieldGroup data-slot="checkbox-group">
                        {scopes.map((scope) => (
                          <Field
                            key={scope}
                            orientation="inline"
                            data-invalid={isInvalid}
                          >
                            <Checkbox
                              id={`integration-scopes-${scope}`}
                              name={field.name}
                              aria-invalid={isInvalid}
                              checked={field.state.value?.includes(scope)}
                              onCheckedChange={(checked) => {
                                if (checked) {
                                  field.pushValue(scope);
                                } else {
                                  const index =
                                    field.state.value?.indexOf(scope) ?? -1;
                                  if (index > -1) {
                                    field.removeValue(index);
                                  }
                                }
                              }}
                            />
                            <FieldLabel
                              htmlFor={`integration-scopes-${scope}`}
                              className="font-normal capitalize"
                            >
                              {scope === "issue" ? (
                                <FormattedMessage
                                  id="common.issue"
                                  defaultMessage="Issue"
                                />
                              ) : (
                                <FormattedMessage
                                  id="common.verify"
                                  defaultMessage="Verify"
                                />
                              )}
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
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button type="button" variant="outline">
                <FormattedMessage id="common.cancel" defaultMessage="Cancel" />
              </Button>
            </DialogClose>
            {defaultValues && (
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button
                    variant="destructive"
                    className="relative ml-auto overflow-hidden"
                  >
                    {isDeleting && (
                      <div className="absolute inset-0 z-10 grid place-items-center bg-destructive">
                        <IconLoader2 className="animate animate-spin" />
                      </div>
                    )}
                    <span>
                      <FormattedMessage
                        id="common.delete"
                        defaultMessage="Delete"
                      />
                    </span>
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>
                      <FormattedMessage
                        id="common.delete.withValue"
                        defaultMessage="Delete {value}"
                        values={{ value: defaultValues.displayName }}
                      />
                    </AlertDialogTitle>
                    <AlertDialogDescription>
                      <FormattedMessage
                        id="integrationsDialog.delete.description"
                        defaultMessage="Are you sure you want to delete this integration?"
                      />
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>
                      <FormattedMessage
                        id="common.cancel"
                        defaultMessage="Cancel"
                      />
                    </AlertDialogCancel>
                    <AlertDialogAction
                      variant="destructive"
                      onClick={() =>
                        deleteIntegration(defaultValues.id, {
                          onSuccess() {
                            setIsDialogOpen(false);
                          },
                        })
                      }
                    >
                      <FormattedMessage
                        id="common.delete"
                        defaultMessage="Delete"
                      />
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            )}
            <form.Subscribe selector={(state) => state.isDefaultValue}>
              {(isDefaultValue) => (
                <Button
                  disabled={isDefaultValue || isPending}
                  type="submit"
                  className="relative overflow-hidden"
                >
                  {isPending && (
                    <div className="absolute inset-0 z-10 grid place-items-center bg-primary">
                      <IconLoader2 className="animate animate-spin" />
                    </div>
                  )}
                  <span>
                    {defaultValues ? (
                      <FormattedMessage
                        id="common.update"
                        defaultMessage="Update"
                      />
                    ) : (
                      <FormattedMessage id="common.add" defaultMessage="Add" />
                    )}
                  </span>
                </Button>
              )}
            </form.Subscribe>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
