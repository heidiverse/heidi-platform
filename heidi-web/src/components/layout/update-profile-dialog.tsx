// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconPhotoUp, IconTrash } from "@tabler/icons-react";
import { useForm } from "@tanstack/react-form";
import { FormattedMessage, useIntl } from "react-intl";
import { z } from "zod";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
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
import { Label } from "@/components/ui/label";
import { useUpdateUserMetadataMutation } from "@/lib/auth/mutations";
import { useUser } from "@/lib/hooks/use-user";
import { cn, fileToDataURL } from "@/lib/utils";

export function UpdateProfileDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const user = useUser();
  const { $t } = useIntl();
  const { mutate: updateUserMetadata } = useUpdateUserMetadataMutation();
  const profileFormSchema = z.object({
    displayName: z.string().min(
      1,
      $t({
        id: "common.profile.validation.displayNameRequired",
        defaultMessage: "Display Name is required",
      }),
    ),
    thumbnail: z
      .instanceof(File)
      .refine((file) => file.size <= 1 * 1024 * 1024, {
        message: $t({
          id: "common.profile.validation.thumbnailMaxSize",
          defaultMessage: "File size must be less than 1MB",
        }),
      })
      .optional(),
    thumbnailBase64: z.string().optional(),
  });
  const form = useForm({
    defaultValues: {
      displayName: user.displayName,
      thumbnail: undefined,
      thumbnailBase64: user.picture,
    } as z.input<typeof profileFormSchema>,
    validators: {
      onSubmit: profileFormSchema,
    },
    onSubmit({ value: { displayName, thumbnail }, formApi }) {
      updateUserMetadata(
        {
          ...(!formApi.state.fieldMeta.displayName?.isDefaultValue && {
            displayName,
          }),
          ...(!formApi.state.fieldMeta.thumbnail?.isDefaultValue && {
            thumbnail,
          }),
        },
        {
          onSuccess() {
            onOpenChange(false);
          },
        },
      );
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            form.handleSubmit();
          }}
        >
          <DialogHeader className="flex-row items-center gap-3">
            <form.Field name="thumbnail">
              {(field) => (
                <label
                  htmlFor={field.name}
                  className="grid size-13.5 shrink-0 place-items-center rounded-full border bg-card shadow-xs"
                >
                  <Avatar>
                    <form.Field name="thumbnailBase64">
                      {(thumbnailField) => (
                        <AvatarImage
                          src={thumbnailField.state.value}
                          alt={user.displayName}
                        />
                      )}
                    </form.Field>
                    <form.Field name="displayName">
                      {(displayNameField) => (
                        <AvatarFallback>
                          {displayNameField.state.value}
                        </AvatarFallback>
                      )}
                    </form.Field>
                  </Avatar>
                </label>
              )}
            </form.Field>
            <div className="flex flex-col gap-y-1.5">
              <DialogTitle>
                <FormattedMessage
                  id="common.profile.update"
                  defaultMessage="Update Profile"
                />
              </DialogTitle>
              <DialogDescription>
                <FormattedMessage
                  id="common.profile.update.description"
                  defaultMessage="Update your profile information"
                />
              </DialogDescription>
            </div>
          </DialogHeader>
          <div className="my-4 flex flex-col gap-2">
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
                      placeholder={$t({
                        id: "common.displayName.placeholder.short",
                        defaultMessage: "Enter Display Name",
                      })}
                      id={field.name}
                      name={field.name}
                      value={field.state.value}
                      onChange={(event) =>
                        field.handleChange(event.target.value)
                      }
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
            <form.Field name="thumbnail">
              {(field) => {
                const isInvalid =
                  field.state.meta.isTouched && !field.state.meta.isValid;
                return (
                  <Field orientation="horizontal" data-invalid={isInvalid}>
                    <FieldLabel htmlFor={field.name}>
                      <FormattedMessage
                        id="common.profileImage"
                        defaultMessage="Profile Image"
                      />
                    </FieldLabel>
                    <Input
                      type="file"
                      accept="image/*"
                      id={field.name}
                      name={field.name}
                      className="peer sr-only fixed"
                      onChange={async (event) => {
                        const file = event.target.files?.[0];
                        field.handleChange(file);
                        if (file) {
                          form.setFieldValue(
                            "thumbnailBase64",
                            await fileToDataURL(file),
                          );
                        } else {
                          form.setFieldValue("thumbnailBase64", undefined);
                        }
                        event.target.value = "";
                      }}
                      onBlur={field.handleBlur}
                      aria-invalid={isInvalid}
                    />
                    <div className="flex items-center gap-1">
                      <Label
                        className={cn(
                          "flex h-10 items-center rounded-full border p-3",
                          !field.state.meta.isValid &&
                            "border-destructive text-destructive",
                          "flex-1 gap-2 bg-input outline-2 outline-offset-2 outline-transparent transition-colors peer-focus-within:outline-ring hover:bg-secondary",
                        )}
                        htmlFor={field.name}
                      >
                        <IconPhotoUp className="size-4 shrink-0" />
                        {field.state.value
                          ? field.state.value.name
                          : $t({
                              id: "common.profileImage.choose",
                              defaultMessage: "Choose a profile image",
                            })}
                      </Label>
                      {field.state.value && (
                        <Button
                          type="button"
                          className="bg-input"
                          variant="outline"
                          size="icon"
                          onClick={() => {
                            form.setFieldValue("thumbnailBase64", undefined);
                            form.setFieldValue("thumbnail", undefined);
                            form.setErrorMap({ onSubmit: { fields: {} } });
                          }}
                        >
                          <IconTrash />
                        </Button>
                      )}
                    </div>
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
              <Button variant="outline">
                <FormattedMessage id="common.cancel" defaultMessage="Cancel" />
              </Button>
            </DialogClose>
            <form.Subscribe selector={(state) => state.isDefaultValue}>
              {(isDefaultValue) => (
                <Button type="submit" disabled={isDefaultValue}>
                  <FormattedMessage
                    id="common.update"
                    defaultMessage="Update"
                  />
                </Button>
              )}
            </form.Subscribe>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
