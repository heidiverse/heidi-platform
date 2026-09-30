// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconLogout, IconUser } from "@tabler/icons-react";
import { useAtomValue } from "jotai";
import { useState } from "react";
import { FormattedMessage } from "react-intl";
import { Breadcrumbs } from "@/components/layout/breadcrumbs";
import { UpdateProfileDialog } from "@/components/layout/update-profile-dialog";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  SegmentedControl,
  SegmentedControlItem,
} from "@/components/ui/segmented-control";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { localeAtom } from "@/lib/atoms";
import { LOCALES } from "@/lib/constants";
import { useUser } from "@/lib/hooks/use-user";
import { runtimeConfig } from "@/lib/runtime-config";

export function Header() {
  const user = useUser();
  const locale = useAtomValue(localeAtom);
  const [isUpdateProfileDialogOpen, setIsUpdateProfileDialogOpen] =
    useState(false);

  return (
    <header className="mx-auto flex w-full max-w-6xl flex-col-reverse items-start justify-between gap-4 py-4.5 sm:flex-row sm:items-center">
      <div className="flex max-w-full min-w-0 items-center gap-2">
        <SidebarTrigger variant="ghost" className="p-1 text-foreground" />
        <Breadcrumbs />
      </div>
      <div className="flex shrink-0 flex-row-reverse items-center gap-2 sm:flex-row">
        <div className="sm:text-right">
          <p className="text-sm font-medium">{user.displayName}</p>
          <p className="text-xs text-neutral-500">{user.email}</p>
        </div>
        <div className="grid size-13.5 shrink-0 place-items-center rounded-full border bg-card shadow-xs">
          <DropdownMenu>
            <DropdownMenuTrigger className="rounded-full outline-2 outline-offset-9 outline-transparent transition-colors focus-visible:outline-ring">
              <Avatar>
                <AvatarImage
                  src={user.picture || undefined}
                  alt={user.displayName}
                />
                <AvatarFallback>
                  {user.displayName.substring(0, 2).toUpperCase()}
                </AvatarFallback>
              </Avatar>
            </DropdownMenuTrigger>
            <DropdownMenuContent
              collisionPadding={16}
              align="end"
              sideOffset={12}
            >
              <div className="mb-1">
                <SegmentedControl value={locale} className="rounded-xl">
                  {LOCALES.map((item) => (
                    <SegmentedControlItem
                      labelClassName="cursor-not-allowed rounded-lg opacity-50"
                      value={item}
                      key={item}
                      disabled
                    >
                      {item.toUpperCase()}
                    </SegmentedControlItem>
                  ))}
                </SegmentedControl>
                <p className="mt-1 text-center text-xs text-muted-foreground">
                  <FormattedMessage
                    id="common.languageComingSoon"
                  />
                </p>
              </div>

              <DropdownMenuItem
                onClick={() => setIsUpdateProfileDialogOpen(true)}
              >
                <IconUser />
                <FormattedMessage
                  id="common.profile"
                  defaultMessage="Profile"
                />
              </DropdownMenuItem>
              {runtimeConfig.logoutUrl && (
                <DropdownMenuItem asChild>
                  <a
                    href={runtimeConfig.logoutUrl}
                    className="flex items-center gap-2"
                  >
                    <IconLogout />
                    <FormattedMessage
                      id="common.logout"
                      defaultMessage="Logout"
                    />
                  </a>
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
          <UpdateProfileDialog
            open={isUpdateProfileDialogOpen}
            onOpenChange={setIsUpdateProfileDialogOpen}
          />
        </div>
      </div>
    </header>
  );
}
