// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { addKeyCertificate, type CertificateProfile, createKey, type KeyUsage, keyCapabilities, type PlatformKey } from "@/lib/api/keys/api";
import { getSigningProviders } from "@/lib/api/signing-providers/api";

const defaultAlgorithm = "ES256";

/** Keep provisioning beside the slot; certificates can be completed without leaving it. */
export function SlotKeyCreate({ tenantId, suggestedName, usage, profile, trustSystem, onCreated }: {
  tenantId?: string; suggestedName: string; usage: KeyUsage; profile: CertificateProfile;
  trustSystem: string; onCreated: (key: PlatformKey) => void;
}) {
  const queryClient = useQueryClient();
  const [name, setName] = useState(suggestedName);
  const [algorithm, setAlgorithm] = useState(defaultAlgorithm);
  const [providerId, setProviderId] = useState("");
  const scopeKey = tenantId ?? "platform";
  const { data: providers = [] } = useQuery({ queryKey: ["signing-providers", scopeKey], queryFn: () => getSigningProviders(tenantId) });
  const { data: capabilities } = useQuery({ queryKey: ["key-capabilities", scopeKey], queryFn: () => keyCapabilities(tenantId) });
  const create = useMutation({
    mutationFn: () => createKey({ tenantId, key: {
      keyId: name.trim(), algorithm, usage, providerId: providerId ? Number(providerId) : undefined,
    } }),
    onSuccess: async (key) => {
      // Preserve the created key even if certificate provisioning needs a retry.
      if (usage === "SIGN" && trustSystem !== "Switzerland" && capabilities?.developmentCertificates && key.activeVersionId) {
        try {
          await addKeyCertificate({ tenantId, keyId: key.keyId, versionId: key.activeVersionId, profile, trustSystem });
        } catch (error) {
          toast.error("Key created; certificate still needed.", { description: error instanceof Error ? error.message : undefined });
        }
      }
      await queryClient.invalidateQueries({ queryKey: ["platform-keys", scopeKey] });
      onCreated(key);
    },
    onError: (error) => toast.error("Could not create key.", { description: error.message }),
  });

  return <div className="space-y-3 rounded-md border p-3">
    <Field><FieldLabel htmlFor="slot-new-key">Key name</FieldLabel><Input id="slot-new-key" value={name} onChange={(event) => setName(event.target.value)} /></Field>
    <details><summary className="text-sm">Provider and algorithm</summary><div className="mt-2 space-y-3">
      <Field><FieldLabel htmlFor="slot-new-provider">Provider</FieldLabel><select id="slot-new-provider" className="h-9 rounded-md border bg-transparent px-3" value={providerId} onChange={(event) => setProviderId(event.target.value)}><option value="">Default provider</option>{providers.map((provider) => <option key={provider.id} value={provider.id}>{provider.name}</option>)}</select></Field>
      <Field><FieldLabel htmlFor="slot-new-algorithm">Algorithm</FieldLabel><Input id="slot-new-algorithm" value={algorithm} disabled={usage === "KEY_AGREEMENT"} onChange={(event) => setAlgorithm(event.target.value)} /></Field>
    </div></details>
    <p className="text-sm text-muted-foreground">{usage !== "SIGN" ? "Creates a key for credential request decryption."
      : trustSystem === "Switzerland" ? "Publish the new public key in your DID before saving the slot."
      : capabilities?.developmentCertificates ? "Includes a development certificate. Production wallets require a trusted CA."
      : "After creation, request a certificate from your CA using Certificates / CSR."}</p>
    <Button variant="outline" disabled={!name.trim() || !algorithm.trim() || create.isPending} onClick={() => create.mutate()}>{create.isPending ? "Creating…" : "Create key for this slot"}</Button>
  </div>;
}
