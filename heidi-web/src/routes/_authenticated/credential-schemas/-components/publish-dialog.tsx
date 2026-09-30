// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useForm } from "@tanstack/react-form";
import { FormattedMessage } from "react-intl";
import { gte, valid } from "semver";
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
} from "@/components/ui/dialog";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { usePublishSchemaMutation } from "@/lib/api/credential-schemas/mutations";

export function PublishDialog({
  open,
  defaultVersion: nextMinVersion,
  onClose,
  schemaId,
}: {
  open: boolean;
  defaultVersion: string;
  onClose: () => void;
  schemaId: string;
}) {
  const { mutate: publish, isPending } = usePublishSchemaMutation();
  const formSchema = z.object({
    version: z
      .string()
      .refine(
        (v) => valid(v) && gte(v, nextMinVersion),
        `Version must be valid and be greater than or equal to the minimum version (${nextMinVersion})`,
      ),
  });

  const form = useForm({
    defaultValues: {
      version: nextMinVersion,
    },
    validators: {
      onSubmit: formSchema,
    },
    onSubmit({ value }) {
      publish({ schemaId, version: value.version }, { onSuccess: onClose });
    },
  });

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="space-y-4">
        <DialogHeader>
          <DialogTitle>
            <FormattedMessage
              id="pages.crdentialSchemas.publishDialog.title"
              defaultMessage="Publish Changes"
            />
          </DialogTitle>
          <DialogDescription>
            <FormattedMessage
              id="pages.crdentialSchemas.publishDialog.description"
              defaultMessage="Set the version of the schema to publish. The version must be a a valid semver increase from the currently published version."
            />
          </DialogDescription>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            e.stopPropagation();
            form.handleSubmit();
          }}
        >
          <form.Field name="version">
            {(field) => {
              const isInvalid =
                field.state.meta.isTouched && !field.state.meta.isValid;
              return (
                <Field data-invalid={isInvalid}>
                  <FieldLabel htmlFor={field.name}>
                    <FormattedMessage
                      id="common.version"
                      defaultMessage="Version"
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
                  />
                  {isInvalid && <FieldError errors={field.state.meta.errors} />}
                </Field>
              );
            }}
          </form.Field>
          <DialogFooter>
            <DialogClose asChild>
              <Button type="button" variant="outline" className="px-4">
                <FormattedMessage id="common.cancel" defaultMessage="Cancel" />
              </Button>
            </DialogClose>
            <Button disabled={isPending} type="submit">
              <FormattedMessage id="common.publish" defaultMessage="Publish" />
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
