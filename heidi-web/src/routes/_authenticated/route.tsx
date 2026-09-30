// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { config } from "@heidiverse/heidi-web-components/config";
import { createFileRoute, Outlet } from "@tanstack/react-router";
import { Layout } from "@/components/layout/layout";
import { runtimeConfig } from "@/lib/runtime-config";

export const Route = createFileRoute("/_authenticated")({
  beforeLoad: () => {
    config.init({
      baseUrl: runtimeConfig.heidiApiBaseUrl,
    });
  },
  component: RouteComponent,
});

function RouteComponent() {
  return (
    <Layout>
      <Outlet />
    </Layout>
  );
}
