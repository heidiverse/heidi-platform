// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { Checkbox as CheckboxPrimitive } from "radix-ui";
import { Checkbox as CheckboxComponent } from "@/components/ui/checkbox";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { useFieldContext } from "../form-context";

interface TextareaProps extends Omit<
  React.ComponentPropsWithoutRef<typeof CheckboxPrimitive.Root>,
  "checked" | "onCheckedChange" | "id" | "name"
> {
  label: string;
}

export function Checkbox({ label, ...props }: TextareaProps) {
  const field = useFieldContext<boolean>();
  const isInvalid = field.state.meta.isTouched && !field.state.meta.isValid;
  return (
    <Field orientation="inline" data-invalid={isInvalid}>
      <FieldLabel className="sr-only" htmlFor={field.name}>
        {label}
      </FieldLabel>
      <CheckboxComponent
        id={field.name}
        name={field.name}
        checked={field.state.value}
        onCheckedChange={(checked) => field.handleChange(checked === true)}
        aria-invalid={isInvalid}
        {...props}
      />
      {isInvalid && <FieldError errors={field.state.meta.errors} />}
    </Field>
  );
}
