// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import ReactDOM from "react-dom/client";
import "@/styles/main.css";
import {
  QueryCache,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import { createRouter, RouterProvider } from "@tanstack/react-router";
import { Provider as JotaiProvider } from "jotai";
import { StrictMode, Suspense } from "react";
import { ErrorBoundary } from "react-error-boundary";
import { useIntl } from "react-intl";
import { toast } from "sonner";
import { AppBootstrapError } from "@/components/common/app-bootstrap-error";
import { DefaultErrorComponent } from "@/components/common/error";
import { Loading } from "@/components/common/loading";
import { NotFoundPage } from "@/components/common/not-found";
import { Toaster } from "@/components/ui/sonner";
import { jotaiStore } from "@/lib/atoms";
import { useUser } from "@/lib/hooks/use-user";
import { setIntl } from "@/lib/intl-service";
import { AsyncIntlProvider } from "@/lib/providers/async-intl-provider";
import { loadRuntimeConfig } from "@/lib/runtime-config";
import { routeTree } from "@/routeTree.gen";

// Builds carrying an extension register it here. The OSS fallback is an empty
// virtual module, while hosted/product builds resolve it to their package entry.
import "virtual:heidi-web-extension";

const HTTP_CODES_TO_SKIP = [401, 403, 404];

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 10 * 1000,
      retry: (failureCount, error) => {
        if (import.meta.env.MODE === "dev-local") {
          return false;
        }
        if (
          typeof error === "object" &&
          error !== null &&
          "code" in error &&
          typeof error.code === "number" &&
          HTTP_CODES_TO_SKIP.includes(error.code)
        ) {
          return false;
        }
        if (failureCount < 3) {
          return true;
        }
        return false;
      },
    },
  },
  queryCache: new QueryCache({
    onError(error, query) {
      if (
        query.meta?.errorMessage &&
        typeof query.meta.errorMessage === "string"
      ) {
        toast.error(query.meta.errorMessage, { description: error.message });
      }
    },
  }),
});

const router = createRouter({
  scrollRestoration: true,
  routeTree,
  context: {
    queryClient,
    user: undefined!,
    intl: undefined!,
  },
  // this preloads links on hover, is recommended by docs, to be discussed
  defaultPreload: "intent",
  // Since we're using React Query, we don't want loader calls to ever be stale
  // This will ensure that the loader is always called when the route is preloaded or visited
  defaultPreloadStaleTime: 0,
});

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}

function InnerApp() {
  const user = useUser();
  const intl = useIntl();
  setIntl(intl);
  return (
    <RouterProvider
      context={{ user, intl }}
      defaultErrorComponent={DefaultErrorComponent}
      defaultPendingComponent={Loading}
      defaultNotFoundComponent={NotFoundPage}
      router={router}
    />
  );
}

await loadRuntimeConfig();

const rootElement = document.getElementById("heidi-root")!;

if (!rootElement.innerHTML) {
  const root = ReactDOM.createRoot(rootElement);
  root.render(
    <StrictMode>
      <JotaiProvider store={jotaiStore}>
        <QueryClientProvider client={queryClient}>
          <ErrorBoundary FallbackComponent={AppBootstrapError}>
            <Suspense fallback={<Loading />}>
              <AsyncIntlProvider>
                <InnerApp />
                <Toaster />
              </AsyncIntlProvider>
            </Suspense>
          </ErrorBoundary>
        </QueryClientProvider>
      </JotaiProvider>
    </StrictMode>,
  );
}
