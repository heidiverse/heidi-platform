// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useIntl } from "react-intl";
import { toast } from "sonner";
import {
  addLibrary,
  deleteLibrary,
  importI14yTemplate,
} from "@/lib/api/templates/api";
import {
  templateLibraryListOptions,
  templateListOptions,
} from "@/lib/api/templates/query-options";

export function useAddLibraryMutation() {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: addLibrary,
    onSuccess: () => {
      queryClient.invalidateQueries(templateLibraryListOptions());
      toast.success(
        $t({
          id: "pages.extensions.toast.add.success",
          defaultMessage: "Library added.",
        }),
      );
    },
    onError: ({ message }) => {
      toast.error(
        $t({
          id: "pages.extensions.toast.add.error",
          defaultMessage: "Failed to add library.",
        }),
        { description: message },
      );
    },
  });
}

export function useDeleteLibraryMutation() {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: deleteLibrary,
    onSuccess: () => {
      queryClient.invalidateQueries(templateLibraryListOptions());
      queryClient.invalidateQueries(templateListOptions());
      toast.success(
        $t({
          id: "pages.extensions.toast.delete.success",
          defaultMessage: "Library deleted.",
        }),
      );
    },
    onError: ({ message }) => {
      toast.error(
        $t({
          id: "pages.extensions.toast.delete.error",
          defaultMessage: "Failed to delete library.",
        }),
        { description: message },
      );
    },
  });
}

export function useImportI14yTemplateMutation() {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: importI14yTemplate,
    onSuccess: () => {
      queryClient.invalidateQueries(templateLibraryListOptions());
      queryClient.invalidateQueries(templateListOptions());
      toast.success(
        $t({
          id: "pages.extensions.i14y.toast.success",
          defaultMessage: "I14Y schema imported.",
        }),
      );
    },
    onError: ({ message }) => {
      toast.error(
        $t({
          id: "pages.extensions.i14y.toast.error",
          defaultMessage: "Failed to import I14Y schema.",
        }),
        { description: message },
      );
    },
  });
}
