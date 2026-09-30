// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconPencil, IconPlus } from "@tabler/icons-react";
import { useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute, getRouteApi, Link } from "@tanstack/react-router";
import { useAtom } from "jotai";
import { FormattedMessage, useIntl } from "react-intl";
import { PageHeader } from "@/components/common/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { organisationListOptions } from "@/lib/api/organisations/query-options";
import { selectedTenantAtom } from "@/lib/atoms";
import { OrganisationDialog } from "@/routes/_organisations/organisations/-components/organisation-dialog";

export const Route = createFileRoute("/_organisations/organisations/")({
  loader: ({ context: { queryClient } }) => {
    queryClient.ensureQueryData(organisationListOptions());
  },
  component: RouteComponent,
});

const routeApi = getRouteApi("/_organisations/organisations");

function RouteComponent() {
  const [, setSelectedTenant] = useAtom(selectedTenantAtom);
  const { data: organisations } = useSuspenseQuery(organisationListOptions());
  const { crumb } = routeApi.useLoaderData();
  const { $t } = useIntl();
  return (
    <>
      <PageHeader heading={crumb}>
        <OrganisationDialog>
          <Button className="w-full @lg:w-auto">
            <IconPlus className="size-4" />
            <FormattedMessage
              id="common.new.withValue"
              defaultMessage="New {value}"
              values={{
                pronoun: "feminine",
                value: $t({
                  id: "common.organisation",
                  defaultMessage: "Organisation",
                }),
              }}
            />
          </Button>
        </OrganisationDialog>
      </PageHeader>
      <div className="mt-3 grid grid-cols-1 gap-3 @lg:grid-cols-2 @3xl:grid-cols-3">
        {organisations.map((org) => (
          <Card
            key={org.tenantId}
            className="flex flex-col items-start justify-between gap-2 p-6"
          >
            <div className="flex flex-col gap-1.5">
              <h2 className="text-2xl leading-none font-semibold">
                {org.displayName}
              </h2>
              <h3 className="text-xs text-muted-foreground">
                {org.tenantId}
              </h3>
              <Badge
                className="self-start"
                variant={org.revoked ? "destructive" : "success"}
              >
                {org.revoked ? (
                  <FormattedMessage
                    id="common.inactive"
                    defaultMessage="Inactive"
                  />
                ) : (
                  <FormattedMessage
                    id="common.active"
                    defaultMessage="Active"
                  />
                )}
              </Badge>
            </div>
            <div className="mt-3 flex w-full items-center justify-between">
              <Button variant="outline" className="gap-2" asChild>
                <Link
                  to="/organisations/$tenantId"
                  params={{ tenantId: org.tenantId }}
                >
                  <IconPencil className="size-4" />
                  <FormattedMessage id="common.edit" defaultMessage="Edit" />
                </Link>
              </Button>
              <Button
                onClick={() => {
                  setSelectedTenant(org.tenantId);
                }}
                asChild
              >
                <Link to="/">
                  <FormattedMessage id="common.open" defaultMessage="Open" />
                </Link>
              </Button>
            </div>
          </Card>
        ))}
      </div>
    </>
  );
}
