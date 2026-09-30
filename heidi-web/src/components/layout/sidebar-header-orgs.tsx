// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconChevronDown,
  IconPuzzle,
  IconRubberStamp,
  IconSettings,
} from "@tabler/icons-react";
import { useSuspenseQueries, useSuspenseQuery } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import { useAtom, useAtomValue } from "jotai";
import { Suspense, useState } from "react";
import { FormattedMessage, useIntl } from "react-intl";
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
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar";
import type { Organisation } from "@/lib/api/organisations/api";
import { organisationListOptions } from "@/lib/api/organisations/query-options";
import type { Settings } from "@/lib/api/settings/api";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";
import { selectedTenantAtom } from "@/lib/atoms";
import { UserRole } from "@/lib/auth/identity";
import { useUser } from "@/lib/hooks/use-user";
import { cn } from "@/lib/utils";

function OrganisationComponent({
  displayName,
  settings,
}: {
  displayName: string;
  settings: Settings;
}) {
  return (
    <>
      {settings.thumbnail ? (
        <img
          src={settings.thumbnail}
          className="-my-1 -ml-1 size-12 shrink-0 rounded-xl bg-white object-cover ring ring-glacier-160/20"
          alt={displayName}
        />
      ) : (
        <div className="-my-1 -ml-1 size-12 shrink-0 rounded-xl bg-input ring ring-glacier-160/20" />
      )}

      <div className="min-w-0">
        <p className="text-xs tracking-wider text-muted-foreground uppercase">
          <FormattedMessage
            id="common.organisation"
            defaultMessage="Organisation"
          />
        </p>
        <p className="truncate text-base font-medium">{displayName}</p>
      </div>
    </>
  );
}

function DropdownItem({
  settings,
  name,
}: {
  settings?: Settings;
  name: string;
}) {
  return (
    <>
      {settings?.thumbnail ? (
        <img
          className="size-4 shrink-0 rounded-sm bg-white"
          src={settings.thumbnail}
          alt={name}
        />
      ) : (
        <div className="size-4 shrink-0 rounded-sm bg-input" />
      )}
      <span className="truncate">{name}</span>
    </>
  );
}

function DropdownContent({
  organisations,
  organisationSettings,
  setOpen,
}: {
  organisations: Organisation[];
  organisationSettings: Settings[];
  setOpen: (open: boolean) => void;
}) {
  const { $t } = useIntl();
  const navigate = useNavigate();
  const params = useParams({ strict: false });
  const [, setSelectedOrganisation] = useAtom(selectedTenantAtom);

  return (
    <Command className="rounded-none bg-transparent text-white [&_[cmdk-input-wrapper]]:border-glacier-160">
      <CommandInput
        autoFocus
        placeholder={`${$t({ id: "common.search", defaultMessage: "Search" })}...`}
        className="text-primary-foreground"
      />
      <CommandList>
        <CommandEmpty>
          <FormattedMessage
            id="common.noResultsFound"
            defaultMessage="No Results Found"
          />
        </CommandEmpty>
        <CommandGroup
          heading={$t({
            id: "common.management",
            defaultMessage: "Management",
          })}
          className="text-inherit"
        >
          <CommandItem
            asChild
            className="rounded-lg! transition-colors data-[selected=true]:bg-glacier-140 data-[selected=true]:text-text-invert"
          >
            <Link className="cursor-pointer" to="/organisations">
              <IconSettings />
              <FormattedMessage
                id="sidebar.manageOrganisations"
                defaultMessage="Manage Organisations"
              />
            </Link>
          </CommandItem>
          <CommandItem
            asChild
            className="rounded-lg! transition-colors data-[selected=true]:bg-glacier-140 data-[selected=true]:text-text-invert"
          >
            <Link className="cursor-pointer" to="/extensions">
              <IconPuzzle />
              <FormattedMessage
                id="sidebar.manageExtensions"
                defaultMessage="Manage Extensions"
              />
            </Link>
          </CommandItem>
          <CommandItem
            asChild
            className="rounded-lg! transition-colors data-[selected=true]:bg-glacier-140 data-[selected=true]:text-text-invert"
          >
            <Link className="cursor-pointer" to="/issuer-definitions">
              <IconRubberStamp />
              <FormattedMessage
                id="sidebar.manageIssuerDefinitions"
                defaultMessage="Manage identities"
              />
            </Link>
          </CommandItem>
        </CommandGroup>
        <CommandGroup
          heading={$t({
            id: "common.organisation",
            defaultMessage: "Organisation",
          })}
          className="text-inherit"
        >
          {organisations.map((org, index) => (
            <CommandItem
              className="rounded-lg! transition-colors data-[selected=true]:bg-glacier-140 data-[selected=true]:text-text-invert"
              key={org.tenantId}
              onSelect={() => {
                setOpen(false);
                setSelectedOrganisation(org.tenantId);
                if (Object.keys(params).length > 0) {
                  navigate({ to: "/" });
                }
              }}
            >
              <DropdownItem
                settings={organisationSettings[index]}
                name={org.displayName}
              />
            </CommandItem>
          ))}
        </CommandGroup>
      </CommandList>
    </Command>
  );
}

export function SidebarHeaderOrgs() {
  const user = useUser();
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const tenantId = selectedTenant || user.tenantId;
  const isSuperAdmin = user.roles.includes(UserRole.SuperAdmin);
  const [open, setOpen] = useState(false);
  const { data: settings } = useSuspenseQuery(
    settingsForOrganisationOptions(tenantId),
  );
  // The organisation list only exists where several are reachable; a single-tenant
  // deployment reads its name straight from the organisation it is already loading.
  const organisationListQueries = isSuperAdmin
    ? [organisationListOptions()]
    : [];
  const organisations = useSuspenseQueries({
    queries: organisationListQueries,
    combine: (results) => results.flatMap((result) => result.data),
  });
  const organisationSettings = useSuspenseQueries({
    queries: isSuperAdmin
      ? organisations.map((org) =>
          settingsForOrganisationOptions(org.tenantId),
        )
      : [],
    combine: (results) => results.map((result) => result.data),
  });
  const displayName = settings.displayName || settings.tenantId;

  return (
    <SidebarHeader>
      <SidebarMenu>
        <SidebarMenuItem>
          {isSuperAdmin ? (
            <Popover open={open} onOpenChange={setOpen}>
              <PopoverTrigger asChild>
                <SidebarMenuButton className="h-auto rounded-2xl">
                  <Suspense fallback={<OrganisationFallback />}>
                    <OrganisationComponent
                      displayName={displayName}
                      settings={settings}
                    />
                  </Suspense>
                  <IconChevronDown className="ml-auto" />
                </SidebarMenuButton>
              </PopoverTrigger>
              <PopoverContent className="w-(--radix-popper-anchor-width) border-glacier-160 bg-glacier-180 p-0 shadow-xl">
                <DropdownContent
                  setOpen={setOpen}
                  organisations={organisations}
                  organisationSettings={organisationSettings}
                />
              </PopoverContent>
            </Popover>
          ) : (
            <div className="flex items-center gap-2 p-2">
              <Suspense fallback={<OrganisationFallback />}>
                <OrganisationComponent
                  displayName={displayName}
                  settings={settings}
                />
              </Suspense>
            </div>
          )}
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarHeader>
  );
}

export function OrganisationFallback({ isError }: { isError?: boolean }) {
  return (
    <SidebarHeader>
      <SidebarMenu>
        <SidebarMenuItem>
          <div className="flex items-center gap-2 p-2">
            <div
              className={cn(
                "-my-1 -ml-1 size-12 rounded-md bg-input",
                !isError && "animate-pulse",
              )}
            />
            <div className="min-w-0">
              <p className="text-xs tracking-wider text-muted-foreground uppercase">
                <FormattedMessage
                  id="sidebar.organisation"
                  defaultMessage="Organisation"
                />
              </p>
              <p className="truncate text-base font-medium">
                {isError ? "Error" : "Loading..."}
              </p>
            </div>
          </div>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarHeader>
  );
}
