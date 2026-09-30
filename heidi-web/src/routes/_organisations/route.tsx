// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";
import { OrganisationsLayout } from "@/components/layout/organisations-layout";
import { UserRole } from "@/lib/auth/identity";

export const Route = createFileRoute("/_organisations")({
  beforeLoad: ({ context }) => {
    if (!context.user.roles.includes(UserRole.SuperAdmin)) {
      throw redirect({ to: "/" });
    }
  },
  component: () => {
    return (
      <OrganisationsLayout>
        <Outlet />
      </OrganisationsLayout>
    );
  },
});
