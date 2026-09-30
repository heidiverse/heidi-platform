// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconArrowLeft,
  IconBuilding,
  IconPuzzle,
  IconRubberStamp,
} from "@tabler/icons-react";
import { Link } from "@tanstack/react-router";
import { type ReactNode, useEffect } from "react";
import { FormattedMessage } from "react-intl";
import { useLocalStorage } from "usehooks-ts";
import { Header } from "@/components/layout/header";
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  useSidebar,
} from "@/components/ui/sidebar";

export function OrganisationsLayout({ children }: { children?: ReactNode }) {
  const [open, setOpen] = useLocalStorage("sidebar:state", true);
  useEffect(() => {
    document.documentElement.classList.add("organisations");
    return () => {
      document.documentElement.classList.remove("organisations");
    };
  }, []);
  return (
    <SidebarProvider open={open} onOpenChange={setOpen}>
      <AppSidebar />
      <main className="@container w-full">
        <div className="mx-auto w-full max-w-6xl min-w-0 px-4 pb-8 sm:px-8">
          <Header />
          {children}
        </div>
      </main>
    </SidebarProvider>
  );
}

function AppSidebar() {
  const { isMobile, setOpenMobile } = useSidebar();
  return (
    <Sidebar className="z-50">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton asChild>
              <Link to="/">
                <IconArrowLeft />
                <FormattedMessage id="common.goBack" defaultMessage="Go Back" />
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>
            <FormattedMessage
              id="sidebar.superAdmin"
              defaultMessage="Super Admin"
            />
          </SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarMenuButton asChild>
                  <Link
                    onClick={() => {
                      if (isMobile) {
                        setOpenMobile(false);
                      }
                    }}
                    to="/organisations"
                    activeProps={{ "data-active": true }}
                  >
                    <IconBuilding />
                    <FormattedMessage
                      id="pages.organisations"
                      defaultMessage="Organisations"
                    />
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
              <SidebarMenuItem>
                <SidebarMenuButton asChild>
                  <Link
                    onClick={() => {
                      if (isMobile) {
                        setOpenMobile(false);
                      }
                    }}
                    to="/extensions"
                    activeProps={{ "data-active": true }}
                  >
                    <IconPuzzle />
                    <FormattedMessage
                      id="pages.extensions"
                      defaultMessage="Extensions"
                    />
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
              <SidebarMenuItem>
                <SidebarMenuButton asChild>
                  <Link
                    onClick={() => {
                      if (isMobile) {
                        setOpenMobile(false);
                      }
                    }}
                    to="/issuer-definitions"
                    activeProps={{ "data-active": true }}
                  >
                    <IconRubberStamp />
                    <FormattedMessage
                      id="pages.platformConfiguration"
                      defaultMessage="Platform"
                    />
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
    </Sidebar>
  );
}
