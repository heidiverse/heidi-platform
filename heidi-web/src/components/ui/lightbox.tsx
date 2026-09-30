// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconX } from "@tabler/icons-react";
import { Dialog as DialogPrimitive } from "radix-ui";
import * as React from "react";
import { cn } from "@/lib/utils";

const Lightbox = DialogPrimitive.Root;

const LightboxTrigger = DialogPrimitive.Trigger;

const LightboxPortal = DialogPrimitive.Portal;

const LightboxClose = DialogPrimitive.Close;

const LightboxOverlay = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Overlay>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Overlay>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Overlay
    ref={ref}
    className={cn(
      "fixed inset-0 z-50 bg-black/20 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:animate-in data-[state=open]:fade-in-0",
      className,
    )}
    {...props}
  />
));
LightboxOverlay.displayName = DialogPrimitive.Overlay.displayName;

const LightboxContent = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Content>
>(({ className, children, ...props }, ref) => (
  <LightboxPortal>
    <LightboxOverlay />
    <DialogPrimitive.Content
      ref={ref}
      className={cn(
        "pointer-events-none! fixed inset-0 z-50 flex items-center justify-center outline-hidden duration-200 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:slide-out-to-top-10 data-[state=closed]:zoom-out-95 data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:slide-in-from-top-10 data-[state=open]:zoom-in-95 sm:px-6",
        className,
      )}
      {...props}
    >
      <div className="pointer-events-auto relative flex size-full max-w-5xl flex-col bg-[#848D97] sm:h-auto sm:max-h-[95%] sm:rounded-[2.5rem] sm:p-5">
        <div className="size-full flex-1 overflow-hidden overflow-y-auto bg-white sm:rounded-[1.25rem]">
          <LightboxClose className="absolute top-1 right-1 z-10 grid size-16 place-items-center rounded-full bg-white outline-2 outline-offset-2 outline-transparent focus-visible:outline-ring sm:-top-3 sm:-right-3 sm:shadow-md">
            <IconX className="size-8" />
          </LightboxClose>
          {children}
        </div>
      </div>
    </DialogPrimitive.Content>
  </LightboxPortal>
));
LightboxContent.displayName = DialogPrimitive.Content.displayName;

const LightboxHeader = ({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) => (
  <div className={cn("flex flex-col space-y-1.5", className)} {...props} />
);
LightboxHeader.displayName = "DialogHeader";

const LightboxFooter = ({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) => (
  <div className={cn("flex justify-between gap-2", className)} {...props} />
);
LightboxFooter.displayName = "DialogFooter";

const LightboxTitle = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Title>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Title>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Title
    ref={ref}
    className={cn("text-2xl leading-6 font-semibold", className)}
    {...props}
  />
));
LightboxTitle.displayName = DialogPrimitive.Title.displayName;

const LightboxDescription = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Description>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Description>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Description
    ref={ref}
    className={cn("text-sm text-muted-foreground", className)}
    {...props}
  />
));
LightboxDescription.displayName = DialogPrimitive.Description.displayName;

export {
  Lightbox,
  LightboxClose,
  LightboxContent,
  LightboxDescription,
  LightboxFooter,
  LightboxHeader,
  LightboxOverlay,
  LightboxPortal,
  LightboxTitle,
  LightboxTrigger,
};
