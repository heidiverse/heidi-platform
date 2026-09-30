// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { forwardRef, type HTMLAttributes, useEffect, useState } from "react";
import { cn } from "@/lib/utils";

export const CardColorInput = forwardRef<
  HTMLInputElement,
  {
    value: string;
    onValueChange: (hex: string) => void;
  } & HTMLAttributes<HTMLInputElement>
>(({ value, onValueChange, ...inputProps }, ref) => {
  const [error, setError] = useState(false);
  const [internalValue, setInternalValue] = useState(value);
  useEffect(() => {
    if (internalValue !== value) {
      setInternalValue(value);
    }
  }, [value]);
  return (
    <label
      className={cn(
        "box-border flex h-10 w-full cursor-pointer items-center overflow-clip rounded-full border bg-input px-3 py-2 text-sm outline-2 outline-offset-1 outline-transparent file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-within:outline-ring disabled:cursor-not-allowed disabled:opacity-50",
        error && "border-destructive",
      )}
    >
      <input
        ref={ref}
        type="color"
        className="h-full w-0 translate-y-1 opacity-0"
        value={`#${value}`}
        onChange={(e) => onValueChange(e.currentTarget.value.replace(/^#/, ""))}
        tabIndex={-1}
        {...inputProps}
      />
      <div
        className={cn("mr-2 -ml-3 aspect-square h-10 border-r")}
        style={{ backgroundColor: `#${value}` }}
      />
      <span className="text-sm">#</span>
      <input
        id="cardcolor"
        type="text"
        maxLength={6}
        value={internalValue}
        onChange={(e) => {
          setInternalValue(e.target.value);
          if (/^[0-9A-Fa-f]{6}$/.test(e.target.value)) {
            setError(false);
            onValueChange(e.target.value);
          } else {
            setError(true);
          }
        }}
        className="w-full min-w-0 border-none bg-transparent p-0 text-sm focus:ring-0 focus:outline-hidden"
      />
    </label>
  );
});
