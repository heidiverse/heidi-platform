// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { Toaster as Sonner, type ToasterProps } from "sonner";

export function Toaster({ ...props }: ToasterProps) {
  return (
    <Sonner
      className="toaster group"
      toastOptions={{
        classNames: {
          toast:
            "group toast group-[.toaster]:border-border! group-[.toaster]:bg-card! group-[.toaster]:text-foreground! group-[.toaster]:shadow-lg! group-[.toaster]:rounded-2xl!",
          description: "group-[.toast]:text-muted-foreground!",
        },
      }}
      {...props}
    />
  );
}
