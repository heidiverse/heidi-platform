// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useForm } from "@tanstack/react-form";
import { type ReactNode, useState } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { z } from "zod";
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
import { useUpdateSchemaMutation } from "@/lib/api/credential-schemas/mutations";
import { credentialSchemaValidationMessages } from "@/lib/translations";
import type { CredentialSchemaForm } from "./schemas";

function createFormSchema(intl: ReturnType<typeof useIntl>) {
  return z.object({
    displayName: z.string().min(1, {
      error: () =>
        intl.$t(
          credentialSchemaValidationMessages.validationSchemaDisplayNameRequired,
        ),
    }),
  });
}

export function UpdateCredentialSchemaDialog({
  children,
  defaultValues,
}: {
  children: ReactNode;
  defaultValues: CredentialSchemaForm;
}) {
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const intl = useIntl();
  const formSchema = createFormSchema(intl);
  const { mutate: update, isPending } = useUpdateSchemaMutation();

  const form = useForm({
    defaultValues: {
      displayName: defaultValues.displayName,
    },
    validators: {
      onSubmit: formSchema,
    },
    onSubmit({ value }) {
      update(
        { ...defaultValues, displayName: value.displayName },
        { onSuccess: () => setIsDialogOpen(false) },
      );
    },
  });

  return (
    <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="sm:max-w-131.25">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            form.handleSubmit();
          }}
        >
          <DialogHeader>
            <DialogTitle>
              <FormattedMessage
                id="common.edit.withValue"
                defaultMessage="Edit {value}"
                values={{ value: defaultValues.displayName }}
              />
            </DialogTitle>
            <DialogDescription>
              <FormattedMessage
                id="credentialSchema.editDialog.description"
                defaultMessage="Update Credential Schema Display Name"
              />
            </DialogDescription>
          </DialogHeader>

          <div className="py-4">
            <form.Field name="displayName">
              {(field) => {
                const isInvalid =
                  field.state.meta.isTouched && !field.state.meta.isValid;
                return (
                  <Field data-invalid={isInvalid}>
                    <FieldLabel htmlFor={field.name}>
                      <FormattedMessage
                        id="common.title"
                        defaultMessage="Title"
                      />
                    </FieldLabel>
                    <Input
                      className="w-full"
                      type="text"
                      id={field.name}
                      name={field.name}
                      value={field.state.value}
                      onChange={(e) => field.handleChange(e.target.value)}
                      onBlur={field.handleBlur}
                      aria-invalid={isInvalid}
                    />
                    {isInvalid && (
                      <FieldError errors={field.state.meta.errors} />
                    )}
                  </Field>
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
            <Button disabled={isPending} type="submit">
              <FormattedMessage id="common.save" defaultMessage="Save" />
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
