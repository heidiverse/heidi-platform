// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconBlocks,
  IconBug,
  IconBuilding,
  IconClipboardList,
  IconExternalLink,
  IconFingerprint,
  IconKey,
  IconListCheck,
  IconRubberStamp,
  IconSettings,
  IconTemplate,
} from "@tabler/icons-react";
import { useSuspenseQuery } from "@tanstack/react-query";
import { linkOptions } from "@tanstack/react-router";
import { useAtomValue } from "jotai";
import { useMemo } from "react";
import { useIntl } from "react-intl";
import { organisationFeaturesOptions } from "@/lib/api/organisation-features/query-options";
import { selectedTenantAtom } from "@/lib/atoms";
import {
  getExtensionNavGroups,
  mergeNavGroups,
  type NavigationGroupConfig,
  type SidebarRoleGate,
} from "@/lib/extensions";
import { useUser } from "@/lib/hooks/use-user";
import {
  getOrganisationFeatureGroups,
  hasOrganisationFeatureAccess,
} from "@/lib/organisation-features";
import { isDeveloper, isManager, isOperator } from "@/lib/utils/user";


const API_DOCS_HREF = import.meta.env.PROD ? "/api-docs" : "/api-docs.html";

const coreNavConfig: readonly NavigationGroupConfig[] = [
  {
    groupKey: "credentialSchemas",
    links: [
      {
        link: linkOptions({ to: "/credential-schemas" }),
        icon: IconTemplate,
        requiredFeature: "credentialSchemas",
      },
      {
        link: linkOptions({ to: "/proof-schemas" }),
        icon: IconClipboardList,
        requiredFeature: "proofSchemas",
      },
      {
        link: linkOptions({ to: "/status-lists" }),
        icon: IconListCheck,
        labelId: "pages.statusLists",
        defaultMessage: "Status Lists",
      },
    ],
  },
  {
    groupKey: "desk",
    requiredRole: "operator",
    labelId: "sidebar.desk",
    defaultMessage: "Desk",
    links: [
      {
        link: linkOptions({ to: "/issuer" }),
        icon: IconRubberStamp,
        labelId: "pages.issuer",
        defaultMessage: "Web Issuer",
      },
    ],
  },
  {
    groupKey: "developer",
    requiredRole: "developer",
    links: [
      {
        link: linkOptions({ to: "/integrations" }),
        icon: IconBlocks,
        requiredFeature: "integrations",
      },
      {
        href: API_DOCS_HREF,
        icon: IconExternalLink,
        requiredFeature: "apiDocs",
      },
      {
        link: linkOptions({ to: "/testing" }),
        icon: IconBug,
        requiredFeature: "testing",
      },
    ],
  },
  {
    groupKey: "administration",
    requiredRole: "manager",
    links: [
      {
        link: linkOptions({ to: "/organisation" }),
        icon: IconBuilding,
        requiredFeature: "organisation",
      },
      {
        link: linkOptions({ to: "/identities" }),
        icon: IconFingerprint,
        labelId: "pages.issuerDefinitions",
        defaultMessage: "Identities",
        requiredFeature: "organisation",
      },
      {
        link: linkOptions({ to: "/keys" }),
        icon: IconKey,
        labelId: "pages.keys",
        defaultMessage: "Keys",
        requiredFeature: "organisation",
      },
      {
        link: linkOptions({ to: "/settings" }),
        icon: IconSettings,
        requiredFeature: "settings",
      },
    ],
  },
];

export function useNavigationLinks() {
  const { $t } = useIntl();
  const user = useUser();
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const tenantId = selectedTenant || user.tenantId;
  const { data: organisationFeatures } = useSuspenseQuery(
    organisationFeaturesOptions(tenantId),
  );

  function hasRole(role?: SidebarRoleGate) {
    if (!role) {
      return true;
    }
    if (role === "operator") {
      return isOperator(user);
    }
    if (role === "developer") {
      return isDeveloper(user);
    }
    return isManager(user);
  }

  return useMemo(() => {
    const navConfig = mergeNavGroups(coreNavConfig, getExtensionNavGroups());

    return navConfig
      .filter((group) => hasRole(group.requiredRole))
      .map((group) => {
        // Built-in groups take their heading from the organisation feature groups;
        // contributed ones carry their own.
        const groupDefinition = getOrganisationFeatureGroups().find(
          (item) => item.key === group.groupKey,
        );
        const title = groupDefinition ?? {
          labelId: group.labelId,
          defaultMessage: group.defaultMessage,
        };

        const navLinks = group.links
          .filter(
            (link) =>
              hasRole(link.requiredRole) &&
              // A link without a feature is not gated by one.
              (!link.requiredFeature ||
                hasOrganisationFeatureAccess({
                  features: organisationFeatures,
                  feature: link.requiredFeature,
                })),
          )
          .flatMap((link) => {
            const feature = link.requiredFeature
              ? getOrganisationFeatureGroups()
                  .flatMap((item) => item.features)
                  .find((item) => item.key === link.requiredFeature)
              : undefined;
            const labelId = link.labelId ?? feature?.labelId;
            const defaultMessage = link.defaultMessage ?? feature?.defaultMessage;

            if (!labelId || !defaultMessage) {
              return [];
            }

            return [{ ...link, label: $t({ id: labelId, defaultMessage }) }];
          });

        return {
          title:
            title.labelId && title.defaultMessage
              ? $t({
                  id: title.labelId,
                  defaultMessage: title.defaultMessage,
                })
              : group.groupKey,
          navLinks,
        };
      })
      .filter((group) => group.navLinks.length > 0);
  }, [$t, organisationFeatures, user]);
}
