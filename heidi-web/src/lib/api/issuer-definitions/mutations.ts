// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useIntl } from "react-intl";
import { toast } from "sonner";
import {
  createIssuerDefinition,
  deleteIssuerDefinition,
  updateIssuerDefinition,
} from "@/lib/api/issuer-definitions/api";

export function useCreateIssuerDefinitionMutation() {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: createIssuerDefinition,
    onSuccess: (_identity, variables) => {
      queryClient.invalidateQueries({ queryKey: ["issuer-definitions"] });
      if (variables.tenantId) {
        queryClient.invalidateQueries({
          queryKey: ["settings", variables.tenantId],
        });
      }
      toast.success(
        $t({
          id: "issuerDefinition.create",
          defaultMessage: "Identity added",
        }),
      );
    },
    onError: (e) => {
      toast.error(
        $t({
          id: "issuerDefinition.create.error",
          defaultMessage: "Failed to add identity",
        }),
        {
          description: e.message,
        },
      );
    },
  });
}

export function useUpdateIssuerDefinitionMutation() {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: updateIssuerDefinition,
    onSuccess: (_result, variables) => {
      queryClient.invalidateQueries({ queryKey: ["issuer-definitions"] });
      if (variables.tenantId) {
        queryClient.invalidateQueries({
          queryKey: ["settings", variables.tenantId],
        });
      }
      toast.success(
        $t({
          id: "issuerDefinition.update",
          defaultMessage: "Identity updated",
        }),
      );
    },
    onError: (e) => {
      toast.error(
        $t({
          id: "issuerDefinition.update.error",
          defaultMessage: "Failed to update identity",
        }),
        {
          description: e.message,
        },
      );
    },
  });
}

export function useDeleteIssuerDefinitionMutation() {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: deleteIssuerDefinition,
    onSuccess: (_result, variables) => {
      queryClient.invalidateQueries({ queryKey: ["issuer-definitions"] });
      if (variables.tenantId) {
        queryClient.invalidateQueries({
          queryKey: ["settings", variables.tenantId],
        });
      }
      toast.success(
        $t({
          id: "issuerDefinition.delete.success",
          defaultMessage: "Identity deleted",
        }),
      );
    },
    onError: (e) => {
      toast.error(
        $t({
          id: "issuerDefinition.delete.error",
          defaultMessage: "Failed to delete identity",
        }),
        {
          description: e.message,
        },
      );
    },
  });
}
