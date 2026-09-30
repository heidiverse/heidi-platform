// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { QueryClient } from "@tanstack/react-query";
import { redirect } from "@tanstack/react-router";
import { organisationFeaturesOptions } from "@/lib/api/organisation-features/query-options";
import { jotaiStore, selectedTenantAtom } from "@/lib/atoms";
import type {
  UserRole as UserRoleType,
  UserTokenData,
} from "@/lib/auth/identity";
import { getExtensionFeatureGroups } from "@/lib/extensions";

export type OrganisationFeatures = {
  credentialSchemas: boolean;
  proofSchemas: boolean;
  integrations: boolean;
  apiDocs: boolean;
  user: boolean;
  organisation: boolean;
  settings: boolean;
} & Record<string, boolean>;

export type OrganisationFeatureKey = string;

export type FeatureDefinition = {
  key: OrganisationFeatureKey;
  labelId: string;
  defaultMessage: string;
  readOnly?: boolean;
  /** Value used when an assembled extension predates its persisted flag. */
  defaultValue?: boolean;
};

export type FeatureGroupDefinition = {
  key: string;
  labelId: string;
  defaultMessage: string;
  features: readonly FeatureDefinition[];
};

export const organisationFeatureGroups: readonly FeatureGroupDefinition[] = [
  {
    key: "credentialSchemas",
    labelId: "pages.settings.features.groups.credentialSchemas",
    defaultMessage: "Credential Schemas",
    features: [
      {
        key: "credentialSchemas",
        labelId: "pages.credentialSchemas",
        defaultMessage: "Credential Schemas",
      },
      {
        key: "proofSchemas",
        labelId: "pages.proofSchemas",
        defaultMessage: "Proof Schemas",
      },
    ],
  },
  {
    key: "developer",
    labelId: "pages.settings.features.groups.developer",
    defaultMessage: "Developer",
    features: [
      {
        key: "integrations",
        labelId: "pages.integrations",
        defaultMessage: "Integrations",
      },
      {
        key: "apiDocs",
        labelId: "pages.settings.features.apiDocs",
        defaultMessage: "Integrator API",
      },
      {
        key: "testing",
        labelId: "pages.settings.features.testing",
        defaultMessage: "Testing",
        defaultValue: true,
      },
    ],
  },
  {
    key: "administration",
    labelId: "pages.settings.features.groups.administration",
    defaultMessage: "Administration",
    features: [
      {
        key: "user",
        labelId: "pages.settings.features.user",
        defaultMessage: "User",
      },
      {
        key: "organisation",
        labelId: "common.organisation",
        defaultMessage: "Organisation",
        readOnly: true,
      },
      {
        key: "settings",
        labelId: "pages.settings",
        defaultMessage: "Settings",
        readOnly: true,
      },
    ],
  },
];

export function getOrganisationFeatureGroups(): readonly FeatureGroupDefinition[] {
  const groups = organisationFeatureGroups.map((group) => ({
    ...group,
    features: [...group.features],
  }));

  for (const extensionGroup of getExtensionFeatureGroups()) {
    const group = groups.find((item) => item.key === extensionGroup.key);
    if (group) {
      group.features = [...group.features, ...extensionGroup.features];
    } else {
      groups.push({ ...extensionGroup, features: [...extensionGroup.features] });
    }
  }

  return groups;
}

export function normalizeOrganisationFeatures(
  features: OrganisationFeatures,
): OrganisationFeatures {
  const extensionDefaults = Object.fromEntries(
    getOrganisationFeatureGroups()
      .flatMap((group) => group.features)
      .filter(
        (feature) =>
          feature.defaultValue !== undefined && features[feature.key] === undefined,
      )
      .map((feature) => [feature.key, feature.defaultValue]),
  );

  return {
    ...extensionDefaults,
    ...features,
    organisation: true,
    settings: true,
  };
}

export function hasOrganisationFeatureAccess({
  features,
  feature,
}: {
  features: OrganisationFeatures;
  feature?: OrganisationFeatureKey;
}) {
  if (!feature) {
    return true;
  }
  return normalizeOrganisationFeatures(features)[feature];
}

export async function requireRoleAndFeatureAccess({
  queryClient,
  user,
  allowedRoles,
  feature,
}: {
  queryClient: QueryClient;
  user: UserTokenData;
  allowedRoles?: UserRoleType[];
  feature?: OrganisationFeatureKey;
}) {
  const tenantId = jotaiStore.get(selectedTenantAtom) || user.tenantId;
  const features = await queryClient.ensureQueryData(
    organisationFeaturesOptions(tenantId),
  );
  const hasRole =
    !allowedRoles || allowedRoles.some((role) => user.roles.includes(role));

  if (
    !hasRole ||
    !hasOrganisationFeatureAccess({
      features,
      feature,
    })
  ) {
    throw redirect({ to: "/" });
  }
  return features;
}
