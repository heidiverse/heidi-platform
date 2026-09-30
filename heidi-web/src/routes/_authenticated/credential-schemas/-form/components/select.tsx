// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { Select as SelectPrimitive } from "radix-ui";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import {
  Select as SelectComponent,
  SelectContent,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useFieldContext } from "../form-context";

interface SelectProps extends Omit<
  React.ComponentPropsWithoutRef<typeof SelectPrimitive.Root>,
  "name" | "value" | "onValueChange"
> {
  label: string;
  placeholder?: string;
}

export function Select({
  label,
  placeholder,
  children,
  ...props
}: SelectProps) {
  const field = useFieldContext<string>();
  const isInvalid = field.state.meta.isTouched && !field.state.meta.isValid;
  return (
    <Field data-invalid={isInvalid}>
      <FieldLabel className="sr-only" htmlFor={field.name}>
        {label}
      </FieldLabel>
      <SelectComponent
        value={field.state.value || ""}
        onValueChange={field.handleChange}
        {...props}
      >
        <SelectTrigger id={field.name} aria-invalid={isInvalid}>
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
        <SelectContent>{children}</SelectContent>
      </SelectComponent>
      {isInvalid && <FieldError errors={field.state.meta.errors} />}
    </Field>
  );
}
