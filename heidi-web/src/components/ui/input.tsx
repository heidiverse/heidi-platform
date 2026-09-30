// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { TablerIcon } from "@tabler/icons-react";
import * as React from "react";
import { cn } from "@/lib/utils";

export interface InputProps
  extends React.InputHTMLAttributes<HTMLInputElement> {
  icon?: TablerIcon;
  valueValid?: boolean;
  iconPosition?: "start" | "end";
  inputClassName?: string;
}

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  (
    {
      className,
      type,
      icon: Icon,
      iconPosition = "start",
      valueValid = true,
      inputClassName = "",
      ...props
    },
    ref,
  ) => {
    return (
      <div
        className={cn(
          "relative flex items-center",
          iconPosition === "start" ? "justify-start" : "justify-end",
          className,
        )}
      >
        <input
          type={type}
          className={cn(
            "flex h-10 w-full rounded-full border bg-input px-3 py-2 text-sm outline-2 outline-offset-2 outline-transparent transition-colors file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:outline-ring disabled:cursor-not-allowed disabled:opacity-50",
            Icon && iconPosition === "start" && "pl-9",
            Icon && iconPosition === "end" && "pr-9",
            !valueValid && "border-red-700",
            inputClassName,
          )}
          ref={ref}
          {...props}
        />
        {Icon && (
          <div aria-hidden className="pointer-events-none absolute mx-3">
            <Icon className="size-4.5 text-muted-foreground" />
          </div>
        )}
      </div>
    );
  },
);
Input.displayName = "Input";

export { Input };
