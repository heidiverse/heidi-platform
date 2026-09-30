// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useSuspenseQueries } from "@tanstack/react-query";
import { createFileRoute, getRouteApi } from "@tanstack/react-router";
import { useAtomValue } from "jotai";
import { PageHeader } from "@/components/common/page-header";
import { IdentityActions } from "@/components/identity/identity-actions";
import { IdentityDetail } from "@/components/identity/identity-detail";
import { issuerDefinitionListOptions } from "@/lib/api/issuer-definitions/query-options";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";
import { selectedTenantAtom } from "@/lib/atoms";
import { UserRole } from "@/lib/auth/identity";
import { useUser } from "@/lib/hooks/use-user";
import { getLocalizedValue } from "@/lib/utils/localized";

export const Route = createFileRoute("/_authenticated/identities/$identityId")({
  loader: ({ context: { intl } }) => ({
    crumb: intl.$t({ id: "pages.issuerDefinition", defaultMessage: "Identity" }),
  }),
  component: RouteComponent,
});

const routeApi = getRouteApi("/_authenticated/identities/$identityId");

function RouteComponent() {
  const { crumb } = routeApi.useLoaderData();
  const { identityId } = Route.useParams();
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
  const identity = identities.find((candidate) => candidate.id === Number(identityId));
  if (!identity || !settings.issuerIds.includes(identity.id)) throw new Error("Identity not found");

  return <>
    <PageHeader heading={<>
      <span className="block text-sm font-medium text-muted-foreground">{crumb}</span>
      <span className="block">
        {getLocalizedValue(identity.displayName, settings.defaultLanguage) || identity.slug}
      </span>
    </>} />
    {identity.tenantId === tenantId && (
      <IdentityActions
        identity={identity}
        languages={settings.translations}
        fallbackLanguage={settings.defaultLanguage}
        onDeleted={() => navigate({ to: "/identities" })}
      />
    )}
    <IdentityDetail identity={identity} tenantId={tenantId} />
  </>;
}
