// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { createFileRoute, notFound } from "@tanstack/react-router";
import { z } from "zod";
import { IntegrationDetail } from "@/components/common/integration-detail";
import { integrationOptions } from "@/lib/api/integrations/query-options";

export const Route = createFileRoute("/_authenticated/integrations/$integrationId")({
  validateSearch: z.object({
    action: z.enum(["issuance", "presentation"]).default("issuance"),
  }),
  loader: async ({ context, params }) => {
    const integration = await context.queryClient.ensureQueryData(
      integrationOptions({ id: params.integrationId }),
    );
    if (!integration) {
      throw notFound();
    }
    return { crumb: integration.displayName };
  },
  component: RouteComponent,
});

function RouteComponent() {
  const { integrationId } = Route.useParams();
  const { crumb } = Route.useLoaderData();
  const { action } = Route.useSearch();
  return (
    <IntegrationDetail
      integrationId={integrationId}
      heading={crumb}
      action={action}
    />
  );
}
