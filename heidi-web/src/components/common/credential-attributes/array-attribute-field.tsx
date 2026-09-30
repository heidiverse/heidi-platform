// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconPlus, IconTrash } from "@tabler/icons-react";
import { createFormHook, createFormHookContexts } from "@tanstack/react-form";
import { FormattedMessage } from "react-intl";
import { AttributeField } from "@/components/common/credential-attributes/attribute-field";
import { Button } from "@/components/ui/button";
import type { AttributeType } from "@/types/credential-schema";

export const { fieldContext, formContext, useFieldContext } =
  createFormHookContexts();

export const { useAppForm: useAttributeArrayForm, withForm } = createFormHook({
  fieldContext,
  formContext,
  fieldComponents: {},
  formComponents: {},
});

export type AttributeArrayFieldProps = {
  fieldName: string;
  label: string;
  type: AttributeType;
  values: string[];
  onChange: (values: string[]) => void;
};

export function AttributeArrayField({
  fieldName,
  label,
  type,
  values,
  onChange,
}: AttributeArrayFieldProps) {
  function updateAt(index: number, value: string) {
    const nextValues = [...values];
    nextValues[index] = value;
    onChange(nextValues);
  }

  function add() {
    onChange([...values, ""]);
  }

  function remove(index: number) {
    onChange(values.filter((_, i) => i !== index));
  }

  const lastValue = values.at(-1) ?? "";

  return (
    <div className="space-y-4">
      {values.map((value, index) => (
        <div
          key={`${fieldName}-${index.toString()}`}
          className="flex items-end gap-2"
        >
          <div className="grow">
            <AttributeField
              fieldName={`${fieldName}-${index}`}
              label={index === 0 ? label : ""}
              type={type}
              value={value}
              onValueChange={(nextValue) => updateAt(index, nextValue)}
            />
          </div>
          {index > 0 && (
            <Button
              type="button"
              variant="outline"
              size="icon"
              onClick={() => remove(index)}
            >
              <IconTrash />
            </Button>
          )}
        </div>
      ))}
      <Button
        disabled={lastValue === ""}
        type="button"
        variant="tertiary"
        onClick={add}
      >
        <span className="flex items-center gap-2">
          <IconPlus className="size-4" />
          <FormattedMessage
            id="common.add.withValue"
            defaultMessage="Add {value}"
            values={{ value: label }}
          />
        </span>
      </Button>
    </div>
  );
}

export const FormAttributeArrayField = withForm({
  defaultValues: {} as {
    attributes: Record<string, string | string[]>;
  },
  props: {} as {
    type: AttributeType;
    attributeName: string;
    label: string;
  },
  render({ form, type, attributeName, label }) {
    return (
      <form.AppField name={`attributes.${attributeName}`} mode="array">
        {(field) => {
          if (!Array.isArray(field.state.value)) {
            return null;
          }

          return (
            <AttributeArrayField
              fieldName={attributeName}
              label={label}
              type={type}
              values={field.state.value}
              onChange={(values) => field.handleChange(values)}
            />
          );
        }}
      </form.AppField>
    );
  },
});
