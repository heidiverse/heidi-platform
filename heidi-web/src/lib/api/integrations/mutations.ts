// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useIntl } from "react-intl";
import { toast } from "sonner";
import {
  createIntegration,
  deleteIntegration,
  updateIntegration,
} from "@/lib/api/integrations/api";
import { integrationsListOptions } from "@/lib/api/integrations/query-options";

export function useCreateIntegrationMutation() {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: createIntegration,
    onSuccess: () => {
      queryClient.invalidateQueries(integrationsListOptions());
      toast.success(
        $t({
          id: "integration.add",
          defaultMessage: "Integration added",
        }),
      );
    },
    onError: (e) => {
      toast.error(
        $t({
          id: "integration.add.error",
          defaultMessage: "Failed to add integration",
        }),
        { description: e.message },
      );
    },
  });
}

export function useUpdateIntegrationMutation() {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: updateIntegration,
    onSuccess: () => {
      queryClient.invalidateQueries(integrationsListOptions());
      toast.success(
        $t({
          id: "integration.update",
          defaultMessage: "Integration updated",
        }),
      );
    },
    onError: (e) => {
      toast.error(
        $t({
          id: "integration.update.error",
          defaultMessage: "Failed to update Integration",
        }),
        { description: e.message },
      );
    },
  });
}

export function useDeleteIntegrationMutation() {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: deleteIntegration,
    onSuccess: () => {
      queryClient.invalidateQueries(integrationsListOptions());
      toast.success(
        $t({
          id: "integration.delete",
          defaultMessage: "Integration deleted",
        }),
      );
    },
    onError: (e) => {
      toast.error(
        $t({
          id: "integration.delete.error",
          defaultMessage: "Failed to delete Integration",
        }),
        { description: e.message },
      );
    },
  });
}
