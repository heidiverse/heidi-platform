// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconHash,
  IconPlus,
  IconReplace,
  IconTrash,
} from "@tabler/icons-react";
import { useField, useStore } from "@tanstack/react-form";
import { useAtomValue } from "jotai";
import { FormattedMessage } from "react-intl";
import slugify from "slugify";
import { Button } from "@/components/ui/button";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { contentLanguageAtom } from "@/lib/atoms";
import { cn } from "@/lib/utils";
import { withForm } from "@/routes/_authenticated/credential-schemas/-form/form-context";
import {
  CredentialSchemaState,
  OverrideCredentialTypes,
} from "@/types/credential-schema";
import type { CredentialSchemaForm } from "./schemas";

export const AttributeOverrides = withForm({
  defaultValues: {} as CredentialSchemaForm,
  render: ({ form }) => {
    const locale = useAtomValue(contentLanguageAtom);
    const attributes = useStore(form.store, (state) => state.values.attributes);

    const { replaceValue } = useField({
      form,
      name: "attributes",
    });

    const state = useStore(form.store, (state) => state.values.state);
    const isPublished = state === CredentialSchemaState.Published;

    return (
      <div>
        <div className="relative overflow-hidden rounded-2xl border bg-background">
          <div className="absolute top-0 right-0 bottom-4 z-10 w-4 bg-linear-to-r from-transparent via-muted to-muted" />
          <Table
            safariFix={false}
            className="group"
            containerClassName="p-4 pt-0.5"
          >
            <TableHeader>
              <TableRow className="*:px-2">
                <TableHead className="min-w-0">
                  <div className="flex items-center gap-1 text-nowrap">
                    <IconHash className="size-4 shrink-0" />
                    <FormattedMessage
                      id="attributes.key"
                      defaultMessage="Key"
                    />
                  </div>
                </TableHead>
                {OverrideCredentialTypes.map((credentialType) => (
                  <TableHead key={credentialType}>
                    <div className="flex items-center gap-1 text-nowrap">
                      <IconReplace className="size-4 shrink-0" />
                      <FormattedMessage
                        id="attributes.override"
                        defaultMessage="Override"
                      />{" "}
                      {credentialType.toLowerCase()}
                    </div>
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody className="isolate">
              <form.AppField name="attributes" mode="array">
                {(field) => {
                  const attributesWithOverrides = field.state.value.filter(
                    (attribute) =>
                      Object.keys(attribute.attributeNameOverrides ?? {})
                        .length > 0,
                  );

                  if (attributesWithOverrides.length === 0) {
                    return (
                      <TableRow>
                        <TableCell colSpan={99} className="px-2! pt-6 pb-2">
                          <FormattedMessage
                            id="common.noOverrides"
                            defaultMessage="No Overrides"
                          />
                        </TableCell>
                      </TableRow>
                    );
                  }

                  return attributesWithOverrides.map((attribute, i) => {
                    if (
                      Object.keys(attribute.attributeNameOverrides).length === 0
                    ) {
                      return null;
                    }

                    const index = attributes.findIndex(
                      (a) => a.name === attribute.name,
                    );

                    return (
                      <TableRow key={attribute.name}>
                        <TableCell className="align-middle">
                          <p className="truncate px-2 py-2.5 text-sm">
                            {attribute.name}
                          </p>
                        </TableCell>
                        {OverrideCredentialTypes.map((credentialType) => (
                          <TableCell
                            key={credentialType}
                            className={cn(isPublished && "align-middle")}
                          >
                            {isPublished ? (
                              <p className="truncate px-2 py-2.5 text-sm">
                                {
                                  field.state.value[index]
                                    ?.attributeNameOverrides[credentialType]
                                }
                              </p>
                            ) : (
                              <form.AppField
                                name={`attributes[${index}].attributeNameOverrides.${credentialType}`}
                              >
                                {(subField) => {
                                  const isInvalid =
                                    subField.state.meta.isTouched &&
                                    !subField.state.meta.isValid;
                                  return (
                                    <Field data-invalid={isInvalid}>
                                      <FieldLabel
                                        htmlFor={subField.name}
                                        className="sr-only"
                                      >
                                        {credentialType}
                                      </FieldLabel>
                                      <Input
                                        className="w-full"
                                        onChange={(e) => {
                                          subField.handleChange(
                                            slugify(e.target.value, {
                                              locale: "de",
                                              trim: false,
                                            }),
                                          );
                                        }}
                                        value={subField.state.value || ""}
                                        name={subField.name}
                                        id={subField.name}
                                        aria-invalid={isInvalid}
                                      />
                                      {isInvalid && (
                                        <FieldError
                                          errors={subField.state.meta.errors}
                                        />
                                      )}
                                    </Field>
                                  );
                                }}
                              </form.AppField>
                            )}
                          </TableCell>
                        ))}
                        {!isPublished && (
                          <TableCell
                            className="sticky right-0 flex justify-end"
                            style={{ zIndex: 100 - i }}
                          >
                            <Button
                              type="button"
                              variant="outline"
                              size="icon"
                              className="relative bg-input transition-shadow group-data-[scrolled-end=false]:shadow-lg"
                              onClick={() => {
                                field.replaceValue(index, {
                                  ...attribute,
                                  attributeNameOverrides: {},
                                });
                              }}
                            >
                              <div className="absolute -inset-2 -right-4 -z-10 rounded-lg bg-linear-to-r from-transparent via-muted to-muted group-data-[scrolled-end=true]:bg-none" />
                              <IconTrash />
                            </Button>
                          </TableCell>
                        )}
                      </TableRow>
                    );
                  });
                }}
              </form.AppField>
            </TableBody>
          </Table>
        </div>
        <Select
          onValueChange={(value) => {
            const index = attributes.findIndex((a) => a.name === value);
            const attribute = attributes[index];
            if (!attribute) {
              return;
            }
            replaceValue(index, {
              ...attribute,
              attributeNameOverrides: Object.fromEntries(
                OverrideCredentialTypes.map((credentialType) => [
                  credentialType,
                  "",
                ]),
              ),
            });
          }}
        >
          <SelectTrigger asChild className="mt-4">
            <Button variant="tertiary">
              <IconPlus />
              <FormattedMessage
                id="common.addOverride"
                defaultMessage="Add Override"
              />
            </Button>
          </SelectTrigger>
          <SelectContent>
            {attributes.map((attribute) => (
              <SelectItem
                withIndicator={false}
                key={attribute.id}
                value={attribute.name}
              >
                <div className="flex flex-col">
                  {attribute.name}
                  <span className="text-xs font-normal text-muted-foreground">
                    {attribute.displayName[locale] ||
                      Object.values(attribute.displayName).filter(Boolean)[0] ||
                      attribute.name}
                  </span>
                </div>
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    );
  },
});
