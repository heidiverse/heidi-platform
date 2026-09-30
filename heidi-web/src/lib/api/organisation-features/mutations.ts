// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useIntl } from "react-intl";
import { toast } from "sonner";
import { updateOrganisationFeatures } from "@/lib/api/organisation-features/api";
import { organisationFeaturesOptions } from "@/lib/api/organisation-features/query-options";
import type { OrganisationFeatures } from "@/lib/organisation-features";

export function useUpdateOrganisationFeaturesMutation({
  showToast = true,
}: {
  showToast?: boolean;
} = {}) {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: ({
      tenantId,
      features,
    }: {
      tenantId: string;
      features: OrganisationFeatures;
    }) => updateOrganisationFeatures(tenantId, features),
    onSuccess: (_, { tenantId }) => {
      queryClient.invalidateQueries(organisationFeaturesOptions(tenantId));
      if (showToast) {
        toast.success(
          $t({
            id: "pages.settings.toast.features.success",
            defaultMessage: "Organisation features updated.",
          }),
        );
      }
    },
    onError: (e) => {
      if (showToast) {
        toast.error(
          $t({
            id: "pages.settings.toast.features.error",
            defaultMessage: "Feature update was not successful.",
          }),
          {
            description: e.message,
          },
        );
      }
    },
  });
}
