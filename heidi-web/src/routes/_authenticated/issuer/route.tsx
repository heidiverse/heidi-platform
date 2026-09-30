// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { createFileRoute } from "@tanstack/react-router";
import { UserRole } from "@/lib/auth/identity";
import { requireRoleAndFeatureAccess } from "@/lib/organisation-features";

export const Route = createFileRoute("/_authenticated/issuer")({
  beforeLoad: async ({ context }) => {
    await requireRoleAndFeatureAccess({
      queryClient: context.queryClient,
      user: context.user,
      allowedRoles: [UserRole.SuperAdmin, UserRole.Admin, UserRole.Operator],
    });
  },
  loader({ context }) {
    return {
      crumb: context.intl.$t({
        id: "pages.issuer",
        defaultMessage: "Web Issuer",
      }),
    };
  },
});
