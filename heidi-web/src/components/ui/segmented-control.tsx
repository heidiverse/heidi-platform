// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { RadioGroup as RadioGroupPrimitive } from "radix-ui";
import * as React from "react";

import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";

const SegmentedControl = React.forwardRef<
  React.ElementRef<typeof RadioGroupPrimitive.Root>,
  React.ComponentPropsWithoutRef<typeof RadioGroupPrimitive.Root>
>(({ className, ...props }, ref) => {
  return (
    <RadioGroupPrimitive.Root
      className={cn(
        "grid h-10 auto-cols-fr grid-flow-col rounded-full border bg-muted p-0.75",
        className,
      )}
      {...props}
      ref={ref}
    />
  );
});
SegmentedControl.displayName = RadioGroupPrimitive.Root.displayName;

const SegmentedControlItem = React.forwardRef<
  React.ElementRef<typeof RadioGroupPrimitive.Item>,
  React.ComponentPropsWithoutRef<typeof RadioGroupPrimitive.Item> & {
    labelClassName?: string;
  }
>(({ className, children, labelClassName = "", ...props }, ref) => {
  return (
    <div>
      <RadioGroupPrimitive.Item
        ref={ref}
        className={cn("peer sr-only", className)}
        id={props.value}
        {...props}
      >
        <RadioGroupPrimitive.Indicator />
      </RadioGroupPrimitive.Item>
      <Label
        className={cn(
          "block w-full cursor-pointer truncate rounded-full px-3 py-1.5 text-center text-sm font-medium text-muted-foreground ring ring-transparent outline-2 outline-offset-2 outline-transparent transition-colors peer-focus-visible:outline-ring peer-data-[state=checked]:bg-surface peer-data-[state=checked]:text-text-primary peer-data-[state=checked]:ring-border",
          labelClassName,
        )}
        htmlFor={props.value}
      >
        {children}
      </Label>
    </div>
  );
});
SegmentedControlItem.displayName = RadioGroupPrimitive.Item.displayName;

export { SegmentedControl, SegmentedControlItem };
