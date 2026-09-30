// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconChevronDown } from "@tabler/icons-react";
import { Link } from "@tanstack/react-router";
import { Suspense } from "react";
import { ErrorBoundary } from "react-error-boundary";
import { useLocalStorage } from "usehooks-ts";
import { OrganisationFallback, SidebarHeaderOrgs } from "@/components/layout/sidebar-header-orgs";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";
import { useNavigationLinks } from "@/lib/hooks/use-navigation-links";

function SidebarBackground() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 279 338"
      className="absolute inset-x-0 bottom-0"
    >
      <path
        fill="url(#a)"
        fillOpacity={0.5}
        d="m279 231.236-88.536-73.284L279 106.616v124.62Z"
      />
      <path
        fill="url(#b)"
        fillOpacity={0.5}
        d="m0 .224 190.464 157.728L0 268.436V.224Z"
      />
      <path
        fill="url(#c)"
        fillOpacity={0.5}
        d="M190.464 157.952 0 268.436V338h279V231.236l-88.536-73.284Z"
      />
      <defs>
        <linearGradient
          id="a"
          x1={249.304}
          x2={302.095}
          y1={231.236}
          y2={120.945}
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="#00233B" />
          <stop offset={1} stopColor="#01607A" />
        </linearGradient>
        <linearGradient
          id="b"
          x1={95}
          x2={129.747}
          y1={210}
          y2={23.878}
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="#01607A" />
          <stop offset={1} stopColor="#014F68" />
        </linearGradient>
        <linearGradient
          id="c"
          x1={172}
          x2={218}
          y1={338}
          y2={144.5}
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="#01607A" />
          <stop offset={1} stopColor="#319DB2" />
        </linearGradient>
      </defs>
    </svg>
  );
}

export function AppSidebar() {
  const navLinkGroups = useNavigationLinks();
  const { setOpenMobile, isMobile } = useSidebar();
  const [collapsedSections, setCollapsedSections] = useLocalStorage(
    "sidebar:collapsedSections",
    [] as string[],
  );

  function handleNavClick() {
    if (isMobile) {
      setOpenMobile(false);
    }
  }

  return (
    <Sidebar className="z-50">
      <SidebarBackground />
      <ErrorBoundary fallback={<OrganisationFallback isError />}>
        <Suspense fallback={<OrganisationFallback />}>
          <SidebarHeaderOrgs />
        </Suspense>
      </ErrorBoundary>
      <SidebarContent>
        {navLinkGroups.map((group) => (
          <Collapsible
            key={group.title}
            open={!collapsedSections.includes(group.title)}
            onOpenChange={(open) => {
              if (open) {
                setCollapsedSections((prev) =>
                  prev.filter((title) => title !== group.title),
                );
              } else {
                setCollapsedSections((prev) => [...prev, group.title]);
              }
            }}
            className="group/collapsible"
          >
            <SidebarGroup>
              <SidebarGroupLabel asChild>
                <CollapsibleTrigger className="gap-2">
                  <IconChevronDown className="transition-transform group-data-[state=open]/collapsible:rotate-180" />
                  {group.title}
                </CollapsibleTrigger>
              </SidebarGroupLabel>
              <CollapsibleContent>
                <SidebarGroupContent>
                  <SidebarMenu>
                    {group.navLinks.map((link) => (
                      <SidebarMenuItem
                        key={`${link.href ?? link.link?.to ?? ""}${link.label}`}
                      >
                        <SidebarMenuButton asChild>
                          {"href" in link ? (
                            <a
                              href={link.href}
                              target="_blank"
                              rel="noreferrer"
                              onClick={handleNavClick}
                            >
                              <link.icon />
                              <span>{link.label}</span>
                            </a>
                          ) : (
                            <Link
                              {...link.link}
                              activeProps={{ "data-active": true }}
                              onClick={handleNavClick}
                            >
                              <link.icon />
                              <span>{link.label}</span>
                            </Link>
                          )}
                        </SidebarMenuButton>
                      </SidebarMenuItem>
                    ))}
                  </SidebarMenu>
                </SidebarGroupContent>
              </CollapsibleContent>
            </SidebarGroup>
          </Collapsible>
        ))}
      </SidebarContent>
    </Sidebar>
  );
}
