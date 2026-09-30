// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useIntl } from "react-intl";
import { toast } from "sonner";
import { organisationListOptions } from "@/lib/api/organisations/query-options";
import {
  addOrganisationToTrustRegistry,
  generateVerifierCertificateForOrganisationInTrustRegistry,
  updateSettings,
} from "@/lib/api/settings/api";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";

export function useUpdateSettingsMutation({
  showToast = true,
}: {
  showToast?: boolean;
} = {}) {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: updateSettings,
    onSuccess: (_, { tenantId }) => {
      queryClient.invalidateQueries(
        settingsForOrganisationOptions(tenantId),
      );
      queryClient.invalidateQueries(organisationListOptions());
      if (showToast) {
        toast.success(
          $t({
            id: "pages.settings.toast.update.success",
            defaultMessage: "Settings updated.",
          }),
        );
      }
    },
    onError: (e) => {
      if (showToast) {
        toast.error(
          $t({
            id: "pages.settings.toast.update.error",
            defaultMessage: "Update was not successful.",
          }),
          { description: e.message },
        );
      }
    },
  });
}

export function useAddOrganisationToTrustRegistryMutation({
  showToast = true,
}: {
  showToast?: boolean;
} = {}) {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: addOrganisationToTrustRegistry,
    onSuccess: (_, tenantId) => {
      queryClient.invalidateQueries(
        settingsForOrganisationOptions(tenantId),
      );
      if (showToast) {
        toast.success(
          $t({
            id: "pages.settings.toast.trustRegistry.success",
            defaultMessage: "Organisation added to trust registry.",
          }),
        );
      }
    },
    onError: (e) => {
      if (showToast) {
        toast.error(
          $t({
            id: "pages.settings.toast.trustRegistry.error",
            defaultMessage: "Add to trust registry was not successful.",
          }),
          {
            description: e.message,
          },
        );
      }
    },
  });
}

export function useGenerateVerifierCertificateMutation() {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: generateVerifierCertificateForOrganisationInTrustRegistry,
    onSuccess: (_, tenantId) => {
      queryClient.invalidateQueries(
        settingsForOrganisationOptions(tenantId),
      );
      toast.success(
        $t({
          id: "pages.settings.toast.verifierCertificate.success",
          defaultMessage: "Verifier certificate generated.",
        }),
      );
    },
    onError: (e) => {
      toast.error(
        $t({
          id: "pages.settings.toast.verifierCertificate.error",
          defaultMessage: "Generation was not successful.",
        }),
        { description: e.message },
      );
    },
  });
}
