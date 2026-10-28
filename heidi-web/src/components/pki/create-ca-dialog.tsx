// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { certifySubca, createSubca, getSubcas, type IssuingSubca, selfSignSubca } from "@/lib/api/keys/issuing-pki";
import { getSigningProviders } from "@/lib/api/signing-providers/api";
import { canAddIntermediate, depthLabel, type RootPathChoice, rootPathOptions } from "./ca-options";
import { RootPathField } from "./root-path-field";

/** Keep provisioned keys when certification fails so retries do not create duplicates. */
export function CreateCaDialog({ tenantId, parentId, existing, onClose, onCreated }: {
  tenantId: string; parentId?: string; existing?: IssuingSubca;
  onClose: () => void; onCreated?: (ca: IssuingSubca) => void;
}) {
  const client = useQueryClient();
  const { data: cas = [] } = useQuery({ queryKey: ["issuing-pki", tenantId], queryFn: () => getSubcas(tenantId) });
  const { data: providers = [] } = useQuery({ queryKey: ["signing-providers", tenantId], queryFn: () => getSigningProviders(tenantId) });
  const [parent, setParent] = useState(existing?.parentCaId ?? parentId ?? "");
  const [subject, setSubject] = useState(existing?.subjectDn ?? "");
  const [algorithm, setAlgorithm] = useState("ES256");
  const [providerId, setProviderId] = useState("");
  const [created, setCreated] = useState(existing);
  const [path, setPath] = useState<RootPathChoice>({ mode: "", limit: "" });
  const parentCa = cas.find((ca) => ca.id === parent);
  const rootOptions = rootPathOptions(path);
  const pathReady = parent ? Boolean(parentCa && canAddIntermediate(parentCa)) : Boolean(rootOptions);
  const create = useMutation({
    mutationFn: async () => {
      if (!pathReady) throw new Error("Choose a valid CA path length.");
      const ca = created ?? await createSubca(tenantId, subject, algorithm, providerId ? Number(providerId) : undefined, parent || undefined);
      setCreated(ca);
      if (ca.parentCaId) await certifySubca(tenantId, ca.id);
      else if (rootOptions) await selfSignSubca(tenantId, ca.id, rootOptions);
      return ca;
    },
    onSuccess: async (ca) => {
      await client.invalidateQueries({ queryKey: ["issuing-pki", tenantId] });
      onCreated?.(ca);
      onClose();
    },
    onError: async (error) => {
      await client.invalidateQueries({ queryKey: ["issuing-pki", tenantId] });
      toast.error("Could not certify CA", { description: error.message });
    },
  });
  const locked = Boolean(created) || create.isPending;

  return <Dialog open onOpenChange={(open) => { if (!open && !create.isPending) onClose(); }}>
    <DialogContent className="max-h-[90vh] overflow-y-auto">
      <DialogHeader>
        <DialogTitle>{existing ? "Generate CA certificate" : parent ? "Add intermediate CA" : "Create root CA"}</DialogTitle>
        <DialogDescription>{created ? "The CA key is ready. Complete its certificate below." : "Create a certificate authority with its own registered signing key."}</DialogDescription>
      </DialogHeader>
      <Field>
        <FieldLabel htmlFor="ca-parent">Parent CA</FieldLabel>
        <select id="ca-parent" className="h-10 rounded-md border bg-surface px-3" value={parent} disabled={locked} onChange={(event) => setParent(event.target.value)}>
          <option value="">New root CA</option>
          {cas.filter((ca) => ca.certificateChain.length).map((ca) => <option key={ca.id} value={ca.id} disabled={!canAddIntermediate(ca)}>{ca.subjectDn} · {depthLabel(ca)}</option>)}
        </select>
      </Field>
      {parentCa && <p className="text-sm text-muted-foreground">{depthLabel(parentCa)}. This intermediate consumes one level.</p>}
      <Field><FieldLabel htmlFor="ca-subject">Subject</FieldLabel><Input id="ca-subject" value={subject} disabled={locked} onChange={(event) => setSubject(event.target.value)} placeholder="CN=CA,O=Organisation,C=CH" /></Field>
      {!parent && <RootPathField value={path} onChange={setPath} disabled={create.isPending} />}
      {!created && <div className="grid gap-4 sm:grid-cols-2">
        <Field><FieldLabel htmlFor="ca-algorithm">Algorithm</FieldLabel><select id="ca-algorithm" className="h-10 rounded-md border bg-surface px-3" value={algorithm} disabled={locked} onChange={(event) => setAlgorithm(event.target.value)}><option>ES256</option><option>ES384</option><option>ES512</option><option>RS256</option></select></Field>
        <Field><FieldLabel htmlFor="ca-provider">Signing provider</FieldLabel><select id="ca-provider" className="h-10 rounded-md border bg-surface px-3" value={providerId} disabled={locked} onChange={(event) => setProviderId(event.target.value)}><option value="">Default provider</option>{providers.map((provider) => <option key={provider.id} value={provider.id}>{provider.name}</option>)}</select></Field>
      </div>}
      <div className="flex flex-wrap justify-end gap-2 border-t pt-4">
        <Button variant="outline" disabled={create.isPending} onClick={onClose}>Cancel</Button>
        <Button disabled={!subject.trim() || !pathReady || create.isPending} onClick={() => create.mutate()}>{create.isPending ? "Creating…" : created ? "Generate certificate" : parent ? "Create intermediate" : "Create root CA"}</Button>
      </div>
    </DialogContent>
  </Dialog>;
}
