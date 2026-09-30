// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconBlocks,
  IconBug,
  IconBuilding,
  IconClipboardList,
  IconExternalLink,
  IconRubberStamp,
  IconSettings,
  IconTemplate,
} from "@tabler/icons-react";
import { useSuspenseQuery } from "@tanstack/react-query";
import {
  createFileRoute,
  Link,
  linkOptions,
} from "@tanstack/react-router";
import { useAtomValue } from "jotai";
import { type ReactNode, useMemo } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { PageHeader } from "@/components/common/page-header";
import { Card } from "@/components/ui/card";
import { organisationFeaturesOptions } from "@/lib/api/organisation-features/query-options";
import { selectedTenantAtom } from "@/lib/atoms";
import { UserRole, type UserTokenData } from "@/lib/auth/identity";
import {
  type DashboardSegment,
  getExtensionDashboardSegments,
} from "@/lib/extensions";
import { useUser } from "@/lib/hooks/use-user";
import {
  hasOrganisationFeatureAccess,
  type OrganisationFeatures,
} from "@/lib/organisation-features";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/_authenticated/")({
  component: RouteComponent,
});

function RouteComponent() {
  const user = useUser();
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const tenantId = selectedTenant || user.tenantId;
  const { data: organisationFeatures } = useSuspenseQuery(
    organisationFeaturesOptions(tenantId),
  );
  const { $t } = useIntl();

  return (
    <div>
      <PageHeader
        heading={`${$t({ id: "common.welcome", defaultMessage: "Welcome" })} ${user.displayName}`}
      />
      <Card>
        <h2 className="text-xl leading-none font-semibold">
          <FormattedMessage id="common.overview" defaultMessage="Overview" />
        </h2>
        <div className="mt-4 flex flex-col gap-4">
          {useBlocks()
            .filter((block) =>
              block.segments.some((segment) =>
                hasAccess(segment, user, organisationFeatures),
              ),
            )
            .map((block) => (
              <Block
                key={block.key}
                block={block}
                user={user}
                organisationFeatures={organisationFeatures}
              />
            ))}
        </div>
      </Card>
    </div>
  );
}

function Block({
  block,
  user,
  organisationFeatures,
}: {
  block: (typeof blocks)[number];
  user: UserTokenData;
  organisationFeatures: OrganisationFeatures;
}) {
  return (
    <div className="group">
      <h3 className="text-xs leading-none tracking-wider text-muted-foreground uppercase">
        {block.title}
      </h3>
      <div className="flex flex-col">
        {block.segments
          .filter((segment) => hasAccess(segment, user, organisationFeatures))
          .map((segment, i) => (
            <div
              // biome-ignore lint/suspicious/noArrayIndexKey: array is static, so index is not a concern
              key={i}
              className="border-b py-4 last:border-none last:pb-0"
            >
              {"href" in segment ? (
                <a
                  className="flex items-center gap-3 rounded-xl outline-2 outline-offset-2 outline-transparent transition-colors focus-visible:outline-ring"
                  href={segment.href}
                  target="_blank"
                  rel="noreferrer"
                >
                  <div
                    className={cn(
                      block.color,
                      "rounded-xl border border-current/10 bg-current/5 p-3",
                    )}
                  >
                    <segment.icon />
                  </div>
                  <div>
                    <h4 className="font-semibold">{segment.title}</h4>
                    <p className="text-sm text-muted-foreground">
                      {segment.description}
                    </p>
                  </div>
                </a>
              ) : (
                <Link
                  className="flex items-center gap-3 rounded-xl outline-2 outline-offset-2 outline-transparent transition-colors focus-visible:outline-ring"
                  to={segment.link.to}
                >
                  <div
                    className={cn(
                      block.color,
                      "rounded-xl border border-current/10 bg-current/5 p-3",
                    )}
                  >
                    <segment.icon />
                  </div>
                  <div>
                    <h4 className="font-semibold">{segment.title}</h4>
                    <p className="text-sm text-muted-foreground">
                      {segment.description}
                    </p>
                  </div>
                </Link>
              )}
            </div>
          ))}
      </div>
    </div>
  );
}

function hasAccess(
  segment: Segment,
  user: UserTokenData,
  organisationFeatures: OrganisationFeatures,
) {
  return (
    (!segment.allowedRoles ||
      segment.allowedRoles.some((role) => user.roles.includes(role))) &&
    hasOrganisationFeatureAccess({
      features: organisationFeatures,
      feature: segment.requiredFeature,
    })
  );
}

/** The built-in blocks with any extension entries appended. */
function useBlocks(): Block[] {
  return useMemo(
    () =>
      blocks.map((block) => {
        const contributed = getExtensionDashboardSegments(block.key);
        return contributed.length > 0
          ? { ...block, segments: [...block.segments, ...contributed] }
          : block;
      }),
    [],
  );
}

type Block = {
  /** Stable identity, so an extension can name the block it adds to. */
  key: string;
  color: string;
  title: ReactNode;
  segments: Segment[];
};

type Segment = DashboardSegment;

const blocks: Block[] = [
  {
    key: "credentialSchemas",
    color: "text-glacier",
    title: (
      <FormattedMessage
        id="pages.settings.features.groups.credentialSchemas"
        defaultMessage="Credential Schemas"
      />
    ),
    segments: [
      {
        title: (
          <FormattedMessage
            id="pages.credentialSchemas"
            defaultMessage="Credential Schemas"
          />
        ),
        description: (
          <FormattedMessage
            id="pages.credentialSchemas.description"
            defaultMessage="Define schemas for credential issuance"
          />
        ),
        icon: IconTemplate,
        link: linkOptions({ to: "/credential-schemas" }),
        requiredFeature: "credentialSchemas",
      },
      {
        title: (
          <FormattedMessage
            id="pages.proofSchemas"
            defaultMessage="Proof Schemas"
          />
        ),
        description: (
          <FormattedMessage
            id="pages.proofSchemas.description"
            defaultMessage="Create schemas for presentation"
          />
        ),
        icon: IconClipboardList,
        link: linkOptions({ to: "/proof-schemas" }),
        requiredFeature: "proofSchemas",
      },
    ],
  },
  {
    key: "desk",
    color: "text-sky-600",
    title: <FormattedMessage id="sidebar.desk" defaultMessage="Desk" />,
    segments: [
      {
        allowedRoles: [UserRole.SuperAdmin, UserRole.Admin, UserRole.Operator],
        title: (
          <FormattedMessage id="pages.issuer" defaultMessage="Web Issuer" />
        ),
        description: (
          <FormattedMessage
            id="pages.issuer.description"
            defaultMessage="Issue credentials in a web interface"
          />
        ),
        icon: IconRubberStamp,
        link: linkOptions({ to: "/issuer" }),
      },
    ],
  },
  {
    key: "developer",
    color: "text-green",
    title: (
      <FormattedMessage
        id="pages.settings.features.groups.developer"
        defaultMessage="Developer"
      />
    ),
    segments: [
      {
        title: (
          <FormattedMessage
            id="pages.integrations"
            defaultMessage="Integrations"
          />
        ),
        description: (
          <FormattedMessage
            id="pages.integrations.description"
            defaultMessage="Create integrations for API calls"
          />
        ),
        icon: IconBlocks,
        link: linkOptions({ to: "/integrations" }),
        allowedRoles: [UserRole.SuperAdmin, UserRole.Admin, UserRole.Developer],
        requiredFeature: "integrations",
      },
      {
        title: (
          <FormattedMessage
            id="pages.apiDocs"
            defaultMessage="Integrator API"
          />
        ),
        description: (
          <FormattedMessage
            id="pages.overview.apiDocs.description"
            defaultMessage="Browse backend and browser integration endpoints"
          />
        ),
        icon: IconExternalLink,
        href: import.meta.env.PROD ? "/api-docs" : "/api-docs.html",
        allowedRoles: [UserRole.SuperAdmin, UserRole.Admin, UserRole.Developer],
        requiredFeature: "apiDocs",
      },
      {
        title: (
          <FormattedMessage id="pages.testing" defaultMessage="Testing" />
        ),
        description: (
          <FormattedMessage
            id="pages.overview.testing.description"
            defaultMessage="Test flows and integrations"
          />
        ),
        icon: IconBug,
        link: linkOptions({ to: "/testing" }),
        allowedRoles: [UserRole.SuperAdmin, UserRole.Admin, UserRole.Developer],
        requiredFeature: "testing",
      },
    ],
  },
  {
    key: "administration",
    color: "text-violet",
    title: (
      <FormattedMessage
        id="pages.settings.features.groups.administration"
        defaultMessage="Administration"
      />
    ),
    segments: [
      {
        allowedRoles: [UserRole.SuperAdmin, UserRole.Admin, UserRole.Manager],
        title: (
          <FormattedMessage
            id="common.organisation"
            defaultMessage="Organisation"
          />
        ),
        description: (
          <FormattedMessage
            id="pages.organisation.description"
            defaultMessage="Manage organisation profile, identities, and trust registries"
          />
        ),
        icon: IconBuilding,
        link: linkOptions({ to: "/organisation" }),
        requiredFeature: "organisation",
      },
      {
        allowedRoles: [UserRole.SuperAdmin, UserRole.Admin, UserRole.Manager],
        title: (
          <FormattedMessage id="pages.settings" defaultMessage="Settings" />
        ),
        description: (
          <FormattedMessage
            id="pages.settings.description"
            defaultMessage="Manage organisation settings"
          />
        ),
        icon: IconSettings,
        link: linkOptions({ to: "/settings" }),
        requiredFeature: "settings",
      },
    ],
  },
  {
    // Extension point for product-specific dashboard entries.
    key: "extensions",
    color: "text-sky-600",
    title: (
      <FormattedMessage
        id="pages.settings.features.groups.extensions"
        defaultMessage="Extensions"
      />
    ),
    segments: [],
  },
];
