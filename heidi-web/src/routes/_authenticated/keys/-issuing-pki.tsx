// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { createSubca, getSubcas, type IssuingSubca, importSubcaChain, selfSignSubca, subcaCsrUrl } from "@/lib/api/keys/issuing-pki";
import { getSigningProviders } from "@/lib/api/signing-providers/api";

export function IssuingPki({ tenantId }: { tenantId: string }) {
  const client = useQueryClient();
  const { data: subcas = [] } = useQuery({ queryKey: ["issuing-pki", tenantId], queryFn: () => getSubcas(tenantId) });
  const { data: providers = [] } = useQuery({ queryKey: ["signing-providers", tenantId], queryFn: () => getSigningProviders(tenantId) });
  const [subjectDn, setSubjectDn] = useState("");
  const [algorithm, setAlgorithm] = useState("ES256");
  const [providerId, setProviderId] = useState<number | undefined>();
  const [chains, setChains] = useState<Record<string, string>>({});
  const refresh = () => client.invalidateQueries({ queryKey: ["issuing-pki", tenantId] });
  const create = useMutation({
    mutationFn: () => createSubca(tenantId, subjectDn, algorithm, providerId),
    onSuccess: async () => { setSubjectDn(""); await refresh(); toast.success("SubCA key created"); },
    onError: (error) => toast.error("Could not create SubCA", { description: error.message }),
  });
  const selfSign = useMutation({
    mutationFn: (id: string) => selfSignSubca(tenantId, id),
    onSuccess: async () => { await refresh(); toast.success("SubCA certificate created"); },
    onError: (error) => toast.error("Could not certify SubCA", { description: error.message }),
  });
  const upload = useMutation({
    mutationFn: (id: string) => importSubcaChain(tenantId, id, parseChain(chains[id] ?? "")),
    onSuccess: async () => { await refresh(); toast.success("SubCA chain imported"); },
    onError: (error) => toast.error("Could not import SubCA chain", { description: error.message }),
  });

  return <Card className="mt-4 space-y-5">
    <div><h2 className="text-2xl font-semibold">Issuing PKI</h2><p className="text-sm text-muted-foreground">Organisation SubCAs use signing service keys. The offline root remains outside Heidi.</p></div>
    <div className="grid gap-3 rounded-lg border p-4 sm:grid-cols-2">
      <Field><FieldLabel htmlFor="subca-subject">SubCA subject</FieldLabel><Input id="subca-subject" value={subjectDn} onChange={(event) => setSubjectDn(event.target.value)} placeholder="CN=Issuing SubCA,O=Organisation,C=CH" /></Field>
      <Field><FieldLabel htmlFor="subca-algorithm">Algorithm</FieldLabel><select id="subca-algorithm" className="h-9 rounded-md border bg-transparent px-3" value={algorithm} onChange={(event) => setAlgorithm(event.target.value)}><option>ES256</option><option>ES384</option><option>RS256</option></select></Field>
      <Field><FieldLabel htmlFor="subca-provider">Signing provider</FieldLabel><select id="subca-provider" className="h-9 rounded-md border bg-transparent px-3" value={providerId ?? ""} onChange={(event) => setProviderId(event.target.value ? Number(event.target.value) : undefined)}><option value="">Default provider</option>{providers.map((provider) => <option value={provider.id} key={provider.id}>{provider.name}</option>)}</select></Field>
      <div className="flex items-end"><Button disabled={!subjectDn.trim() || create.isPending} onClick={() => create.mutate()}>Create SubCA key</Button></div>
    </div>
    {subcas.length === 0 && <p className="text-sm text-muted-foreground">No SubCA keys yet.</p>}
    {subcas.map((ca) => <div key={ca.id} className="space-y-3 rounded-lg border p-4">
      <div><p className="font-semibold">{ca.subjectDn}</p><p className="text-sm text-muted-foreground">{ca.certificateSource ?? "Awaiting certificate"} · {ca.keyVersionId}</p><p className="text-sm text-muted-foreground">Wallet trust and trusted-list recognition are external and have not been verified.</p></div>
      <div className="flex flex-wrap gap-2"><Button variant="outline" asChild><a href={subcaCsrUrl(tenantId, ca.id)} download={`subca-${ca.id}.csr.pem`}>Download CSR</a></Button>
        {!ca.certificateSource && <Button variant="outline" disabled={selfSign.isPending} onClick={() => selfSign.mutate(ca.id)}>Create self-signed certificate</Button>}
        {ca.certificateChain.length > 0 && <Button variant="outline" onClick={() => downloadChain(ca)}>Download certificate</Button>}
      </div>
      <Field><FieldLabel htmlFor={`subca-chain-${ca.id}`}>Offline-root-signed chain (PEM, SubCA first)</FieldLabel><textarea id={`subca-chain-${ca.id}`} className="min-h-28 w-full rounded-md border p-2 font-mono text-xs" value={chains[ca.id] ?? ""} onChange={(event) => setChains({ ...chains, [ca.id]: event.target.value })} /></Field>
      <Button disabled={!chains[ca.id]?.trim() || upload.isPending} onClick={() => upload.mutate(ca.id)}>Import chain</Button>
    </div>)}
  </Card>;
}

/** Saves the SubCA chain as PEM, SubCA first, e.g. for external verifiers or wallets. */
function downloadChain(ca: IssuingSubca) {
  const pem = ca.certificateChain.map((certificate) =>
    `-----BEGIN CERTIFICATE-----\n${certificate.match(/.{1,64}/g)?.join("\n")}\n-----END CERTIFICATE-----\n`).join("");
  const url = URL.createObjectURL(new Blob([pem], { type: "application/x-pem-file" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = `subca-${ca.id}.pem`;
  link.click();
  URL.revokeObjectURL(url);
}

function parseChain(input: string): string[] {
  const blocks = [...input.matchAll(/-----BEGIN CERTIFICATE-----([\s\S]*?)-----END CERTIFICATE-----/g)];
  if (blocks.length) return blocks.map((block) => (block[1] ?? "").replace(/\s/g, ""));
  return input.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
}
