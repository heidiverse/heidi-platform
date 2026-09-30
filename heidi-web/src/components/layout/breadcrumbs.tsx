// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconChevronRight } from "@tabler/icons-react";
import { isMatch, Link, useMatches } from "@tanstack/react-router";
import { cn } from "@/lib/utils";

export function Breadcrumbs() {
  const matches = useMatches();

  const isOrganisation = matches.some((match) =>
    match.id.includes("_organisations"),
  );

  if (matches.some((match) => match.status === "pending")) return <div />;
  const matchesWithCrumbsAndHome = matches.filter(
    (match) =>
      (!isOrganisation &&
        match.routeId !== "__root__" &&
        match.pathname === "/" &&
        match.routeId !== "/_authenticated/") ||
      isMatch(match, "loaderData.crumb"),
  );

  const lastMatch =
    matchesWithCrumbsAndHome[matchesWithCrumbsAndHome.length - 1];
  const pageTitle = `${import.meta.env.VITE_HEIDI_WEB_APP_NAME} | ${
    lastMatch?.loaderData && "crumb" in lastMatch.loaderData
      ? lastMatch.loaderData.crumb
      : "Dashboard"
  }`;

  return (
    <>
      <title>{pageTitle}</title>
      <nav className="min-w-0">
        <ul className="flex items-center gap-2 text-sm">
          {matchesWithCrumbsAndHome.map((match, i) => (
            <li
              key={match.id}
              className={cn(
                "flex min-w-0 items-center gap-2",
                i === 0 && "min-w-min",
              )}
            >
              <Link
                activeOptions={{ exact: true, includeSearch: false }}
                inactiveProps={{ className: "text-foreground/50" }}
                className="-mx-2 -my-1 truncate rounded-full px-2 py-1 outline-2 outline-offset-2 outline-transparent transition-colors focus-visible:outline-ring"
                to={match.pathname}
              >
                {match.loaderData && "crumb" in match.loaderData
                  ? match.loaderData.crumb
                  : "Dashboard"}
              </Link>
              {i < matchesWithCrumbsAndHome.length - 1 && (
                <IconChevronRight
                  data-active={i === matchesWithCrumbsAndHome.length - 1}
                  className="size-4 shrink-0 opacity-50 data-[active=true]:opacity-100"
                />
              )}
            </li>
          ))}
        </ul>
      </nav>
    </>
  );
}
