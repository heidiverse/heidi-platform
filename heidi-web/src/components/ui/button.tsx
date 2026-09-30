// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { cva, type VariantProps } from "class-variance-authority";
import { Slot as SlotPrimitive } from "radix-ui";
import * as React from "react";

import { cn } from "@/lib/utils";

const buttonVariants = cva(
  "inline-flex shrink-0 items-center justify-center gap-2.5 rounded-full text-sm leading-none font-bold tracking-wide whitespace-nowrap uppercase outline-2 outline-offset-2 outline-transparent transition-colors focus-visible:outline-ring disabled:pointer-events-none disabled:opacity-50 [&>svg]:size-5",
  {
    variants: {
      variant: {
        default: "bg-primary text-text-invert hover:bg-primary/60",
        secondary:
          "bg-secondary text-secondary-foreground hover:bg-secondary/80",
        tertiary: "bg-glacier-100 text-primary-foreground hover:bg-glacier-80",
        outline:
          "border bg-background text-foreground hover:bg-accent hover:text-accent-foreground",
        ghost: "hover:bg-accent hover:text-accent-foreground",
        destructive:
          "bg-destructive text-destructive-foreground hover:bg-destructive/80",
        success:
          "bg-infocard-success-light text-infocard-success hover:bg-infocard-success-light/50",
        warning:
          "bg-infocard-warning-light text-infocard-warning hover:bg-infocard-warning-light/50",
        link: "text-primary underline-offset-4 hover:underline",
        verifier:
          "bg-verifier-blue px-8! py-6 text-base text-text-invert hover:bg-verifier-lightBlue",
      },
      size: {
        default: "h-10 px-4",
        icon: "h-10 w-10",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, ...props }, ref) => {
    const Comp = asChild ? SlotPrimitive.Slot : "button";
    return (
      <Comp
        type="button"
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      />
    );
  },
);
Button.displayName = "Button";

export { Button, buttonVariants };
