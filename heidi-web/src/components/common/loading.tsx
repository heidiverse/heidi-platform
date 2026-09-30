// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconLoader2 } from "@tabler/icons-react";
import { cn } from "@/lib/utils";

export function Loading({ className = "" }: { className?: string }) {
  return (
    <div
      className={cn(
        "pointer-events-none fixed inset-y-0 right-0 left-0 grid animate-in place-items-center delay-200 duration-500 fade-in-0 fill-mode-backwards group-has-data-[state=expanded]/sidebar-wrapper:left-(--sidebar-width)",
        className,
      )}
    >
      <IconLoader2 className="size-8 animate-spin text-foreground/50" />
    </div>
  );
}
