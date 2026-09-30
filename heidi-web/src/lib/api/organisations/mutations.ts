// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "@tanstack/react-router";
import { useIntl } from "react-intl";
import { toast } from "sonner";
import {
  createOrganisation,
  deleteOrganisationBySlug,
  updateOrganisation,
} from "@/lib/api/organisations/api";
import {
  organisationListOptions,
  organisationOptions,
} from "@/lib/api/organisations/query-options";

export function useCreateOrganisationMutation() {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: createOrganisation,
    onSuccess: () => {
      queryClient.invalidateQueries(organisationListOptions());
      toast.success(
        $t({
          id: "pages.organisations.toast.create.success",
          defaultMessage: "Organisation created",
        }),
      );
    },
    onError: (e) => {
      toast.error(
        $t({
          id: "pages.organisations.toast.create.error",
          defaultMessage: "Failed to create organisation",
        }),
        { description: e.message },
      );
    },
  });
}

export function useUpdateOrganisationMutation() {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: updateOrganisation,
    onSuccess: (_, { tenantId }) => {
      queryClient.invalidateQueries(organisationOptions(tenantId));
      queryClient.invalidateQueries(organisationListOptions());
      toast.success(
        $t({
          id: "pages.organisations.toast.update.success",
          defaultMessage: "Organisation updated",
        }),
      );
    },
    onError: (e) => {
      toast.error(
        $t({
          id: "pages.organisations.toast.update.error",
          defaultMessage: "Failed to update organisation",
        }),
        { description: e.message },
      );
    },
  });
}

export function useDeleteOrganisationMutation() {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  const { tenantId } = useParams({ strict: false });
  const navigate = useNavigate();
  return useMutation({
    mutationFn: deleteOrganisationBySlug,
    onSuccess: () => {
      queryClient.invalidateQueries(organisationListOptions());
      if (tenantId) {
        navigate({ to: "/organisations" });
      }
      toast.success(
        $t({
          id: "pages.organisations.toast.delete.success",
          defaultMessage: "Organisation deleted",
        }),
      );
    },
    onError: (e) => {
      toast.error(
        $t({
          id: "pages.organisations.toast.delete.error",
          defaultMessage: "Failed to delete organisation",
        }),
        { description: e.message },
      );
    },
  });
}
