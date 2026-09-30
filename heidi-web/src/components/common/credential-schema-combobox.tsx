// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { type ComponentPropsWithoutRef, type ReactNode, useState } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { compare } from "semver";
import { Badge } from "@/components/ui/badge";
import { Button, type ButtonProps } from "@/components/ui/button";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  SegmentedControl,
  SegmentedControlItem,
} from "@/components/ui/segmented-control";
import { argbToHex } from "@/lib/utils/color";
import {
  type CredentialSchemaLite,
  TextColor,
} from "@/types/credential-schema";

export function CredentialSchemaCombobox({
  hideVersion = false,
  popoverProps,
  commandProps,
  commandItemProps,
  buttonProps,
  schemas,
  children,
  initialShowLatest = true,
}: {
  hideVersion?: boolean;
  popoverProps?: ComponentPropsWithoutRef<typeof Popover>;
  commandProps?: ComponentPropsWithoutRef<typeof Command>;
  commandItemProps?: (
    schema: CredentialSchemaLite,
  ) => ComponentPropsWithoutRef<typeof CommandItem>;
  buttonProps?: ButtonProps;
  schemas: CredentialSchemaLite[];
  children: ReactNode;
  initialShowLatest?: boolean;
}) {
  const { $t } = useIntl();

  const [showLatest, setShowLatest] = useState(initialShowLatest);
  const filteredSchemas = showLatest
    ? Array.from(
        schemas
          .reduce((map, cur) => {
            const existing = map.get(cur.credentialIdentifier);
            if (!existing || compare(existing.version, cur.version) < 0) {
              map.set(cur.credentialIdentifier, cur);
            }
            return map;
          }, new Map<string, CredentialSchemaLite>())
          .values(),
      )
    : schemas
        .sort((a, b) => compare(a.version, b.version))
        .sort((a, b) =>
          a.credentialIdentifier.localeCompare(b.credentialIdentifier),
        );

  return (
    <Popover {...popoverProps}>
      <PopoverTrigger asChild>
        <Button
          {...buttonProps}
          role="combobox"
          aria-expanded={popoverProps?.open}
        >
          {children}
        </Button>
      </PopoverTrigger>
      <PopoverContent
        collisionPadding={16}
        className="w-auto max-w-[calc(100vw-var(--spacing)*8)] min-w-(--radix-popover-trigger-width) p-0"
      >
        <Command {...commandProps}>
          <SegmentedControl
            className="-m-px rounded-none"
            value={showLatest ? "latest" : "all"}
            onValueChange={(value) => {
              setShowLatest(value === "latest");
            }}
          >
            <SegmentedControlItem
              labelClassName="rounded-tl-xl! rounded"
              value="latest"
            >
              <FormattedMessage
                id="common.versions.latest"
                defaultMessage="Latest versions"
              />
            </SegmentedControlItem>
            <SegmentedControlItem
              labelClassName="rounded-tr-xl! rounded"
              value="all"
            >
              <FormattedMessage
                id="common.versions.all"
                defaultMessage="Show all"
              />
            </SegmentedControlItem>
          </SegmentedControl>
          <CommandInput
            autoFocus
            placeholder={`${$t({ id: "common.search", defaultMessage: "Search" })}...`}
          />
          <CommandList>
            <CommandEmpty>
              <FormattedMessage
                id="credentialSchemaCombobox.noSchemasFound"
                defaultMessage="No Credential Schemas found"
              />
            </CommandEmpty>
            <CommandGroup>
              {filteredSchemas.map((schema) => {
                return (
                  <CommandItem
                    key={schema.id}
                    value={schema.id}
                    keywords={[
                      schema.displayName,
                      schema.credentialIdentifier,
                      schema.version,
                    ].filter(Boolean)}
                    {...commandItemProps?.(schema)}
                  >
                    <ComboboxSchemaItem
                      hideVersion={hideVersion}
                      schema={schema}
                    />
                  </CommandItem>
                );
              })}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}

export function ComboboxSchemaItem({
  schema,
  hideVersion = false,
  hideIdentifier = false,
}: {
  schema?: CredentialSchemaLite;
  hideVersion?: boolean;
  hideIdentifier?: boolean;
}) {
  return (
    <div className="flex w-full items-center gap-3">
      {schema ? (
        <>
          <img
            src={
              schema.credentialSchemeStyleDetails[0]?.style.textColor ===
              TextColor.Dark
                ? "/assets/card-overlay-light.svg"
                : "/assets/card-overlay-dark.svg"
            }
            alt="Card Overlay"
            style={{
              backgroundColor: `#${argbToHex(schema.credentialSchemeStyleDetails[0]?.style.cardColor)}`,
            }}
            className="aspect-cc h-6 shrink-0 rounded border object-cover"
          />
          <div className="overflow-hidden">
            <p className="truncate text-sm">
              {schema.displayName || schema.credentialIdentifier}
            </p>
            {!hideIdentifier && (
              <p className="truncate text-xs text-muted-foreground">
                {schema.credentialIdentifier}
              </p>
            )}
          </div>
          {!hideVersion && (
            <Badge variant="secondary" className="ml-auto">
              {schema.version}
            </Badge>
          )}
        </>
      ) : (
        <span className="text-sm">
          <FormattedMessage
            id="credentialSchemaCombobox.select"
            defaultMessage="Select Credential Schema"
          />
        </span>
      )}
    </div>
  );
}
