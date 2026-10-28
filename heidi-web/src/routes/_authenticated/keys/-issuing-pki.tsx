// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconCertificate, IconChevronRight, IconDots, IconDownload, IconPlus, IconShieldLock, IconTrash } from "@tabler/icons-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { canAddIntermediate, depthLabel } from "@/components/pki/ca-options";
import { anchorLabel, CertificateChain } from "@/components/pki/certificate-chain";
import { CertificateDownload } from "@/components/pki/certificate-download";
import { CreateCaDialog } from "@/components/pki/create-ca-dialog";
import { IssueCertificateDialog } from "@/components/pki/issue-certificate-dialog";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { Field, FieldLabel } from "@/components/ui/field";
import { getKeys, type KeyCertificate, type PlatformKey, type PlatformKeyVersion } from "@/lib/api/keys/api";
import { deleteCaChain, getSubcas, type IssuingSubca, importSubcaChain, subcaCsrUrl } from "@/lib/api/keys/issuing-pki";
import { downloadPem } from "@/lib/certificate-pem";
import { KeyVersionsDialog } from "../organisation/-components/key-versions-dialog";

const ACTION_CLASS = "h-8 px-3 text-xs tracking-normal normal-case";
type IssueMode = "subject" | "csr";
type IssuedLeaf = { key: PlatformKey; version: PlatformKeyVersion; certificate: KeyCertificate };

export function IssuingPki({ tenantId }: { tenantId: string }) {
  const client = useQueryClient();
  const caQuery = useQuery({ queryKey: ["issuing-pki", tenantId], queryFn: () => getSubcas(tenantId) });
  const keyQuery = useQuery({ queryKey: ["platform-keys", tenantId], queryFn: () => getKeys(tenantId) });
  const cas = caQuery.data ?? [];
  const [creating, setCreating] = useState<{ parentId?: string; existing?: IssuingSubca }>();
  const [issuing, setIssuing] = useState<{ id: string; mode: IssueMode }>();
  const [selectedKey, setSelectedKey] = useState<PlatformKey>();
  const [importing, setImporting] = useState<IssuingSubca>();
  const [inspecting, setInspecting] = useState<IssuingSubca>();
  const [deleting, setDeleting] = useState<IssuingSubca>();
  const [downloading, setDownloading] = useState<IssuingSubca>();
  const [expanded, setExpanded] = useState<Set<string>>();
  const roots = cas.filter((ca) => !ca.parentCaId || !cas.some((parent) => parent.id === ca.parentCaId));
  const certificates = (keyQuery.data ?? []).flatMap((key) => key.versions.flatMap((version) =>
    version.certificates.map((certificate) => ({ key, version, certificate }))));
  const toggle = (id: string) => setExpanded((current) => {
    const next = new Set(current ?? cas.map((ca) => ca.id));
    if (next.has(id)) next.delete(id);
    else next.add(id);
    return next;
  });

  return <div className="mt-4 space-y-4">
    <div className="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h2 className="text-2xl font-semibold">Issuing PKI</h2>
        <p className="mt-1 text-sm text-muted-foreground">Manage certificate authorities and their trust chains.</p>
      </div>
      <Button onClick={() => setCreating({})}><IconPlus />Create CA</Button>
    </div>
    <div className="flex flex-wrap items-center justify-between gap-2 border-b pb-3">
      <p className="text-sm text-muted-foreground">{cas.length} certificate {cas.length === 1 ? "authority" : "authorities"} · {roots.length} root {roots.length === 1 ? "chain" : "chains"}</p>
      <div className="flex gap-1">
        <Button variant="ghost" className={ACTION_CLASS} disabled={!cas.length} onClick={() => setExpanded(new Set(cas.flatMap((ca) => [ca.id, `${ca.id}:leaves`])))}>Expand all</Button>
        <Button variant="ghost" className={ACTION_CLASS} disabled={!cas.length} onClick={() => setExpanded(new Set())}>Collapse all</Button>
      </div>
    </div>
    {caQuery.isPending && <Card><p className="text-sm text-muted-foreground">Loading certificate authorities…</p></Card>}
    {(caQuery.isError || keyQuery.isError) && <Card className="flex flex-wrap items-center justify-between gap-3 border-destructive/30">
      <p className="text-sm text-destructive">{caQuery.isError ? "Could not load certificate authorities." : "Could not load issued certificates."}</p>
      <Button variant="outline" className={ACTION_CLASS} onClick={() => { void caQuery.refetch(); void keyQuery.refetch(); }}>Retry</Button>
    </Card>}
    {!caQuery.isPending && !caQuery.isError && cas.length === 0 && <Card className="flex flex-col items-center gap-3 border-dashed py-12 text-center">
      <IconShieldLock className="size-9 text-muted-foreground" />
      <h3 className="font-semibold">Create your first certificate authority</h3>
      <p className="max-w-md text-sm text-muted-foreground">Start with a root CA and choose how many intermediate levels its chains may contain.</p>
      <Button variant="outline" onClick={() => setCreating({})}>Create root CA</Button>
    </Card>}
    {roots.map((ca) => <Card key={ca.id} className="overflow-hidden p-0">
      <CaBranch ca={ca} cas={cas} certificates={certificates} expanded={expanded} onToggle={toggle}
        tenantId={tenantId} onCreate={(parentId) => setCreating({ parentId })}
        onCertify={(existing) => setCreating({ existing })} onIssue={(id, mode) => setIssuing({ id, mode })}
        onKey={setSelectedKey} onImport={setImporting} onInspect={setInspecting} onDelete={setDeleting} onDownload={setDownloading} />
    </Card>)}
    {creating && <CreateCaDialog tenantId={tenantId} {...creating} onClose={() => setCreating(undefined)} />}
    {issuing && <IssueCertificateDialog tenantId={tenantId} caId={issuing.id} initialMode={issuing.mode} onClose={() => setIssuing(undefined)} />}
    {selectedKey && <KeyVersionsDialog tenantId={tenantId} signingKey={selectedKey} onClose={() => setSelectedKey(undefined)} />}
    {importing && <ImportChainDialog tenantId={tenantId} ca={importing} onClose={() => setImporting(undefined)}
      onImported={() => client.invalidateQueries({ queryKey: ["issuing-pki", tenantId] })} />}
    {deleting && <DeleteChainDialog tenantId={tenantId} ca={deleting} cas={cas} onClose={() => setDeleting(undefined)} />}
    {downloading && <DownloadChainDialog ca={downloading} leaves={certificates.filter((leaf) => issuedBy(leaf, downloading))} onClose={() => setDownloading(undefined)} />}
    {inspecting && <Dialog open onOpenChange={(open) => { if (!open) setInspecting(undefined); }}>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader><DialogTitle>Trust chain</DialogTitle><DialogDescription className="break-all">{inspecting.subjectDn}</DialogDescription></DialogHeader>
        <CertificateChain endLabel="Issuing CA" chain={inspecting.certificateChain} anchor={inspecting.anchor} details={inspecting.chainDetails} />
        <Button variant="outline" className="justify-self-end" onClick={() => setInspecting(undefined)}>Close</Button>
      </DialogContent>
    </Dialog>}
  </div>;
}

type BranchProps = {
  ca: IssuingSubca; cas: IssuingSubca[]; certificates: IssuedLeaf[]; tenantId: string;
  expanded?: Set<string>; onToggle: (id: string) => void; onCreate: (id: string) => void;
  onCertify: (ca: IssuingSubca) => void; onIssue: (id: string, mode: IssueMode) => void;
  onKey: (key: PlatformKey) => void; onImport: (ca: IssuingSubca) => void; onInspect: (ca: IssuingSubca) => void;
  onDelete: (ca: IssuingSubca) => void; onDownload: (ca: IssuingSubca) => void;
};

function issuedBy({ certificate }: IssuedLeaf, ca: IssuingSubca) {
  return certificate.issuingCaId === ca.id ||
    (!certificate.issuingCaId && ca.certificateHistory?.includes(certificate.chain[1] ?? ""));
}

/** Retain certificates issued before an authority acquired child CAs or a renewed chain. */
function CaBranch(props: BranchProps) {
  const { ca, cas, certificates, tenantId, expanded, onToggle, onCreate, onCertify, onIssue, onKey, onImport, onInspect, onDelete, onDownload } = props;
  const children = cas.filter((child) => child.parentCaId === ca.id);
  const terminal = children.length === 0;
  const certified = ca.certificateChain.length > 0;
  const expiry = ca.chainDetails?.[0]?.notAfter;
  const expired = expiry !== undefined && Date.parse(expiry) <= Date.now();
  const open = expanded?.has(ca.id) ?? true;
  const leavesId = `${ca.id}:leaves`;
  const leavesOpen = expanded?.has(leavesId) ?? false;
  const leaves = certificates.filter((leaf) => issuedBy(leaf, ca));
  const role = ca.parentCaId ? "Intermediate CA" : ca.certificateSource === "OFFLINE_ROOT" ? "External-root-backed CA" : "Root CA";

  return <Collapsible open={open} onOpenChange={() => onToggle(ca.id)}>
    <div className="flex items-start gap-2 p-4">
      <CollapsibleTrigger asChild>
        <button type="button" className="flex min-w-0 flex-1 items-start gap-3 rounded-lg text-left outline-offset-4 focus-visible:outline-ring">
          <IconChevronRight className={`mt-1 size-4 shrink-0 text-muted-foreground transition-transform ${open ? "rotate-90" : ""}`} />
          <IconShieldLock className="mt-0.5 size-5 shrink-0 text-primary" />
          <span className="min-w-0 space-y-1">
            <span className="block text-xs font-medium text-muted-foreground">{role}</span>
            <span className="block break-words font-semibold [overflow-wrap:anywhere]">{ca.subjectDn}</span>
            <span className="block text-xs text-muted-foreground">{children.length ? `${children.length} intermediate ${children.length === 1 ? "branch" : "branches"}` : "End of trust chain"}{leaves.length ? ` · ${leaves.length} issued certificates` : ""}</span>
          </span>
        </button>
      </CollapsibleTrigger>
      <DropdownMenu>
        <DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="size-8" aria-label={`Actions for ${ca.subjectDn}`}><IconDots /></Button></DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {certified && <DropdownMenuItem onSelect={() => onInspect(ca)}>View trust chain</DropdownMenuItem>}
          <DropdownMenuItem asChild><a href={subcaCsrUrl(tenantId, ca.id)} download={`ca-${ca.id}.csr.pem`}>Download CSR</a></DropdownMenuItem>
          <DropdownMenuItem onSelect={() => onImport(ca)}>Import signed CA chain</DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem variant="destructive" onSelect={() => onDelete(ca)}><IconTrash />{ca.parentCaId ? "Delete branch" : "Delete CA chain"}</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
    <CollapsibleContent>
      <div className="space-y-3 px-4 pb-4 sm:pl-14">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant={expired ? "destructive" : certified ? "secondary" : "warning"}>{expired ? "Expired" : certified ? "Certified" : "Awaiting certificate"}</Badge>
          {!ca.parentCaId && certified && <Badge variant="outline">{anchorLabel(ca.anchor)}</Badge>}
          {expiry && <span className="text-xs text-muted-foreground">{expired ? "Expired" : "Expires"} {new Date(expiry).toLocaleDateString()}</span>}
        </div>
        {certified && <p className="text-xs text-muted-foreground">{depthLabel(ca)}</p>}
        <div className="flex flex-wrap items-center gap-2">
          {!certified && <Button className={ACTION_CLASS} onClick={() => onCertify(ca)}>{ca.parentCaId ? "Sign with parent CA" : "Generate root certificate"}</Button>}
          {certified && terminal && <>
            <Button className={ACTION_CLASS} disabled={expired} onClick={() => onIssue(ca.id, "subject")}><IconCertificate />Issue certificate</Button>
            <Button variant="outline" className={ACTION_CLASS} disabled={expired} onClick={() => onIssue(ca.id, "csr")}>Sign CSR</Button>
          </>}
          {certified && <Button variant="outline" className={ACTION_CLASS} disabled={expired || !canAddIntermediate(ca)} onClick={() => onCreate(ca.id)}><IconPlus />Add intermediate</Button>}
          {certified && terminal && <Button variant="ghost" className={`${ACTION_CLASS} sm:ml-auto`} onClick={() => onDownload(ca)}><IconDownload />Download chain</Button>}
        </div>
        {(terminal || leaves.length > 0) && <Collapsible open={leavesOpen} onOpenChange={() => onToggle(leavesId)} className="rounded-lg border bg-muted/20">
          <CollapsibleTrigger asChild><button type="button" className="flex w-full items-center gap-2 rounded-lg p-3 text-left text-sm focus-visible:outline-ring">
            <IconChevronRight className={`size-4 shrink-0 transition-transform ${leavesOpen ? "rotate-90" : ""}`} />
            <span>{terminal ? "Issued certificates" : "Previously issued certificates"}</span><Badge variant="secondary" className="ml-auto">{leaves.length}</Badge>
          </button></CollapsibleTrigger>
          <CollapsibleContent>
            {!leaves.length && <p className="px-4 pb-4 text-sm text-muted-foreground">No certificates issued by this CA yet.</p>}
            <ul className="divide-y border-t">
              {leaves.map(({ key, version, certificate }) => <li key={certificate.id} className="flex items-center gap-1 pr-3">
                <button type="button" className="flex min-w-0 flex-1 items-center justify-between gap-3 px-4 py-3 text-left hover:bg-muted/50 focus-visible:outline-ring" onClick={() => onKey(key)}>
                  <span className="min-w-0"><span className="block break-words text-sm font-medium">{certificate.chainDetails?.[0]?.subjectDn ?? key.keyId}</span>
                    <span className="mt-1 block text-xs text-muted-foreground">{key.keyId} · v{version.version} · {certificate.profile.replaceAll("_", " ").toLowerCase()}</span>
                    <span className="mt-1 block text-xs text-muted-foreground">{certificate.retiredAt ? "Retired" : `Expires ${certificate.notAfter ? new Date(certificate.notAfter).toLocaleDateString() : "unknown"}`}</span>
                  </span><IconChevronRight className="size-4 shrink-0 text-muted-foreground" />
                </button>
                <CertificateDownload certificate={certificate} />
              </li>)}
            </ul>
          </CollapsibleContent>
        </Collapsible>}
      </div>
      {children.length > 0 && <div className="mb-4 ml-4 space-y-2 border-l-2 pl-2 sm:ml-6 sm:pl-4">
        {children.map((child) => <CaBranch key={child.id} {...props} ca={child} />)}
      </div>}
    </CollapsibleContent>
  </Collapsible>;
}

function ImportChainDialog({ tenantId, ca, onClose, onImported }: {
  tenantId: string; ca: IssuingSubca; onClose: () => void; onImported: () => Promise<unknown>;
}) {
  const [chain, setChain] = useState("");
  const upload = useMutation({
    mutationFn: () => importSubcaChain(tenantId, ca.id, parseChain(chain)),
    onSuccess: async () => { await onImported(); toast.success("CA chain imported"); onClose(); },
    onError: (error) => toast.error("Could not import CA chain", { description: error.message }),
  });
  return <Dialog open onOpenChange={(open) => { if (!open && !upload.isPending) onClose(); }}>
    <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
      <DialogHeader><DialogTitle>Import signed CA chain</DialogTitle><DialogDescription className="break-all">{ca.subjectDn}</DialogDescription></DialogHeader>
      <Field><FieldLabel htmlFor="ca-chain">PEM chain, issuing CA first</FieldLabel><textarea id="ca-chain" className="min-h-48 w-full rounded-md border bg-transparent p-3 font-mono text-xs" value={chain} disabled={upload.isPending} onChange={(event) => setChain(event.target.value)} /></Field>
      <p className="text-sm text-muted-foreground">Include this CA and every issuer through the root. Its certificate must match the registered CA key.</p>
      <div className="flex justify-end gap-2"><Button variant="outline" disabled={upload.isPending} onClick={onClose}>Cancel</Button><Button disabled={!chain.trim() || upload.isPending} onClick={() => upload.mutate()}>{upload.isPending ? "Importing…" : "Import chain"}</Button></div>
    </DialogContent>
  </Dialog>;
}

function DownloadChainDialog({ ca, leaves, onClose }: { ca: IssuingSubca; leaves: IssuedLeaf[]; onClose: () => void }) {
  const [leafId, setLeafId] = useState("");
  const leaf = leaves.find(({ certificate }) => certificate.id === leafId)?.certificate;
  const chain = leaf?.chain ?? ca.certificateChain;
  return <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
    <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
      <DialogHeader><DialogTitle>Download chain</DialogTitle><DialogDescription>PEM bundle ordered from the selected certificate to its root.</DialogDescription></DialogHeader>
      <Field><FieldLabel htmlFor="download-leaf">Include a leaf certificate</FieldLabel>
        <select id="download-leaf" className="h-9 w-full min-w-0 rounded-md border bg-transparent px-3" value={leafId} onChange={(event) => setLeafId(event.target.value)}>
          <option value="">CA chain only</option>
          {leaves.map(({ key, version, certificate }) => <option key={certificate.id} value={certificate.id}>
            {certificate.chainDetails?.[0]?.subjectDn ?? key.keyId} · {key.keyId} v{version.version} · {certificate.id}
          </option>)}
        </select>
      </Field>
      <CertificateChain chain={chain} anchor={leaf?.anchor ?? ca.anchor} details={leaf?.chainDetails ?? ca.chainDetails} endLabel={leaf ? "Leaf" : "Issuing CA"} />
      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="outline" onClick={onClose}>Close</Button>
        {leaf && <Button variant="outline" onClick={() => downloadPem(leaf.chain.slice(0, 1), `certificate-${leaf.id}.pem`)}>Leaf PEM only</Button>}
        <Button onClick={() => downloadPem(chain, `chain-${leaf?.id ?? ca.id}.pem`)}><IconDownload />Download PEM chain</Button>
      </div>
    </DialogContent>
  </Dialog>;
}

function DeleteChainDialog({ tenantId, ca, cas, onClose }: {
  tenantId: string; ca: IssuingSubca; cas: IssuingSubca[]; onClose: () => void;
}) {
  const client = useQueryClient();
  const branch = new Set([ca.id]);
  let size: number;
  do {
    size = branch.size;
    for (const candidate of cas) {
      if (candidate.parentCaId && branch.has(candidate.parentCaId)) branch.add(candidate.id);
    }
  } while (branch.size !== size);
  const remove = useMutation({
    mutationFn: () => deleteCaChain(tenantId, ca.id),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ["issuing-pki", tenantId] });
      toast.success("CA chain deleted");
      onClose();
    },
    onError: (error) => toast.error("Could not delete CA chain", { description: error.message }),
  });
  return <AlertDialog open onOpenChange={(open) => { if (!open && !remove.isPending) onClose(); }}>
    <AlertDialogContent className="max-h-[90vh] overflow-y-auto">
      <AlertDialogHeader><AlertDialogTitle>{ca.parentCaId ? "Delete CA branch?" : "Delete CA chain?"}</AlertDialogTitle>
        <AlertDialogDescription>Remove {branch.size} certificate {branch.size === 1 ? "authority" : "authorities"} from PKI management and stop issuing through them.</AlertDialogDescription>
      </AlertDialogHeader>
      <ul className="space-y-1 rounded-lg border p-3 text-sm">{cas.filter((candidate) => branch.has(candidate.id)).map((candidate) => <li key={candidate.id} className="break-all">{candidate.subjectDn}</li>)}</ul>
      <p className="text-sm text-muted-foreground">Issued certificates remain usable on their registered keys. Existing trust anchors and public certificate URLs are preserved. This does not revoke certificates.</p>
      <AlertDialogFooter>
        <AlertDialogCancel disabled={remove.isPending}>Cancel</AlertDialogCancel>
        <AlertDialogAction variant="destructive" disabled={remove.isPending} onClick={(event) => { event.preventDefault(); remove.mutate(); }}>{remove.isPending ? "Deleting…" : "Delete chain"}</AlertDialogAction>
      </AlertDialogFooter>
    </AlertDialogContent>
  </AlertDialog>;
}

function parseChain(input: string): string[] {
  const blocks = [...input.matchAll(/-----BEGIN CERTIFICATE-----([\s\S]*?)-----END CERTIFICATE-----/g)];
  if (blocks.length) return blocks.map((block) => (block[1] ?? "").replace(/\s/g, ""));
  return input.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
}
