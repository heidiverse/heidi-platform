// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import slugifyFn from "slugify";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input, type InputProps } from "@/components/ui/input";
import { useFieldContext } from "../form-context";

interface TextInputProps extends Omit<
  InputProps,
  "onChange" | "value" | "id" | "name"
> {
  label: string;
  slugify?: boolean;
}

export function TextInput({
  label,
  slugify = false,
  ...props
}: TextInputProps) {
  const field = useFieldContext<string>();
  const isInvalid = field.state.meta.isTouched && !field.state.meta.isValid;
  return (
    <Field data-invalid={isInvalid}>
      <FieldLabel className="sr-only" htmlFor={field.name}>
        {label}
      </FieldLabel>
      <Input
        id={field.name}
        name={field.name}
        value={field.state.value || ""}
        onBlur={field.handleBlur}
        onChange={(e) => {
          if (slugify) {
            e.target.value = slugifyFn(e.target.value, {
              locale: "de",
              trim: false,
            });
          }
          field.handleChange(e.target.value);
        }}
        {...props}
      />
      {isInvalid && <FieldError errors={field.state.meta.errors} />}
    </Field>
  );
}
