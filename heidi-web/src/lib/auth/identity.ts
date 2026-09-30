// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { jotaiStore, selectedTenantAtom } from "@/lib/atoms";
import { runtimeConfig } from "@/lib/runtime-config";
import { fetchWithRedirect, fileToDataURL } from "@/lib/utils";
import { migrateTenantSelection } from "./tenant-selection";

export const UserRole = {
  SuperAdmin: "SUPER_ADMIN",
  Admin: "ADMIN",
  Manager: "MANAGER",
  Developer: "DEVELOPER",
  Operator: "OPERATOR",
  Editor: "EDITOR",
} as const;

export type UserRole = (typeof UserRole)[keyof typeof UserRole];

export type UserTokenData = {
  userId: string;
  email: string;
  tenantId: string;
  roles: UserRole[];
  displayName: string;
  picture: string;
};

export type ProfileUpdate = {
  displayName?: string;
  thumbnail?: File;
};

/**
 * Where the cockpit reads the signed-in user from. The open-source build uses
 * the platform API; deployments with their own IAM replace it via
 * `setIdentityProvider`.
 */
export type IdentityProvider = {
  getCurrentUser(): Promise<UserTokenData>;
  updateProfile(update: ProfileUpdate): Promise<void>;
};

/** Shape of `UserProfile` as returned by `GET /management/v1/user/me`. */
interface UserProfileResponse {
  userId: string;
  email: string;
  tenantId: string;
  role: UserRole[];
  displayName?: string;
  image?: string;
}

export const platformIdentityProvider: IdentityProvider = {
  async getCurrentUser() {
    const res = await fetchWithRedirect(
      new URL("management/v1/user/me", runtimeConfig.heidiApiBaseUrl),
    );
    if (!res.ok) {
      throw new Error("Could not fetch user info");
    }
    const profile = (await res.json()) as UserProfileResponse;
    const roles = profile.role ?? [];
    if (roles.includes(UserRole.SuperAdmin) && profile.tenantId?.trim()) {
      const bootstrapResponse = await fetchWithRedirect(
        new URL("management/v1/tenants/bootstrap", runtimeConfig.heidiApiBaseUrl),
        { method: "POST" },
      );
      if (!bootstrapResponse.ok) {
        throw new Error("Could not bootstrap super-admin tenant");
      }
    }

    return {
      userId: profile.userId,
      email: profile.email,
      tenantId: profile.tenantId,
      roles,
      displayName:
        profile.displayName || profile.email || profile.userId || "User",
      // The platform API stores the thumbnail as a data URL, so it is usable as-is.
      picture: profile.image ?? "",
    };
  },
  async updateProfile({ displayName, thumbnail }) {
    const res = await fetchWithRedirect(
      new URL("management/v1/user/me", runtimeConfig.heidiApiBaseUrl),
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...(displayName && { displayName }),
          ...(thumbnail && { thumbnail: await fileToDataURL(thumbnail) }),
        }),
      },
    );
    if (!res.ok) {
      throw new Error("Could not update user metadata");
    }
  },
};

let identityProvider: IdentityProvider = platformIdentityProvider;

export function setIdentityProvider(provider: IdentityProvider) {
  identityProvider = provider;
}

const publicRouteSegments = new Set(["public"]);

/** Allows a hosted cockpit extension to add its own unauthenticated route roots. */
export function registerPublicRouteSegment(segment: string) {
  publicRouteSegments.add(segment);
}

export async function getCurrentUser() {
  if (
    publicRouteSegments.has(
      window.location.pathname.split("/")[1] ?? "",
    )
  ) {
    return null as unknown as UserTokenData;
  }

  const user = await identityProvider.getCurrentUser();
  const selectedTenant = migrateTenantSelection(
    jotaiStore.get(selectedTenantAtom),
    user.tenantId,
  );

  if (
    !user.roles.includes(UserRole.SuperAdmin) ||
    (user.roles.includes(UserRole.SuperAdmin) && !selectedTenant)
  ) {
    jotaiStore.set(selectedTenantAtom, user.tenantId);
  } else if (selectedTenant !== jotaiStore.get(selectedTenantAtom)) {
    jotaiStore.set(selectedTenantAtom, selectedTenant);
  }

  return user;
}

export function updateProfile(update: ProfileUpdate) {
  return identityProvider.updateProfile(update);
}
