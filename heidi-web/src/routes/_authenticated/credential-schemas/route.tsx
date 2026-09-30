// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { createFileRoute } from "@tanstack/react-router";
import { requireRoleAndFeatureAccess } from "@/lib/organisation-features";

export const Route = createFileRoute("/_authenticated/credential-schemas")({
  beforeLoad: async ({ context }) => {
    await requireRoleAndFeatureAccess({
      queryClient: context.queryClient,
      user: context.user,
      feature: "credentialSchemas",
    });
  },
  loader: ({ context }) => {
    return {
      crumb: context.intl.$t({
        id: "pages.credentialSchemas",
        defaultMessage: "Credential Schemas",
      }),
    };
  },
});
