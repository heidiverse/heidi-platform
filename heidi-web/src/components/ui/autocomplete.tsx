// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

"use client";

import { Command as CommandPrimitive } from "cmdk";
import * as React from "react";
import {
  Command,
  CommandGroup,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { Input } from "@/components/ui/input";
import {
  Popover,
  PopoverAnchor,
  PopoverContent,
} from "@/components/ui/popover";
import { Skeleton } from "@/components/ui/skeleton";
import type { AttributeCatalog } from "@/lib/api/attribute-catalogs/api";
import { cn } from "@/lib/utils";

type Props = {
  value: string;
  onValueChange: (value: string) => void;
  onSelectedItemChange: (item: AttributeCatalog["attributes"][number]) => void;
  items: AttributeCatalog[];
  disabled?: boolean;
  isLoading?: boolean;
  placeholder?: string;
};

export function AutoComplete({
  value,
  onValueChange,
  onSelectedItemChange,
  items,
  disabled = false,
  isLoading = false,
  placeholder = "Search...",
}: Props) {
  const inputRef = React.useRef<HTMLInputElement>(null);
  const [open, setOpen] = React.useState(false);

  function filterItems(v: string) {
    return items.map((i) =>
      v
        ? {
            ...i,
            attributes: i.attributes.filter((a) => a.attributeKey.includes(v)),
          }
        : i,
    );
  }

  const selectables = filterItems(value);

  const hasMatches = selectables.some((s) => s.attributes.length > 0);

  return (
    <div className="flex items-center">
      <Popover open={open} onOpenChange={setOpen}>
        <Command
          shouldFilter={false}
          className="bg-transparent"
          onKeyDown={(e) => {
            if (inputRef.current && e.key === "Escape") {
              inputRef.current.blur();
            }
          }}
        >
          <PopoverAnchor asChild>
            <CommandPrimitive.Input
              asChild
              ref={inputRef}
              value={value}
              onValueChange={(v) => {
                onValueChange(v);
                setOpen(filterItems(v).length > 0);
              }}
              onBlur={() => setOpen(false)}
              onFocus={() => setOpen(true)}
              disabled={disabled}
            >
              <Input placeholder={placeholder} />
            </CommandPrimitive.Input>
          </PopoverAnchor>
          <PopoverContent
            asChild
            onOpenAutoFocus={(e) => e.preventDefault()}
            onInteractOutside={(e) => {
              if (
                e.target instanceof Element &&
                e.target.hasAttribute("cmdk-input")
              ) {
                e.preventDefault();
              }
            }}
            className={cn(
              "w-full min-w-(--radix-popover-trigger-width) p-0",
              !hasMatches && "border-transparent bg-transparent",
            )}
          >
            <CommandList>
              {isLoading && (
                <CommandPrimitive.Loading>
                  <div className="p-1">
                    <Skeleton className="h-6 w-full" />
                  </div>
                </CommandPrimitive.Loading>
              )}
              {hasMatches && !isLoading && (
                <CommandGroup>
                  {selectables.map((selectable) => {
                    return (
                      <CommandGroup
                        className="p-0"
                        key={selectable.id}
                        heading={selectable.catalogDisplayName}
                      >
                        {selectable.attributes.map((attribute) => (
                          <CommandItem
                            key={attribute.attributeKey}
                            onMouseDown={(e) => {
                              e.preventDefault();
                              e.stopPropagation();
                            }}
                            onSelect={() => {
                              onSelectedItemChange(attribute);
                              setOpen(false);
                            }}
                          >
                            {attribute.attributeKey}
                          </CommandItem>
                        ))}
                      </CommandGroup>
                    );
                  })}
                </CommandGroup>
              )}
            </CommandList>
          </PopoverContent>
        </Command>
      </Popover>
    </div>
  );
}
