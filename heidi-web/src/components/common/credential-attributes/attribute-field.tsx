// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { format, formatISO, parseISO } from "date-fns";
import type { ComponentProps } from "react";
import { ImageField } from "@/components/common/credential-attributes/image-field";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { AttributeType } from "@/types/credential-schema";

export function AttributeField({
  fieldName,
  label,
  type,
  value,
  onValueChange,
  disabled,
  placeholder,
}: {
  fieldName: string;
  label: string;
  type: AttributeType;
  value: string;
  onValueChange: (v: string) => void;
  disabled?: boolean;
  placeholder?: string;
}) {
  const containerProps: ComponentProps<"div"> = {
    className: "flex flex-col min-h-10 gap-1",
  };
  const labelProps: ComponentProps<"label"> = {
    htmlFor: `input-${fieldName}`,
    className: "leading-5",
  };

  if (type === AttributeType.Image) {
    if (disabled) {
      return (
        <div {...containerProps}>
          <Label {...labelProps}>{label}</Label>
          <Input disabled placeholder={placeholder} />
        </div>
      );
    }
    return (
      <div {...containerProps}>
        <Label className={cn(labelProps.className, "mb-2")}>
          {placeholder || label}
        </Label>
        <ImageField defaultValue={value} onValueChange={onValueChange} />
      </div>
    );
  }

  if (type === AttributeType.Number) {
    return (
      <div {...containerProps}>
        <Label {...labelProps}>{label}</Label>
        <Input
          type="number"
          id={`input-${fieldName}`}
          value={value}
          onChange={(e) => onValueChange(e.target.value)}
          disabled={disabled}
          placeholder={placeholder}
        />
      </div>
    );
  }
  if (type === AttributeType.Date || type === AttributeType.DateOfBirth) {
    return (
      <div {...containerProps}>
        <Label {...labelProps}>{label}</Label>
        <Input
          id={`input-${fieldName}`}
          type="date"
          value={value}
          onChange={(e) => onValueChange(e.target.value)}
          disabled={disabled}
          placeholder={placeholder}
        />
      </div>
    );
  }
  if (type === AttributeType.DateTime) {
    return (
      <div {...containerProps}>
        <Label {...labelProps}>{label}</Label>
        <Input
          id={`input-${fieldName}`}
          type="datetime-local"
          value={value ? format(parseISO(value), "yyyy-MM-dd'T'HH:mm") : ""}
          onChange={(e) => onValueChange(formatISO(e.target.value))}
          disabled={disabled}
          placeholder={placeholder}
        />
      </div>
    );
  }
  if (type === AttributeType.Time) {
    return (
      <div {...containerProps}>
        <Label {...labelProps}>{label}</Label>
        <Input
          id={`input-${fieldName}`}
          type="time"
          value={value ? format(parseISO(`1970-01-01T${value}`), "HH:mm") : ""}
          onChange={(e) => {
            onValueChange(
              format(parseISO(`1970-01-01T${e.target.value}`), "HH:mm:ssXXX"),
            );
          }}
          disabled={disabled}
          placeholder={placeholder}
        />
      </div>
    );
  }
  if (type === AttributeType.Boolean) {
    return (
      <div {...containerProps}>
        <Label {...labelProps}>{label}</Label>
        {placeholder && (
          <p className="text-xs text-muted-foreground">{placeholder}</p>
        )}
        <Switch
          id={`input-${fieldName}`}
          checked={value === "true"}
          onCheckedChange={(v) => onValueChange(v ? "true" : "false")}
          disabled={disabled}
        />
      </div>
    );
  }

  if (type === AttributeType.Mail) {
    return (
      <div {...containerProps}>
        <Label {...labelProps}>{label}</Label>
        <Input
          type="email"
          id={`input-${fieldName}`}
          value={value}
          onChange={(e) => onValueChange(e.target.value)}
          disabled={disabled}
          placeholder={placeholder}
        />
      </div>
    );
  }

  if (type === AttributeType.Phone) {
    return (
      <div {...containerProps}>
        <Label {...labelProps}>{label}</Label>
        <Input
          type="tel"
          id={`input-${fieldName}`}
          value={value}
          onChange={(e) => onValueChange(e.target.value)}
          disabled={disabled}
          placeholder={placeholder}
        />
      </div>
    );
  }

  if (type === AttributeType.Other) {
    return (
      <div {...containerProps}>
        <Label {...labelProps}>{label}</Label>
        <Textarea
          id={`input-${fieldName}`}
          value={value}
          onChange={(e) => onValueChange(e.target.value)}
          disabled={disabled}
          placeholder={placeholder}
        />
      </div>
    );
  }
  return (
    <div {...containerProps}>
      <Label {...labelProps}>{label}</Label>
      <Input
        type="text"
        id={`input-${fieldName}`}
        value={value}
        onChange={(e) => onValueChange(e.target.value)}
        disabled={disabled}
        placeholder={placeholder}
      />
    </div>
  );
}
