// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconPlus, IconSearch } from "@tabler/icons-react";
import { createFileRoute, getRouteApi, redirect } from "@tanstack/react-router";
import { FormattedMessage, useIntl } from "react-intl";
import { z } from "zod";
import { PageHeader } from "@/components/common/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { integrationsListOptions } from "@/lib/api/integrations/query-options";
import { isDeveloper } from "@/lib/utils/user";
import { IntegrationDialog } from "@/routes/_authenticated/integrations/-components/integrations-dialog";
import { IntegrationsList } from "@/routes/_authenticated/integrations/-components/integrations-list";

export const Route = createFileRoute("/_authenticated/integrations/")({
  component: RouteComponent,
  beforeLoad: ({ context: { user } }) => {
    if (!isDeveloper(user)) {
      throw redirect({ to: "/" });
    }
  },
  validateSearch: z.object({ q: z.string().optional() }),
  loader: ({ context: { queryClient } }) => {
    queryClient.ensureQueryData(integrationsListOptions());
  },
});

const routeApi = getRouteApi("/_authenticated/integrations");

function RouteComponent() {
  const { crumb } = routeApi.useLoaderData();
  const { q: search = "" } = Route.useSearch();
  const navigate = Route.useNavigate();
  function setSearch(value: string) {
    navigate({ search: { q: value || undefined }, replace: true });
  }
  const { $t } = useIntl();
  return (
    <>
      <PageHeader heading={crumb}>
        <div className="flex grow flex-wrap items-center gap-2 @lg:grow-0">
          <Input
            icon={IconSearch}
            value={search}
            className="w-full @lg:w-auto"
            onChange={(e) => setSearch(e.target.value)}
            placeholder={`${$t({ id: "common.search", defaultMessage: "Search" })}...`}
          />
          <IntegrationDialog>
            <Button className="w-full @lg:w-auto">
              <IconPlus />
              <FormattedMessage
                id="common.new.withValue"
                defaultMessage="New {value}"
                values={{
                  pronoun: "feminine",
                  value: $t({
                    id: "common.integration",
                    defaultMessage: "Integration",
                  }),
                }}
              />
            </Button>
          </IntegrationDialog>
        </div>
      </PageHeader>
      <IntegrationsList integrationsSearch={search} />
    </>
  );
}
