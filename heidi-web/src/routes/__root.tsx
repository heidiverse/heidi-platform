// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { TanStackDevtools } from "@tanstack/react-devtools";
import { formDevtoolsPlugin } from "@tanstack/react-form-devtools";
import type { QueryClient } from "@tanstack/react-query";
import { ReactQueryDevtoolsPanel } from "@tanstack/react-query-devtools";
import {
  createRootRouteWithContext,
  Outlet,
  useRouter,
} from "@tanstack/react-router";
import { TanStackRouterDevtoolsPanel } from "@tanstack/react-router-devtools";
import { useAtomValue } from "jotai";
import { useEffect, useRef } from "react";
import type { IntlShape } from "react-intl";
import { localeAtom } from "@/lib/atoms";
import type { UserTokenData } from "@/lib/auth/identity";
import { runtimeConfig } from "@/lib/runtime-config";
import { cn } from "@/lib/utils";

interface RouterContext {
  queryClient: QueryClient;
  user: UserTokenData;
  intl: IntlShape;
}

export const Route = createRootRouteWithContext<RouterContext>()({
  component: () => {
    // needed so that translated strings from router are updated
    const isFirstRender = useRef(true);
    const locale = useAtomValue(localeAtom);
    const router = useRouter();
    useEffect(() => {
      if (isFirstRender.current) {
        isFirstRender.current = false;
        return;
      }
      document.documentElement.lang = locale;
      router.invalidate();
    }, [locale]);

    return (
      <>
        <Outlet />
        <DevBadge />
        <TanStackDevtools
          plugins={[
            formDevtoolsPlugin(),
            {
              name: "TanStack Query",
              render: <ReactQueryDevtoolsPanel />,
              defaultOpen: false,
            },
            {
              name: "TanStack Router",
              render: <TanStackRouterDevtoolsPanel />,
              defaultOpen: false,
            },
          ]}
        />
      </>
    );
  },
});

function DevBadge() {
  if (import.meta.env.MODE.includes("prod")) return null;
  return (
    <div
      className={cn(
        "pointer-events-none fixed inset-0 z-[9999] flex flex-col items-center justify-start border border-rose-500 select-none",
      )}
    >
      <div
        className={cn(
          "relative -mt-px flex h-6 items-center gap-1 rounded-b-lg border border-t-0 border-rose-500 bg-rose-50 px-2 text-sm font-semibold text-rose-500 shadow-lg shadow-rose-700/15 outline-2 outline-offset-2 outline-transparent transition focus-visible:outline-rose-300 organisations:border-rose-500 organisations:bg-rose-800 organisations:text-rose-200",
        )}
      >
        <RoundedCornerInvert className="left-0 -translate-x-full -rotate-90" />
        DEV
        <span className="text-xs font-normal">
          {runtimeConfig.appVersion}
        </span>
        <RoundedCornerInvert className="right-0 translate-x-full -scale-x-100 rotate-90" />
      </div>
    </div>
  );
}

function RoundedCornerInvert({ className }: { className: string }) {
  return (
    <svg
      width="8"
      height="8"
      viewBox="0 0 8 8"
      fill="none"
      className={cn("absolute top-0", className)}
      xmlns="http://www.w3.org/2000/svg"
    >
      <path
        d="M8 8H0C4.41828 8 8 4.41828 8 0V8Z"
        className="fill-rose-50 organisations:fill-rose-800"
      />
      <path
        d="M0 8V7C3.86599 7 7 3.86599 7 0H8C8 4.41828 4.41828 8 0 8Z"
        className="fill-rose-500"
      />
    </svg>
  );
}
