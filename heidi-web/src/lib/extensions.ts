// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { TablerIcon } from "@tabler/icons-react";
import type { LinkOptions } from "@tanstack/react-router";
import type { ComponentType, ReactNode } from "react";
import type { MessageDescriptor } from "react-intl";
import type { IdentityProvider, UserRole } from "@/lib/auth/identity";
import { setIdentityProvider } from "@/lib/auth/identity";
import type { Locale } from "@/lib/constants";
import type {
  FeatureGroupDefinition,
  OrganisationFeatureKey,
} from "@/lib/organisation-features";

export type SidebarRoleGate = "operator" | "developer" | "manager";

/**
 * A sidebar entry. `requiredFeature` both gates the link and supplies its label;
 * entries without one carry their own `labelId` and `defaultMessage`, which is how
 * extensions add links for features the core organisation flags do not describe.
 */
export type NavigationConfigLink = {
  icon: TablerIcon;
  requiredFeature?: OrganisationFeatureKey;
  requiredRole?: SidebarRoleGate;
  labelId?: string;
  defaultMessage?: string;
} & ({ link: LinkOptions; href?: never } | { href: string; link?: never });

/**
 * A sidebar group. Reusing the `groupKey` of an existing group appends to it rather
 * than creating a second one with the same heading.
 */
export type NavigationGroupConfig = {
  groupKey: string;
  requiredRole?: SidebarRoleGate;
  labelId?: string;
  defaultMessage?: string;
  links: readonly NavigationConfigLink[];
};

/**
 * Named points in the built-in screens where an extension may render. The core owns
 * the surrounding page and passes each slot the context its components need; add a
 * slot here and render an `ExtensionSlot` for it where it belongs.
 */
export type SlotProps = {
  "organisation-detail": { tenantId: string };
};

export type SlotName = keyof SlotProps;

export type SlotComponents = {
  [Name in SlotName]?: ComponentType<SlotProps[Name]>;
};

/** Generic presentation transaction-data cases contributed by a build extension. */
export type PresentationTestCase = {
  id: string;
  label: MessageDescriptor;
  transactionData: readonly unknown[];
  /** Additional top-level fields for the generic process extension-data seam. */
  extensionData?: Readonly<Record<string, unknown>>;
};

/** An entry in the dashboard overview. The core gates and renders it. */
export type DashboardSegment = {
  title: ReactNode;
  description: ReactNode;
  icon: TablerIcon;
  allowedRoles?: UserRole[];
  requiredFeature?: OrganisationFeatureKey;
} & ({ link: LinkOptions; href?: never } | { href: string; link?: never });

/** Dashboard entries an extension adds to one of the built-in blocks. */
export type DashboardSegmentGroup = {
  blockKey: string;
  segments: readonly DashboardSegment[];
};

export type MessageLoader = () => Promise<Record<string, string>>;

export type CockpitExtension = {
  /** Unique across registered extensions; registering the same id twice throws. */
  id: string;
  navGroups?: readonly NavigationGroupConfig[];
  slots?: SlotComponents;
  dashboardSegments?: readonly DashboardSegmentGroup[];
  featureGroups?: readonly FeatureGroupDefinition[];
  presentationTestCases?: readonly PresentationTestCase[];
  /** Merged over the built-in catalogue, so an extension may also reword it. */
  messages?: Partial<Record<Locale, MessageLoader>>;
  identityProvider?: IdentityProvider;
};

const extensions: CockpitExtension[] = [];

/**
 * Registers an extension. Call during start-up, before the app renders: the
 * sidebar and the message catalogue read the registry on first render only.
 */
export function registerExtension(extension: CockpitExtension) {
  if (extensions.some((item) => item.id === extension.id)) {
    throw new Error(`Extension "${extension.id}" is already registered`);
  }
  extensions.push(extension);

  if (extension.identityProvider) {
    setIdentityProvider(extension.identityProvider);
  }
}

export function getExtensionNavGroups(): readonly NavigationGroupConfig[] {
  return extensions.flatMap((extension) => extension.navGroups ?? []);
}

/** Components registered for a slot, paired with the extension that supplied them. */
export function getSlotEntries<Name extends SlotName>(
  slot: Name,
): Array<{ id: string; Component: ComponentType<SlotProps[Name]> }> {
  return extensions.flatMap((extension) => {
    const Component = extension.slots?.[slot];
    return Component ? [{ id: extension.id, Component }] : [];
  });
}

/** Extension entries for one dashboard block, in registration order. */
export function getExtensionDashboardSegments(
  blockKey: string,
): DashboardSegment[] {
  return extensions.flatMap((extension) =>
    (extension.dashboardSegments ?? [])
      .filter((group) => group.blockKey === blockKey)
      .flatMap((group) => group.segments),
  );
}

/** Feature groups an extension contributes to the organisation settings page. */
export function getExtensionFeatureGroups(): readonly FeatureGroupDefinition[] {
  return extensions.flatMap((extension) => extension.featureGroups ?? []);
}

export function getExtensionMessageLoaders(locale: Locale): MessageLoader[] {
  return extensions.flatMap((extension) => extension.messages?.[locale] ?? []);
}

export function getExtensionPresentationTestCases(): readonly PresentationTestCase[] {
  return extensions.flatMap((extension) => extension.presentationTestCases ?? []);
}

/** Appends extension links to the group they name, keeping the built-in order. */
export function mergeNavGroups(
  core: readonly NavigationGroupConfig[],
  contributed: readonly NavigationGroupConfig[],
): NavigationGroupConfig[] {
  const merged = core.map((group) => {
    const additions = contributed
      .filter((item) => item.groupKey === group.groupKey)
      .flatMap((item) => item.links);

    return additions.length > 0
      ? { ...group, links: [...group.links, ...additions] }
      : group;
  });

  const newGroups = contributed.filter(
    (item) => !core.some((group) => group.groupKey === item.groupKey),
  );

  const allGroups = [...merged, ...newGroups];
  const administrationGroups = allGroups.filter(
    (group) => group.groupKey === "administration",
  );

  // Keep the built-in Administration section after all extension-provided
  // sections, including extensions that add links to Administration itself.
  return [
    ...allGroups.filter((group) => group.groupKey !== "administration"),
    ...administrationGroups,
  ];
}
