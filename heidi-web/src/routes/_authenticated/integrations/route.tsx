// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { createFileRoute } from "@tanstack/react-router";
import { UserRole } from "@/lib/auth/identity";
import { requireRoleAndFeatureAccess } from "@/lib/organisation-features";

export const Route = createFileRoute("/_authenticated/integrations")({
  beforeLoad: async ({ context }) => {
    await requireRoleAndFeatureAccess({
      queryClient: context.queryClient,
      user: context.user,
      allowedRoles: [UserRole.SuperAdmin, UserRole.Admin, UserRole.Developer],
      feature: "integrations",
    });
  },
  loader({ context: { intl } }) {
    return {
      crumb: intl.$t({
        id: "pages.integrations",
        defaultMessage: "Integrations",
      }),
    };
  },
});
