// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useRouter } from "@tanstack/react-router";
import { useIntl } from "react-intl";
import { toast } from "sonner";
import { jotaiStore, selectedTenantAtom } from "@/lib/atoms";
import { CredentialSchemaState } from "@/types/credential-schema";
import {
  archiveSchema,
  copySchemaToOrganisation,
  createDraftFromSchema,
  createSchema,
  getSchemaOverview,
  publishSchema,
  updateSchema,
} from "./api";
import { schemaListOptions, schemaOptions } from "./query-options";

export function useUpdateSchemaMutation({
  onSuccess,
}: {
  onSuccess?: () => void;
} = {}) {
  const queryClient = useQueryClient();
  const router = useRouter();
  const { $t } = useIntl();

  return useMutation({
    mutationFn: updateSchema,
    onSuccess: async ({ id }) => {
      // TODO: find out if this is a good way to invalidate the queries and also the router (await is blocking)
      await queryClient.invalidateQueries(schemaOptions({ schemaId: id }));
      router.invalidate();
      onSuccess?.();
      toast.success($t({ id: "schema.save", defaultMessage: "Schema saved." }));
    },
    onError: ({ message }) => {
      toast.error(
        $t({
          id: "schema.save.error",
          defaultMessage: "Save was not successful.",
        }),
        { description: message },
      );
    },
  });
}

export function usePublishSchemaMutation() {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: publishSchema,
    onSuccess: (_, { schemaId }) => {
      queryClient.invalidateQueries(
        schemaListOptions({
          statesToExclude: [CredentialSchemaState.Archived],
          includeImages: false,
        }),
      );
      queryClient.invalidateQueries(schemaOptions({ schemaId }));
      toast.success(
        $t({ id: "schema.publish", defaultMessage: "Schema published." }),
      );
    },
    onError: ({ message }) => {
      toast.error(
        $t({
          id: "schema.publish.error",
          defaultMessage: "Publish was not successful.",
        }),
        { description: message },
      );
    },
  });
}

export function useCreateSchemaMutation({
  onSuccess,
}: { onSuccess?: () => void } = {}) {
  const navigate = useNavigate();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: createSchema,
    onSuccess: ({ id }) => {
      toast.success(
        $t({ id: "schema.create", defaultMessage: "Schema created." }),
      );
      navigate({
        to: "/credential-schemas/$schemaId",
        params: {
          schemaId: id,
        },
      });
      onSuccess?.();
    },
    onError: ({ message }) => {
      toast.error(
        $t({
          id: "schema.create.error",
          defaultMessage: "Create was not successful.",
        }),
        { description: message },
      );
    },
  });
}

export function useCreateNewVersionMutation({
  onSuccess,
}: { onSuccess?: () => void } = {}) {
  const navigate = useNavigate();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: createDraftFromSchema,
    onSuccess: ({ id }) => {
      toast.success(
        $t({ id: "schema.create", defaultMessage: "Schema created." }),
      );
      navigate({
        to: "/credential-schemas/$schemaId",
        params: {
          schemaId: id,
        },
      });
      onSuccess?.();
    },
    onError: ({ message }) => {
      toast.error(
        $t({
          id: "schema.create.error",
          defaultMessage: "Create was not successful.",
        }),
        { description: message },
      );
    },
  });
}

export function useCopySchemaToOrganisationMutation() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: copySchemaToOrganisation,
    onSuccess: (_, { tenantId }) => {
      jotaiStore.set(selectedTenantAtom, tenantId);
      queryClient.invalidateQueries(schemaListOptions());
      toast.success(
        $t({ id: "schema.copy", defaultMessage: "Schema copied." }),
      );
      navigate({ to: "/credential-schemas" });
    },
    onError: ({ message }) => {
      toast.error(
        $t({
          id: "schema.copy.error",
          defaultMessage: "Copy was not successful.",
        }),
        { description: message },
      );
    },
  });
}

export function useArchiveAllSchemasWithIdentifierMutation() {
  const queryClient = useQueryClient();
  const { $t } = useIntl();
  return useMutation({
    mutationFn: async ({
      credentialIdentifier,
    }: {
      credentialIdentifier: string;
    }) => {
      const allVersionsOfCredentialSchema = await getSchemaOverview({
        statesToExclude: [CredentialSchemaState.Archived],
        credentialIdentifier,
        includeStyle: false,
        includeImages: false,
      });
      const promises = [];
      for (const schema of allVersionsOfCredentialSchema) {
        if (schema.credentialIdentifier === credentialIdentifier) {
          promises.push(archiveSchema(schema.id));
        }
      }
      await Promise.all(promises);
    },
    onSuccess: () => {
      queryClient.invalidateQueries(schemaListOptions());
      toast.success(
        $t({ id: "schema.archive", defaultMessage: "Schema archived." }),
      );
    },
    onError: ({ message }) => {
      toast.error(
        $t({
          id: "schema.archive.error",
          defaultMessage: "Archive was not successful.",
        }),
        { description: message },
      );
    },
  });
}
