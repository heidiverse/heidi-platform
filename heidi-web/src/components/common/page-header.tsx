// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { useIntersectionObserver } from "usehooks-ts";
import { cn } from "@/lib/utils";

export function PageHeader({
  heading,
  className = "",
  children,
}: {
  heading: ReactNode;
  className?: string;
  children?: ReactNode;
}) {
  const { ref, entry } = useIntersectionObserver({ threshold: [1] });

  return (
    <div
      ref={ref}
      className={cn(
        "sticky -top-px z-10 -mx-4 border-b border-transparent bg-background sm:-mx-8 md:mr-0 md:ml-[min(calc((100cqw-var(--container-6xl)+var(--spacing)*16)/-2),var(--spacing)*-8)] md:w-[100cqw]",
        entry && entry.intersectionRatio < 1 && "border-border",
      )}
    >
      <div
        className={cn(
          "mx-auto flex min-h-10 max-w-6xl flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4 py-4.5 sm:px-8",
          className,
        )}
      >
        <h1 className="max-w-full truncate text-3xl font-bold">{heading}</h1>
        {children}
      </div>
    </div>
  );
}
