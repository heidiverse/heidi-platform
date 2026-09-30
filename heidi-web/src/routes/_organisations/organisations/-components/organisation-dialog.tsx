// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconLoader2 } from "@tabler/icons-react";
import { useForm } from "@tanstack/react-form";
import { type ReactNode, useEffect, useState } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import slugify from "slugify";
import { z } from "zod";
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
  FieldContent,
  FieldDescription,
  FieldError,
  FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  useCreateOrganisationMutation,
  useDeleteOrganisationMutation,
  useUpdateOrganisationMutation,
} from "@/lib/api/organisations/mutations";
import { cn } from "@/lib/utils";

type OrganisationFormValues = {
  displayName: string;
  tenantId: string;
  status: "active" | "inactive";
};

export function OrganisationDialog({
  children,
  defaultValues,
}: {
  children: ReactNode;
  defaultValues?: OrganisationFormValues;
}) {
  const isEdit = !!defaultValues;
  const { $t } = useIntl();
  const formSchema = z.object({
    displayName: z.string().min(
      1,
      $t({
        id: "pages.organisations.validation.displayNameRequired",
        defaultMessage: "Organisation Name is required",
      }),
    ),
    tenantId: z.string().min(
      1,
      $t({
        id: "pages.organisations.validation.tenantIdRequired",
        defaultMessage: "Slug is required",
      }),
    ),
    status: z.enum(["active", "inactive"], {
      message: $t({
        id: "pages.organisations.validation.statusRequired",
        defaultMessage: "Status is required",
      }),
    }),
  });
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);

  const { mutateAsync: createOrganisation } = useCreateOrganisationMutation();
  const { mutateAsync: updateOrganisation, isPending } =
    useUpdateOrganisationMutation();
  const { mutateAsync: deleteOrganisation } = useDeleteOrganisationMutation();

  const form = useForm({
    validators: { onSubmit: formSchema },
    defaultValues:
      defaultValues ??
      ({
        displayName: "",
        tenantId: "",
        status: "active",
      } as z.input<typeof formSchema>),
    onSubmit({ value }) {
      if (isEdit) {
        updateOrganisation(
          {
            tenantId: value.tenantId,
            displayName: value.displayName,
            ...(value.status !== defaultValues.status && {
              status: value.status,
            }),
          },
          {
            onSuccess() {
              setIsDialogOpen(false);
              form.reset();
            },
          },
        );
      } else {
        createOrganisation(
          {
            tenantId: value.tenantId,
            displayName: value.displayName,
          },
          {
            onSuccess() {
              setIsDialogOpen(false);
              form.reset();
            },
          },
        );
      }
    },
  });

  useEffect(() => {
    if (defaultValues) {
      form.reset(defaultValues);
    }
  }, [defaultValues, form]);

  return (
    <Dialog
      open={isDialogOpen}
      onOpenChange={(open) => {
        if (!open) {
          form.reset();
        }
        setIsDialogOpen(open);
      }}
    >
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            <FormattedMessage
              id={isEdit ? "common.edit.withValue" : "common.add.withValue"}
              defaultMessage={isEdit ? "Edit {value}" : "Add {value}"}
              values={{
                value: $t({
                  id: "common.organisation",
                  defaultMessage: "Organisation",
                }),
              }}
            />
          </DialogTitle>
          <DialogDescription className="sr-only">
            <FormattedMessage
              id={
                isEdit
                  ? "pages.organisations.dialog.editDescription"
                  : "pages.organisations.dialog.addDescription"
              }
              defaultMessage={
                isEdit ? "Edit current organisation" : "Add a new organisation"
              }
            />
          </DialogDescription>
        </DialogHeader>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            form.handleSubmit();
          }}
          className="flex flex-col gap-4"
        >
          <div className="flex flex-col gap-3">
            <form.Field name="displayName">
              {(field) => {
                const isInvalid =
                  field.state.meta.isTouched && !field.state.meta.isValid;
                return (
                  <Field data-invalid={isInvalid} orientation="horizontal">
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
                      onChange={(e) => {
                        if (
                          !isEdit &&
                          !form.getFieldMeta("tenantId")?.isDirty
                        ) {
                          form.setFieldValue(
                            "tenantId",
                            slugify(e.target.value, {
                              locale: "de",
                              lower: true,
                              strict: true,
                            }),
                            {
                              dontUpdateMeta: true,
                              dontValidate: true,
                              dontRunListeners: true,
                            },
                          );
                        }
                        field.handleChange(e.target.value);
                      }}
                      onBlur={field.handleBlur}
                      aria-invalid={isInvalid}
                      autoComplete="off"
                      data-1p-ignore
                      data-lpignore="true"
                      data-protonpass-ignore="true"
                      data-bwignore
                    />
                    {isInvalid && (
                      <FieldError errors={field.state.meta.errors} />
                    )}
                  </Field>
                );
              }}
            </form.Field>

            <form.Field name="tenantId">
              {(field) => {
                const isInvalid =
                  field.state.meta.isTouched && !field.state.meta.isValid;
                return (
                  <Field orientation="horizontal" data-invalid={isInvalid}>
                    <FieldLabel htmlFor={field.name}>
                      <FormattedMessage
                        id="common.slug"
                        defaultMessage="Slug"
                      />
                    </FieldLabel>
                    {isEdit ? (
                      <p className="px-3 py-2 text-sm">{field.state.value}</p>
                    ) : (
                      <>
                        <FieldContent>
                          <Input
                            id={field.name}
                            name={field.name}
                            aria-invalid={isInvalid}
                            value={field.state.value}
                            onChange={(e) => field.handleChange(e.target.value)}
                            onBlur={field.handleBlur}
                          />
                          <FieldDescription>
                            <FormattedMessage
                              id="pages.organisations.tenantId.description"
                              defaultMessage="Can't be changed later"
                            />
                          </FieldDescription>
                        </FieldContent>
                        {isInvalid && (
                          <FieldError errors={field.state.meta.errors} />
                        )}
                      </>
                    )}
                  </Field>
                );
              }}
            </form.Field>

            <form.Field name="status">
              {(field) => {
                const isInvalid =
                  field.state.meta.isTouched && !field.state.meta.isValid;
                return (
                  <Field orientation="horizontal" data-invalid={isInvalid}>
                    <FieldLabel htmlFor={field.name}>
                      <FormattedMessage
                        id="common.status"
                        defaultMessage="Status"
                      />
                    </FieldLabel>
                    <Select
                      onValueChange={(v) =>
                        field.handleChange(v as typeof field.state.value)
                      }
                      value={field.state.value}
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
                                id: "common.status",
                                defaultMessage: "Status",
                              }),
                            },
                          )}
                        />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="active">
                          <FormattedMessage
                            id="common.active"
                            defaultMessage="Active"
                          />
                        </SelectItem>
                        <SelectItem value="inactive">
                          <FormattedMessage
                            id="common.inactive"
                            defaultMessage="Inactive"
                          />
                        </SelectItem>
                      </SelectContent>
                    </Select>
                    {isInvalid && (
                      <FieldError errors={field.state.meta.errors} />
                    )}
                  </Field>
                );
              }}
            </form.Field>
          </div>
          <DialogFooter className={cn(isEdit && "justify-end")}>
            {isEdit && (
              <AlertDialog
                open={isDeleteDialogOpen}
                onOpenChange={setIsDeleteDialogOpen}
              >
                <AlertDialogTrigger asChild>
                  <Button variant="destructive" className="mr-auto">
                    <FormattedMessage
                      id="common.delete.withValue"
                      defaultMessage="Delete {value}"
                      values={{
                        value: $t({
                          id: "common.organisation",
                          defaultMessage: "Organisation",
                        }),
                      }}
                    />
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>
                      <FormattedMessage
                        id="common.delete.withValue"
                        defaultMessage="Delete {value}"
                        values={{
                          value: $t({
                            id: "common.organisation",
                            defaultMessage: "Organisation",
                          }),
                        }}
                      />
                    </AlertDialogTitle>
                    <AlertDialogDescription>
                      <FormattedMessage
                        id="pages.organisations.dialog.deleteDescription"
                        defaultMessage="Are you sure you want to delete this organisation?"
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
                      onClick={async () => {
                        if (!defaultValues?.tenantId) {
                          throw new Error("tenantId is undefined");
                        }
                        deleteOrganisation(defaultValues.tenantId, {
                          onSuccess() {
                            setIsDeleteDialogOpen(false);
                            setIsDialogOpen(false);
                          },
                        });
                      }}
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
            <DialogClose asChild>
              <Button variant="outline">
                <FormattedMessage id="common.cancel" defaultMessage="Cancel" />
              </Button>
            </DialogClose>
            <Button
              type="submit"
              className="relative overflow-hidden"
              disabled={isEdit && isPending}
            >
              {isEdit && isPending && (
                <div className="absolute inset-0 grid place-items-center bg-primary">
                  <IconLoader2 className="size-4 animate-spin" />
                </div>
              )}
              <FormattedMessage
                id={isEdit ? "common.update" : "common.create"}
                defaultMessage={isEdit ? "Update" : "Create"}
              />
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
