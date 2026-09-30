// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconEye,
  IconEyeClosed,
  IconEyeShare,
  IconFile,
  IconFileTextShield,
  IconHash,
  IconLanguage,
  IconList,
  IconPlus,
  IconTrash,
} from "@tabler/icons-react";
import { useField, useStore } from "@tanstack/react-form";
import { useQuery } from "@tanstack/react-query";
import { customAlphabet } from "nanoid";
import { FormattedMessage, useIntl } from "react-intl";
import slugify from "slugify";
import { AutoComplete } from "@/components/ui/autocomplete";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Field, FieldError } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { AttributeCatalog } from "@/lib/api/attribute-catalogs/api";
import { attributeCatalogListOptions } from "@/lib/api/attribute-catalogs/query-options";
import type { AttributeRule, Template } from "@/lib/api/templates/api";
import { getAttributeTypeMessage } from "@/lib/translations";
import { withForm } from "@/routes/_authenticated/credential-schemas/-form/form-context";
import {
  AttributeType,
  CredentialSchemaState,
  type OverrideCredentialTypes,
} from "@/types/credential-schema";
import type { CredentialSchemaForm } from "./schemas";

const nanoid = customAlphabet("123456789", 6);

type AttributeProps = {
  isMetaAttribute?: boolean;
  template?: Template;
  displayNameLanguages: string[];
};

export const Attributes = withForm({
  props: {} as AttributeProps,
  // only used for types
  defaultValues: {} as CredentialSchemaForm,
  render: ({ form, isMetaAttribute = false, displayNameLanguages, template }) => {
    const { $t } = useIntl();
    const attributeKey = isMetaAttribute ? "metaAttributes" : "attributes";
    const { data: attributeCatalogs } = useQuery({
      ...attributeCatalogListOptions(),
      enabled: !isMetaAttribute,
    });
    const { pushValue: append } = useField({ form, name: attributeKey });
    const state = useStore(form.store, (state) => state.values.state);
    const templateId = useStore(form.store, (state) => state.values.templateId);
    const formAttributes = useStore(
      form.store,
      (state) => state.values[attributeKey],
    );
    // needs to be derived from templateId, as template is a network dependency and can come in later
    const hasTemplate = Boolean(templateId);
    const isPublished = state === CredentialSchemaState.Published;

    const defaultAttributes = form.options.defaultValues?.[
      attributeKey
    ] as CredentialSchemaForm[typeof attributeKey];

    return (
      <>
        <div className="relative overflow-hidden rounded-2xl border bg-background">
          <div className="absolute top-0 right-0 bottom-4 z-10 w-4 bg-linear-to-r from-transparent via-muted to-muted" />
          <Table
            safariFix={false}
            className="group min-w-max"
            containerClassName="p-4 pt-0.5"
          >
            <TableHeader>
              <TableRow className="*:px-2">
                {!isMetaAttribute && (
                  <TableHead className="min-w-0">
                    <div className="flex items-center gap-1 text-nowrap">
                      <IconFileTextShield className="size-4 shrink-0" />
                      <FormattedMessage
                        id="attributes.sensitive"
                        defaultMessage="Sensitive"
                      />
                    </div>
                  </TableHead>
                )}
                {!isMetaAttribute && (
                  <TableHead className="min-w-0">
                    <div className="flex items-center gap-1 text-nowrap">
                      <IconEyeShare className="size-4" />
                      <FormattedMessage
                        id="attributes.disclosable"
                        defaultMessage="Disclosable"
                      />
                    </div>
                  </TableHead>
                )}
                <TableHead>
                  <div className="flex items-center gap-1 text-nowrap">
                    <IconHash className="size-4 shrink-0" />
                    <FormattedMessage
                      id="attributes.key"
                      defaultMessage="Key"
                    />
                    {isPublished ? "" : "*"}
                  </div>
                </TableHead>
                {!isMetaAttribute && (
                  <TableHead className="min-w-0">
                    <div className="flex items-center gap-1 text-nowrap">
                      <IconList className="size-4" />
                      <FormattedMessage
                        id="attributes.isArray"
                        defaultMessage="Array"
                      />
                    </div>
                  </TableHead>
                )}
                {!isMetaAttribute && (
                  <TableHead>
                    <div className="flex items-center gap-1 text-nowrap">
                      <IconFile className="size-4 shrink-0" />
                      <FormattedMessage
                        id="attributes.type"
                        defaultMessage="Type"
                      />
                      {isPublished ? "" : "*"}
                    </div>
                  </TableHead>
                )}
                {displayNameLanguages.map((lang) => (
                  <TableHead key={lang}>
                    <div className="flex items-center gap-1 text-nowrap">
                      <IconLanguage className="size-4 shrink-0" />
                      <FormattedMessage
                        id="common.displayName"
                        defaultMessage="Display Name"
                      />{" "}
                      ({lang.toUpperCase()})
                    </div>
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody className="isolate">
              <form.AppField name={attributeKey} mode="array">
                {(field) => {
                  if (field.state.value.length === 0) {
                    return (
                      <TableRow>
                        <TableCell colSpan={99} className="px-2! pt-6 pb-2">
                          <FormattedMessage
                            id="attributes.none"
                            defaultMessage="No Attributes"
                          />
                        </TableCell>
                      </TableRow>
                    );
                  }

                  if (isPublished) {
                    return field.state.value.map((attribute) => (
                      <TableRow key={attribute.id}>
                        {!isMetaAttribute && "isSensitive" in attribute && (
                          <TableCell className="align-middle">
                            <div className="flex items-center justify-center">
                              <Checkbox
                                tabIndex={-1}
                                className="pointer-events-none mx-auto"
                                checked={attribute.isSensitive}
                              />
                            </div>
                          </TableCell>
                        )}
                        {!isMetaAttribute && "isDisclosable" in attribute && (
                          <TableCell className="align-middle">
                            <div className="flex items-center justify-center">
                              <Checkbox
                                tabIndex={-1}
                                className="pointer-events-none mx-auto"
                                checked={attribute.isDisclosable}
                              />
                            </div>
                          </TableCell>
                        )}
                        <TableCell>
                          <p className="truncate px-2 py-2.5 text-sm">
                            {attribute.name}
                          </p>
                        </TableCell>
                        {!isMetaAttribute && "isArray" in attribute && (
                          <TableCell className="align-middle">
                            <div className="flex items-center justify-center">
                              <Checkbox
                                tabIndex={-1}
                                className="pointer-events-none mx-auto"
                                checked={attribute.isArray}
                              />
                            </div>
                          </TableCell>
                        )}
                        {!isMetaAttribute && "type" in attribute && (
                          <TableCell>
                            <p className="truncate px-2 py-2.5 text-sm">
                              {$t(
                                getAttributeTypeMessage(
                                  attribute.type as AttributeType,
                                ),
                              )}
                            </p>
                          </TableCell>
                        )}
                        {Object.entries(attribute.displayName).map(
                          ([lang, displayName]) => (
                            <TableCell key={lang}>
                              <p className="truncate px-2 py-2.5 text-sm">
                                {displayName}
                              </p>
                            </TableCell>
                          ),
                        )}
                      </TableRow>
                    ));
                  }

                  const attributes = getAttributes(
                    field.state.value,
                    defaultAttributes,
                    template,
                  );

                  return attributes.map(
                    ({ isActive, ...attribute }, displayIndex) => {
                      const rule = hasTemplate
                        ? (template?.attributeRules[attribute.name] ?? {
                            description: "",
                            canBeEmpty: false,
                            isDisplayNameEditable: false,
                            isRequired: true,
                          })
                        : null;
                      const formIndex = formAttributes.findIndex(
                        (a) => a.id === attribute.id,
                      );
                      if (isActive) {
                        return (
                          <ActiveAttributeRow
                            form={form}
                            key={attribute.id}
                            displayIndex={displayIndex}
                            formIndex={formIndex}
                            isMetaAttribute={isMetaAttribute}
                            rule={rule}
                            displayNameLanguages={displayNameLanguages}
                            hasTemplate={hasTemplate}
                            attributeCatalogs={attributeCatalogs}
                          />
                        );
                      }
                      if (
                        !isActive &&
                        "type" in attribute &&
                        "attributeNameOverrides" in attribute
                      ) {
                        return (
                          <DisabledAttributeRow
                            form={form}
                            key={attribute.id}
                            attribute={attribute}
                            displayIndex={displayIndex}
                            displayNameLanguages={displayNameLanguages}
                          />
                        );
                      }
                      return null;
                    },
                  );
                }}
              </form.AppField>
            </TableBody>
          </Table>
        </div>
        {!isPublished && (!hasTemplate || isMetaAttribute) && (
          <Button
            type="button"
            variant="tertiary"
            onClick={() => {
              append({
                id: Number.parseInt(nanoid(), 10),
                displayName: Object.fromEntries(
                  displayNameLanguages.map((lang) => [lang, ""]),
                ),
                name: "",

                ...(isMetaAttribute
                  ? {}
                  : {
                      type: "",
                      isDisclosable: true,
                      isArray: false,
                      isSensitive: false,
                      attributeNameOverrides: {},
                    }),
              });
            }}
          >
            <span className="flex items-center gap-2">
              <IconPlus className="size-4" />
              {isMetaAttribute ? (
                <FormattedMessage
                  id="common.add.withValue"
                  defaultMessage="Add {value}"
                  values={{
                    value: $t({
                      id: "common.metaAttribute",
                      defaultMessage: "Meta Attribute",
                    }),
                  }}
                />
              ) : (
                <FormattedMessage
                  id="common.add.withValue"
                  defaultMessage="Add {value}"
                  values={{
                    value: $t({
                      id: "common.attribute",
                      defaultMessage: "Attribute",
                    }),
                  }}
                />
              )}
            </span>
          </Button>
        )}
      </>
    );
  },
});

const ActiveAttributeRow = withForm({
  defaultValues: {} as CredentialSchemaForm,
  props: {} as {
    formIndex: number;
    displayNameLanguages: string[];
    rule: AttributeRule | null;
    displayIndex: number;
    isMetaAttribute: boolean;
    hasTemplate: boolean;
    attributeCatalogs?: AttributeCatalog[];
  },
  render: ({
    form,
    displayIndex,
    formIndex,
    isMetaAttribute,
    rule,
    displayNameLanguages,
    hasTemplate,
    attributeCatalogs,
  }) => {
    const { $t } = useIntl();
    const { removeValue } = useField({
      form,
      name: isMetaAttribute ? "metaAttributes" : "attributes",
    });
    return (
      <TableRow>
        {!isMetaAttribute && (
          <TableCell className="align-middle">
            <form.AppField name={`attributes[${formIndex}].isSensitive`}>
              {(field) => (
                <field.Checkbox
                  className="mx-auto"
                  disabled={hasTemplate}
                  label={$t({
                    id: "attributes.sensitive",
                    defaultMessage: "Sensitive",
                  })}
                />
              )}
            </form.AppField>
          </TableCell>
        )}
        {!isMetaAttribute && (
          <TableCell className="align-middle">
            <form.AppField name={`attributes[${formIndex}].isDisclosable`}>
              {(field) => (
                <field.Checkbox
                  className="mx-auto"
                  disabled={hasTemplate}
                  label={$t({
                    id: "attributes.disclosable",
                    defaultMessage: "Disclosable",
                  })}
                />
              )}
            </form.AppField>
          </TableCell>
        )}
        <TableCell>
          <form.AppField
            name={
              isMetaAttribute
                ? `metaAttributes[${formIndex}].name`
                : `attributes[${formIndex}].name`
            }
          >
            {(field) => {
              const isInvalid =
                field.state.meta.isTouched && !field.state.meta.isValid;
              return isMetaAttribute ? (
                <field.TextInput
                  type="text"
                  label={$t({
                    id: "attributes.key",
                    defaultMessage: "Key",
                  })}
                  slugify
                  placeholder={$t(
                    {
                      id: "common.enter.withValue",
                      defaultMessage: "Enter {value}",
                    },
                    {
                      value: $t({
                        id: "attributes.key",
                        defaultMessage: "Key",
                      }),
                    },
                  )}
                />
              ) : (
                <Field data-invalid={isInvalid}>
                  <AutoComplete
                    disabled={hasTemplate}
                    items={attributeCatalogs ?? []}
                    onSelectedItemChange={(i) => {
                      field.handleChange(i.attributeKey);
                      form.setFieldValue(
                        `attributes[${formIndex}].type`,
                        i.attributeType,
                      );
                      for (const [lang, displayName] of Object.entries(
                        i.attributeDisplayName,
                      )) {
                        form.setFieldValue(
                          `attributes[${formIndex}].displayName.${lang}`,
                          displayName,
                        );
                      }
                    }}
                    onValueChange={(v) => {
                      field.handleChange(
                        slugify(v, {
                          locale: "de",
                          trim: false,
                        }),
                      );
                    }}
                    value={field.state.value}
                    placeholder={$t(
                      {
                        id: "common.enter.withValue",
                        defaultMessage: "Enter {value}",
                      },
                      {
                        value: $t({
                          id: "attributes.key",
                          defaultMessage: "Key",
                        }),
                      },
                    )}
                  />
                  {isInvalid && <FieldError errors={field.state.meta.errors} />}
                </Field>
              );
            }}
          </form.AppField>
        </TableCell>
        {!isMetaAttribute && (
          <TableCell className="align-middle">
            <form.AppField name={`attributes[${formIndex}].isArray`}>
              {(field) => (
                <field.Checkbox
                  className="mx-auto"
                  disabled={hasTemplate}
                  label={$t({
                    id: "attributes.isArray",
                    defaultMessage: "Array",
                  })}
                />
              )}
            </form.AppField>
          </TableCell>
        )}
        {!isMetaAttribute && (
          <TableCell>
            <form.AppField name={`attributes[${formIndex}].type`}>
              {(field) => (
                <field.Select
                  disabled={hasTemplate}
                  label={$t({
                    id: "attributes.type",
                    defaultMessage: "Type",
                  })}
                  placeholder={$t({
                    id: "attributes.type.placeholder",
                    defaultMessage: "Select Type",
                  })}
                >
                  {(Object.values(AttributeType) as AttributeType[]).map(
                    (type) => (
                      <SelectItem value={type} key={type}>
                        {$t(getAttributeTypeMessage(type))}
                      </SelectItem>
                    ),
                  )}
                </field.Select>
              )}
            </form.AppField>
          </TableCell>
        )}
        {displayNameLanguages.map((lang) => (
          <TableCell key={lang}>
            <form.AppField
              name={
                isMetaAttribute
                  ? `metaAttributes[${formIndex}].displayName.${lang}`
                  : `attributes[${formIndex}].displayName.${lang}`
              }
            >
              {(field) => (
                <field.TextInput
                  label={`${$t({
                    id: "common.displayName",
                    defaultMessage: "Display Name",
                  })} (${lang.toUpperCase()})`}
                  disabled={
                    !!rule && !rule.isDisplayNameEditable && !isMetaAttribute
                  }
                  placeholder={$t({
                    id: "attributes.displayName.placeholder",
                    defaultMessage: "Enter Display Name",
                  })}
                />
              )}
            </form.AppField>
          </TableCell>
        ))}
        {!rule?.isRequired && (
          <TableCell
            className="sticky right-0"
            style={{ zIndex: 100 - displayIndex }}
          >
            <Button
              type="button"
              variant="outline"
              size="icon"
              className="relative bg-input transition-shadow group-data-[scrolled-end=false]:shadow-lg"
              onClick={() => {
                removeValue(formIndex);
              }}
            >
              <div className="absolute -inset-2 -right-4 -z-10 rounded-lg bg-linear-to-r from-transparent via-muted to-muted group-data-[scrolled-end=true]:bg-none" />
              {rule ? <IconEye /> : <IconTrash />}
            </Button>
          </TableCell>
        )}
      </TableRow>
    );
  },
});

const DisabledAttributeRow = withForm({
  defaultValues: {} as CredentialSchemaForm,
  props: {} as {
    attribute: {
      originalIndex: number;
      id: number;
      type: AttributeType;
      displayName: Record<string, string>;
      name: string;
      isSensitive: boolean;
      isDisclosable: boolean;
      isArray: boolean;
      attributeNameOverrides: Partial<Record<OverrideCredentialTypes, string>>;
    };
    displayIndex: number;
    displayNameLanguages: string[];
  },
  render: ({ form, attribute, displayIndex, displayNameLanguages }) => {
    const { $t } = useIntl();
    const { insertValue } = useField({
      form,
      name: "attributes",
    });

    return (
      <TableRow>
        <TableCell className="align-middle">
          <div className="flex">
            <Checkbox
              className="mx-auto"
              disabled
              checked={attribute.isSensitive}
            />
          </div>
        </TableCell>
        <TableCell className="align-middle">
          <div className="flex">
            <Checkbox
              className="mx-auto"
              disabled
              checked={attribute.isDisclosable}
            />
          </div>
        </TableCell>
        <TableCell>
          <Input className="line-through" value={attribute.name} disabled />
        </TableCell>
        <TableCell className="align-middle">
          <div className="flex">
            <Checkbox
              className="mx-auto"
              disabled
              checked={attribute.isArray}
            />
          </div>
        </TableCell>
        <TableCell>
          <Select disabled value={attribute.type}>
            <SelectTrigger className="line-through">
              <SelectValue
                placeholder={$t({
                  id: "attributes.type.placeholder",
                  defaultMessage: "Select Type",
                })}
              />
            </SelectTrigger>
            <SelectContent>
              {(Object.values(AttributeType) as AttributeType[]).map((type) => (
                <SelectItem value={type} key={type}>
                  {$t(getAttributeTypeMessage(type))}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </TableCell>
        {displayNameLanguages.map((lang) => (
          <TableCell key={lang}>
            <Input
              value={attribute.displayName[lang] ?? ""}
              placeholder={$t({
                id: "attributes.displayName.placeholder",
                defaultMessage: "Enter Display Name",
              })}
              className="line-through"
              disabled
            />
          </TableCell>
        ))}
        <TableCell
          className="sticky right-0"
          style={{ zIndex: 100 - displayIndex }}
        >
          <Button
            type="button"
            variant="outline"
            size="icon"
            className="relative transition-shadow group-data-[scrolled-end=false]:shadow-lg"
            onClick={() => {
              insertValue(attribute.originalIndex, {
                id: attribute.id,
                name: attribute.name,
                type: attribute.type,
                displayName: attribute.displayName,
                isSensitive: false,
                isDisclosable: false,
                isArray: false,
                attributeNameOverrides: attribute.attributeNameOverrides,
              });
            }}
          >
            <div className="absolute -inset-2 -right-6 -z-10 rounded-lg bg-linear-to-r from-transparent via-muted to-muted group-data-[scrolled-end=true]:bg-none" />
            <IconEyeClosed className="size-4" />
          </Button>
        </TableCell>
      </TableRow>
    );
  },
});

function getAttributes(
  formAttributes:
    | CredentialSchemaForm["attributes"]
    | CredentialSchemaForm["metaAttributes"],
  defaultAttributes:
    | CredentialSchemaForm["attributes"]
    | CredentialSchemaForm["metaAttributes"],
  template?: Template,
) {
  const templatePositions = new Map(
    (template?.attributes ?? []).map((attr, i) => [attr.name, i]),
  );
  const activeAttributes = formAttributes.map((attr, index) => ({
    ...attr,
    isActive: true,
    originalIndex: templatePositions.get(attr.name) ?? index,
  }));
  const disabledAttributes = (template?.attributes ?? [])
    .filter((a) => !formAttributes.some((attr) => attr.name === a.name))
    .map((attr, index) => {
      const id =
        defaultAttributes.find((a) => a.name === attr.name)?.id ??
        Number.parseInt(nanoid(), 10);
      return {
        ...attr,
        id,
        isActive: false,
        originalIndex:
          templatePositions.get(attr.name) ?? index + formAttributes.length,
      };
    });
  return [...activeAttributes, ...disabledAttributes].sort(
    (a, b) => a.originalIndex - b.originalIndex,
  ) as typeof activeAttributes | typeof disabledAttributes;
}
