// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconChevronRight, IconPlus } from "@tabler/icons-react";
import { useSuspenseQueries } from "@tanstack/react-query";
import { createFileRoute, getRouteApi, Link } from "@tanstack/react-router";
import { useAtomValue } from "jotai";
import { FormattedMessage } from "react-intl";
import { PageHeader } from "@/components/common/page-header";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { issuerDefinitionListOptions } from "@/lib/api/issuer-definitions/query-options";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";
import { selectedTenantAtom } from "@/lib/atoms";
import { UserRole } from "@/lib/auth/identity";
import { useUser } from "@/lib/hooks/use-user";
import { getLocalizedValue } from "@/lib/utils/localized";
import { IdentitySetupDialog } from "./-components/identity-setup-dialog";

export const Route = createFileRoute("/_authenticated/identities/")({
  component: RouteComponent,
});

const routeApi = getRouteApi("/_authenticated/identities");

function RouteComponent() {
  const { crumb } = routeApi.useLoaderData();
  const navigate = Route.useNavigate();
  const user = useUser();
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const tenantId = user.roles.includes(UserRole.SuperAdmin)
    ? selectedTenant || user.tenantId
    : user.tenantId;
  const [{ data: settings }, { data: identities }] = useSuspenseQueries({
    queries: [
      settingsForOrganisationOptions(tenantId),
      issuerDefinitionListOptions({ tenantId }),
    ],
  });
  const assigned = identities.filter((identity) => settings.issuerIds.includes(identity.id));

  return <>
    <PageHeader heading={crumb}>
      <IdentitySetupDialog tenantId={tenantId} fallbackLanguage={settings.defaultLanguage} onCreated={(identityId) => navigate({ to: "/identities/$identityId", params: { identityId: String(identityId) } })}>
        <Button><IconPlus /> <FormattedMessage id="identity.setup.create" /></Button>
      </IdentitySetupDialog>
    </PageHeader>
    <div className="mt-4 grid gap-3">
      {assigned.map((identity) => <Link key={identity.id} to="/identities/$identityId" params={{ identityId: String(identity.id) }}>
        <Card className="grid gap-3 p-4 transition-colors hover:bg-muted/50 sm:grid-cols-[1fr_auto]">
          <div>
            <p className="font-semibold">
              {getLocalizedValue(identity.displayName, settings.defaultLanguage) || identity.slug}
            </p>
            <p className="text-sm text-muted-foreground">
              <FormattedMessage
                id={identity.tenantId === tenantId ? "identity.list.organisationOwned" : "identity.list.inherited"}
              />{" · "}
              <FormattedMessage
                id="identity.list.trustSystems"
                values={{ count: identity.trustSystems?.length ?? 0 }}
              />
            </p>
          </div>
          <IconChevronRight className="self-center text-muted-foreground" />
        </Card>
      </Link>)}
      {assigned.length === 0 && <Card className="border-dashed p-6 text-sm text-muted-foreground"><FormattedMessage id="identity.list.empty" /></Card>}
    </div>
  </>;
}
