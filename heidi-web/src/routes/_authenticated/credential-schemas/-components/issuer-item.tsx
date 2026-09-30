// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconPhotoOff } from "@tabler/icons-react";
import type { IssuerDefinition } from "@/lib/api/issuer-definitions/api";
import { DEFAULT_LOCALE } from "@/lib/constants";
import { cn } from "@/lib/utils";
import { getLocalizedValue } from "@/lib/utils/localized";

export function IssuerItem({
  issuer,
  fallbackLanguage = DEFAULT_LOCALE,
  className = "",
  isDefault = false,
}: {
  issuer?: IssuerDefinition;
  fallbackLanguage?: string;
  className?: string;
  isDefault?: boolean;
}) {
  if (!issuer) {
    return null;
  }

  return (
    <div
      className={cn(
        "flex items-center gap-2 text-sm whitespace-nowrap",
        className,
      )}
    >
      <div className="relative">
        {isDefault && (
          <div className="absolute -top-0.5 -right-0.5 size-2 rounded-full bg-glacier" />
        )}
        <div className="size-6 overflow-hidden rounded-md border">
          {issuer.logo ? (
            <img
              className="size-full"
              src={issuer.logo}
              alt="Identity logo"
            />
          ) : (
            <div className="grid size-full place-items-center">
              <IconPhotoOff className="size-4" />
            </div>
          )}
        </div>
      </div>
      {getLocalizedValue(issuer.displayName, fallbackLanguage) ?? issuer.slug}
    </div>
  );
}
