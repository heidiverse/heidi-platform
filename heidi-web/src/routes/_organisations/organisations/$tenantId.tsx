// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconPencil } from "@tabler/icons-react";
import { useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute, notFound } from "@tanstack/react-router";
import { FormattedMessage } from "react-intl";
import { ExtensionSlot } from "@/components/common/extension-slot";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { organisationOptions } from "@/lib/api/organisations/query-options";
import { OrganisationDialog } from "@/routes/_organisations/organisations/-components/organisation-dialog";

export const Route = createFileRoute(
  "/_organisations/organisations/$tenantId",
)({
  loader: async ({ params, context }) => {
    try {
      const { displayName } = await context.queryClient.ensureQueryData(
        organisationOptions(params.tenantId),
      );
      return {
        crumb: displayName,
      };
    } catch (_error) {
      throw notFound();
    }
  },
  component: RouteComponent,
});

function RouteComponent() {
  const { tenantId } = Route.useParams();

  const { data: organisation } = useSuspenseQuery(
    organisationOptions(tenantId),
  );

  return (
    <>
      <Card className="p-6">
        <div className="flex justify-between">
          <div>
            <h1 className="text-3xl leading-none font-semibold">
              {organisation.displayName}
            </h1>
            <p className="mt-2 text-xl">{organisation.tenantId}</p>
          </div>
          <OrganisationDialog
            defaultValues={{
              tenantId: organisation.tenantId,
              displayName: organisation.displayName,
              status: organisation.revoked ? "inactive" : "active",
            }}
          >
            <Button size="icon" variant="secondary">
              <IconPencil />
            </Button>
          </OrganisationDialog>
        </div>
        <Badge
          variant={organisation.revoked ? "destructive" : "success"}
          className="mt-3"
        >
          {organisation.revoked ? (
            <FormattedMessage id="common.inactive" defaultMessage="Inactive" />
          ) : (
            <FormattedMessage id="common.active" defaultMessage="Active" />
          )}
        </Badge>
      </Card>
      <ExtensionSlot slot="organisation-detail" tenantId={tenantId} />
    </>
  );
}
