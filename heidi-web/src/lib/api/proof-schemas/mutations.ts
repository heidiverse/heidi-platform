// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import {
  archiveProofSchema,
  createProofSchema,
  publishProofSchemaToTrustRegistry,
  updateProofSchema,
} from "@/lib/api/proof-schemas/api";
import {
  proofSchemaListOptions,
  proofSchemaOptions,
} from "@/lib/api/proof-schemas/query-options";

export function useCreateProofSchemaMutation() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  return useMutation({
    mutationFn: createProofSchema,
    onSuccess: (data) => {
      queryClient.invalidateQueries(proofSchemaListOptions());
      toast.success("Proof Schema created.");
      navigate({
        to: "/proof-schemas/$proofSchemaId",
        params: {
          proofSchemaId: data.id.toString(),
        },
      });
    },
    onError: ({ message }) =>
      toast.error("Create was not successful.", { description: message }),
  });
}

export function useUpdateProofSchemaMutation({
  onSuccess,
}: {
  onSuccess?: () => void;
} = {}) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: updateProofSchema,
    onSuccess: (data) => {
      toast.success("Proof Schema updated.");
      queryClient.invalidateQueries(
        proofSchemaOptions({ schemaId: data.schemaId }),
      );
      onSuccess?.();
    },
    onError: ({ message }) =>
      toast.error("Create was not successful.", { description: message }),
  });
}

export function useArchiveProofSchemaMutation() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  return useMutation({
    mutationFn: archiveProofSchema,
    onSuccess: () => {
      queryClient.invalidateQueries(proofSchemaListOptions());
      toast.success("Proof Schema archived.");
      navigate({ to: "/proof-schemas" });
    },
    onError: ({ message }) =>
      toast.error("Archive was not successful.", { description: message }),
  });
}

export function usePublishProofSchemaToTrustRegistryMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: publishProofSchemaToTrustRegistry,
    onSuccess: (_, { proofSchemaId }) => {
      toast.success("Proof Schema published to trust registry.");
      queryClient.invalidateQueries(
        proofSchemaOptions({ schemaId: proofSchemaId }),
      );
    },
    onError: ({ message }) =>
      toast.error("Publish was not successful.", { description: message }),
  });
}
