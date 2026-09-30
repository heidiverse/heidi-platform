// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { FormattedMessage } from "react-intl";
import { PageHeader } from "@/components/common/page-header";
import { IdentityActions } from "@/components/identity/identity-actions";
import { IdentityDetail } from "@/components/identity/identity-detail";
import { PlatformIdentityAccess } from "@/components/identity/platform-identity-access";
import { issuerDefinitionListOptions } from "@/lib/api/issuer-definitions/query-options";
import { DEFAULT_LOCALE } from "@/lib/constants";
import { getLocalizedValue } from "@/lib/utils/localized";

export const Route = createFileRoute("/_organisations/issuer-definitions/$identityId")({
  component: RouteComponent,
});

function RouteComponent() {
  const { identityId } = Route.useParams();
  const navigate = Route.useNavigate();
  const { data: identities } = useSuspenseQuery(issuerDefinitionListOptions());
  const identity = identities.find((candidate) => candidate.id === Number(identityId));
  if (!identity) throw new Error("Platform identity not found");

  return <>
    <PageHeader
      heading={<FormattedMessage
        id="identity.platform.detailHeading"
        values={{
          value: getLocalizedValue(identity.displayName, DEFAULT_LOCALE) || identity.slug,
        }}
      />}
    />
    <IdentityActions
      identity={identity}
      onDeleted={() => navigate({ to: "/issuer-definitions" })}
    />
    <IdentityDetail identity={identity} />
    <PlatformIdentityAccess identity={identity} />
  </>;
}
