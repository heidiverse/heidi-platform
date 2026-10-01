// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useIntl } from "react-intl";
import { toast } from "sonner";
import { refreshToken, updateProfile } from "@/lib/auth/identity";
import { userTokenDataOptions } from "@/lib/auth/query-options";

export function useUpdateUserMetadataMutation() {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: updateProfile,
    onSuccess: () => {
      queryClient.invalidateQueries(userTokenDataOptions());
      toast.success(
        $t({
          id: "users.toast.update.success",
          defaultMessage: "User updated",
        }),
      );
    },
    onError: (e) => {
      toast.error(
        $t({
          id: "users.toast.update.error",
          defaultMessage: "Failed to update user",
        }),
        { description: e.message },
      );
    },
  });
}

export function useRefreshTokenMutation() {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: refreshToken,
    onSuccess: () => {
      queryClient.invalidateQueries(userTokenDataOptions());
      toast.success(
        $t({
          id: "common.profile.refreshToken.success",
          defaultMessage: "Token refreshed",
        }),
      );
    },
    onError: (e) => {
      toast.error(
        $t({
          id: "common.profile.refreshToken.error",
          defaultMessage: "Failed to refresh token",
        }),
        { description: e.message },
      );
    },
  });
}
