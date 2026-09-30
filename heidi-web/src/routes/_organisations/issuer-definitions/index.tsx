// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconChevronRight, IconPlus } from "@tabler/icons-react";
import { useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute, getRouteApi, Link } from "@tanstack/react-router";
import { FormattedMessage } from "react-intl";
import { PageHeader } from "@/components/common/page-header";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { issuerDefinitionListOptions } from "@/lib/api/issuer-definitions/query-options";
import { DEFAULT_LOCALE } from "@/lib/constants";
import { getLocalizedValue } from "@/lib/utils/localized";
import { IdentitySetupDialog } from "@/routes/_authenticated/identities/-components/identity-setup-dialog";
import { KeyManagementCard } from "@/routes/_authenticated/organisation/-components/key-management-card";
import { SigningProviderCard } from "@/routes/_authenticated/organisation/-components/signing-provider-card";

export const Route = createFileRoute("/_organisations/issuer-definitions/")({
  component: RouteComponent,
});

const routeApi = getRouteApi("/_organisations/issuer-definitions");

function RouteComponent() {
  const { crumb } = routeApi.useLoaderData();
  const navigate = Route.useNavigate();
  const { data: identities } = useSuspenseQuery(issuerDefinitionListOptions());

  return <>
    <PageHeader heading={<FormattedMessage id="identity.platform.heading" values={{ crumb }} />} />
    <p className="mt-3 text-sm text-muted-foreground">
      <FormattedMessage id="identity.platform.description" />
    </p>
    <Tabs defaultValue="identities" className="mt-4">
      <TabsList>
        <TabsTrigger value="identities"><FormattedMessage id="identity.platform.identities" /></TabsTrigger>
        <TabsTrigger value="keys"><FormattedMessage id="identity.platform.keys" /></TabsTrigger>
        <TabsTrigger value="providers"><FormattedMessage id="identity.platform.providers" /></TabsTrigger>
      </TabsList>
      <TabsContent value="identities">
        <div className="mb-4 flex justify-end">
          <IdentitySetupDialog onCreated={(identityId) => navigate({
            to: "/issuer-definitions/$identityId",
            params: { identityId: String(identityId) },
          })}>
            <Button><IconPlus /> <FormattedMessage id="identity.platform.add" /></Button>
          </IdentitySetupDialog>
        </div>
        <div className="grid gap-3">
          {identities.map((identity) => <Link key={identity.id}
            to="/issuer-definitions/$identityId" params={{ identityId: String(identity.id) }}>
            <Card className="grid gap-3 p-4 transition-colors hover:bg-muted/50 sm:grid-cols-[1fr_auto]">
              <div>
                <p className="font-semibold">
                  {getLocalizedValue(identity.displayName, DEFAULT_LOCALE) || identity.slug}
                </p>
                <p className="text-sm text-muted-foreground"><FormattedMessage id="identity.platform.owned" /></p>
              </div>
              <IconChevronRight className="self-center text-muted-foreground" />
            </Card>
          </Link>)}
          {identities.length === 0 && <Card className="border-dashed p-6 text-sm text-muted-foreground"><FormattedMessage id="identity.platform.empty" /></Card>}
        </div>
      </TabsContent>
      <TabsContent value="keys">
        <KeyManagementCard identities={identities} />
      </TabsContent>
      <TabsContent value="providers">
        <SigningProviderCard />
      </TabsContent>
    </Tabs>
  </>;
}
