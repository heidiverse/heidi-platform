// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { type CertificateProfile, createKey, getKeys, type PlatformKey } from "@/lib/api/keys/api";
import { getSubcas, issueSubcaLeaf, type LeafTemplate } from "@/lib/api/keys/issuing-pki";
import { isTerminalCa } from "./ca-options";
import { CertificateChain } from "./certificate-chain";
import { CreateCaDialog } from "./create-ca-dialog";

export function IssueCertificateDialog({ tenantId, caId, signingKey, initialVersionId, initialPurpose = "CREDENTIAL_SIGNING", initialMode = "subject", onClose }: {
  tenantId: string; caId?: string; signingKey?: PlatformKey; initialVersionId?: string;
  initialPurpose?: CertificateProfile; onClose: () => void;
  initialMode?: "subject" | "csr";
}) {
  const client = useQueryClient();
  const { data: keys = [] } = useQuery({ queryKey: ["platform-keys", tenantId], queryFn: () => getKeys(tenantId) });
  const { data: cas = [] } = useQuery({ queryKey: ["issuing-pki", tenantId], queryFn: () => getSubcas(tenantId) });
  const [authority, setAuthority] = useState(caId ?? "");
  const [keyId, setKeyId] = useState(signingKey?.keyId ?? "");
  const [versionId, setVersionId] = useState(initialVersionId ?? signingKey?.activeVersionId ?? "");
  const [purpose, setPurpose] = useState<CertificateProfile>(initialPurpose);
  const [template, setTemplate] = useState<LeafTemplate>("GENERIC");
  const [subject, setSubject] = useState("");
  const [csr, setCsr] = useState("");
  const [mode, setMode] = useState<string>(initialMode);
  const [creatingCa, setCreatingCa] = useState(false);
  const [newKey, setNewKey] = useState("");
  const [createdKey, setCreatedKey] = useState<PlatformKey>();
  const selectedKey = createdKey?.keyId === keyId ? createdKey : keys.find((key) => key.keyId === keyId) ?? signingKey;
  const selectedCa = cas.find((ca) => ca.id === authority);
  const issuers = cas.filter((ca) => ca.certificateChain.length && isTerminalCa(ca, cas));
  const canIssue = issuers.some((ca) => ca.id === authority);
  const usableVersions = selectedKey?.versions.filter((version) => ["ACTIVE", "PREPARED"].includes(version.status) && version.usages.includes("SIGN")) ?? [];
  const create = useMutation({
    mutationFn: () => createKey({ tenantId, key: { keyId: newKey.trim(), algorithm: "ES256", usage: "SIGN" } }),
    onSuccess: async (key) => { setCreatedKey(key); setKeyId(key.keyId); setVersionId(key.activeVersionId ?? ""); setNewKey(""); await client.invalidateQueries({ queryKey: ["platform-keys", tenantId] }); },
    onError: (error) => toast.error("Could not create key", { description: error.message }),
  });
  const issue = useMutation({
    mutationFn: () => issueSubcaLeaf(tenantId, authority, keyId, versionId, subject, template, purpose, mode === "csr" ? csr : undefined),
    onSuccess: async () => { await client.invalidateQueries({ queryKey: ["platform-keys", tenantId] }); toast.success("Certificate issued"); onClose(); },
    onError: (error) => toast.error("Could not issue certificate", { description: error.message }),
  });
  return <>
    <Dialog open={!creatingCa} onOpenChange={(open) => { if (!open && !issue.isPending) onClose(); }}><DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
      <DialogHeader><DialogTitle>Issue certificate / sign CSR</DialogTitle></DialogHeader>
      <Field><FieldLabel htmlFor="leaf-ca">Issuing authority</FieldLabel><select id="leaf-ca" className="h-9 rounded-md border bg-transparent px-3" value={canIssue ? authority : ""} onChange={(event) => setAuthority(event.target.value)}><option value="">Choose a CA at the end of a branch</option>{issuers.map((ca) => <option key={ca.id} value={ca.id}>{ca.subjectDn}</option>)}</select></Field>
      <Button variant="outline" onClick={() => setCreatingCa(true)}>Create CA / intermediate</Button>
      {selectedCa && <CertificateChain endLabel="Issuing CA" chain={selectedCa.certificateChain} anchor={selectedCa.anchor} details={selectedCa.chainDetails} />}
      <Field><FieldLabel htmlFor="leaf-key">Registered key</FieldLabel><select id="leaf-key" className="h-9 rounded-md border bg-transparent px-3" value={keyId} disabled={Boolean(signingKey)} onChange={(event) => { const key = keys.find((candidate) => candidate.keyId === event.target.value); setKeyId(event.target.value); setVersionId(key?.activeVersionId ?? ""); }}><option value="">Choose a key</option>{(signingKey ? [signingKey] : keys).filter((key) => key.versions.some((version) => version.usages.includes("SIGN"))).map((key) => <option key={key.id} value={key.keyId}>{key.keyId}</option>)}</select></Field>
      {!signingKey && <details><summary>Create a signing key</summary><div className="mt-2 flex gap-2"><Input aria-label="New key name" value={newKey} onChange={(event) => setNewKey(event.target.value)} /><Button variant="outline" disabled={!newKey.trim() || create.isPending} onClick={() => create.mutate()}>Create key</Button></div></details>}
      <Field><FieldLabel htmlFor="leaf-version">Key version</FieldLabel><select id="leaf-version" className="h-9 rounded-md border bg-transparent px-3" value={versionId} onChange={(event) => setVersionId(event.target.value)}><option value="">Choose a version</option>{usableVersions.map((version) => <option key={version.id} value={version.id}>Version {version.version} · {version.status}</option>)}</select></Field>
      <Field><FieldLabel htmlFor="leaf-purpose">Certificate purpose</FieldLabel><select id="leaf-purpose" className="h-9 rounded-md border bg-transparent px-3" value={purpose} onChange={(event) => setPurpose(event.target.value as CertificateProfile)}><option value="CREDENTIAL_SIGNING">Credential signing</option><option value="ACCESS">Access / presentation signing</option><option value="STATUS_LIST">Status list</option></select></Field>
      <Field><FieldLabel htmlFor="leaf-template">Certificate template</FieldLabel><select id="leaf-template" className="h-9 rounded-md border bg-transparent px-3" value={template} onChange={(event) => setTemplate(event.target.value as LeafTemplate)}><option value="GENERIC">Generic</option><option value="PID">PID</option><option value="EAA">EAA</option></select></Field>
      <Field><FieldLabel htmlFor="leaf-mode">Certificate request</FieldLabel><select id="leaf-mode" className="h-9 rounded-md border bg-transparent px-3" value={mode} onChange={(event) => setMode(event.target.value)}><option value="subject">Define subject</option><option value="csr">Sign CSR</option></select></Field>
      {mode === "csr" ? <Field><FieldLabel htmlFor="leaf-csr">CSR (PEM)</FieldLabel><textarea id="leaf-csr" className="min-h-32 rounded-md border p-2 font-mono text-xs" value={csr} onChange={(event) => setCsr(event.target.value)} /><p className="text-sm text-muted-foreground">The CSR must be signed by the selected registered key version.</p></Field> : <Field><FieldLabel htmlFor="leaf-subject">Subject (distinguished name)</FieldLabel><Input id="leaf-subject" value={subject} onChange={(event) => setSubject(event.target.value)} placeholder={template === "GENERIC" ? "CN=Signing certificate" : "CN=Issuer,O=Organisation,C=CH,2.5.4.97=VATDE-123456789"} /></Field>}
      <p className="text-sm text-muted-foreground">PKI is independent of trust profiles. Assign this chain when selecting a signing key in a certificate-using profile.</p>
      <div className="flex justify-end gap-2"><Button variant="outline" disabled={issue.isPending} onClick={onClose}>Cancel</Button><Button disabled={!canIssue || !keyId || !versionId || !(mode === "csr" ? csr.trim() : subject.trim()) || issue.isPending} onClick={() => issue.mutate()}>{mode === "csr" ? "Sign CSR" : "Issue certificate"}</Button></div>
    </DialogContent></Dialog>
    {creatingCa && <CreateCaDialog tenantId={tenantId} parentId={authority || undefined} onClose={() => setCreatingCa(false)} onCreated={(ca) => setAuthority(ca.id)} />}
  </>;
}
