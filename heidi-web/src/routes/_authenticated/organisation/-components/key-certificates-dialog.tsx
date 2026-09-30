// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { addKeyCertificate, type CertificateProfile, keyCapabilities, keyCsrDownloadUrl, type PlatformKey } from "@/lib/api/keys/api";
import { getSubcas, issueSubcaLeaf } from "@/lib/api/keys/issuing-pki";

const profiles: CertificateProfile[] = ["CREDENTIAL_SIGNING", "ACCESS", "STATUS_LIST"];
const expiryWarningDays = 30;
const dayMilliseconds = 86400000;

export function KeyCertificatesDialog({ tenantId, signingKey, onClose, initialProfile = "CREDENTIAL_SIGNING", initialTrustSystem = "EUDI" }: {
  tenantId?: string; signingKey: PlatformKey; onClose: () => void; initialProfile?: CertificateProfile; initialTrustSystem?: string;
}) {
  const queryClient = useQueryClient();
  const [versionId, setVersionId] = useState(signingKey.versions.find((version) => version.status === "PREPARED")?.id ?? signingKey.activeVersionId ?? "");
  const [profile, setProfile] = useState<CertificateProfile>(initialProfile);
  const [trustSystem, setTrustSystem] = useState(initialTrustSystem);
  const [chain, setChain] = useState("");
  const [subject, setSubject] = useState(`CN=${signingKey.keyId}`);
  const [dnsName, setDnsName] = useState("");
  const [leafName, setLeafName] = useState(signingKey.keyId);
  const [organisation, setOrganisation] = useState("");
  const [organisationId, setOrganisationId] = useState("");
  const [country, setCountry] = useState("CH");
  const [subcaId, setSubcaId] = useState("");
  const [leafProfile, setLeafProfile] = useState<"PID" | "EAA">("PID");
  const { data: subcas = [] } = useQuery({ queryKey: ["issuing-pki", tenantId], queryFn: () => getSubcas(tenantId), enabled: Boolean(tenantId) });
  const { data: capabilities } = useQuery({ queryKey: ["key-capabilities", tenantId], queryFn: () => keyCapabilities(tenantId) });
  const version = signingKey.versions.find((candidate) => candidate.id === versionId);
  const save = useMutation({
    mutationFn: (source: "import" | "development") => addKeyCertificate({
      tenantId, keyId: signingKey.keyId, versionId, profile, trustSystem: trustSystem || null,
      certificateChain: source === "import" ? parseChain(chain) : undefined,
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["platform-keys", tenantId ?? "platform"] });
      toast.success("Certificate added. Select it in the identity slot.");
      onClose();
    },
    onError: (error) => toast.error("Could not add certificate.", { description: error.message }),
  });
  const issue = useMutation({
    mutationFn: () => issueSubcaLeaf(tenantId, subcaId, signingKey.keyId, versionId,
      `CN=${dnValue(leafName)},O=${dnValue(organisation)},C=${country},2.5.4.97=${dnValue(organisationId)}`,
      leafProfile),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["platform-keys", tenantId ?? "platform"] });
      toast.success("Certificate created. Select it in the identity slot.");
      onClose();
    },
    onError: (error) => toast.error("Could not create certificate from SubCA.", { description: error.message }),
  });
  return <Dialog open onOpenChange={(open) => !open && onClose()}><DialogContent className="max-h-[90vh] overflow-y-auto">
    <DialogHeader><DialogTitle>Certificates · {signingKey.keyId}</DialogTitle><DialogDescription>Renew a certificate without replacing its key. Previous certificates remain available to active requests.</DialogDescription></DialogHeader>
    <Field><FieldLabel htmlFor="certificate-version">Key version</FieldLabel><select id="certificate-version" className="h-9 w-full rounded-md border bg-transparent px-3" value={versionId} onChange={(event) => setVersionId(event.target.value)}>{signingKey.versions.map((item) => <option key={item.id} value={item.id}>Version {item.version} · {item.status}</option>)}</select></Field>
    {version?.certificates?.map((certificate) => {
      const days = certificate.notAfter ? Math.ceil((Date.parse(certificate.notAfter) - Date.now()) / dayMilliseconds) : undefined;
      return <div key={certificate.id} className="rounded-md border p-3 text-sm"><p>{certificate.eudiLeafProfile ? `${certificate.eudiLeafProfile} · ` : ""}{certificate.profile.replaceAll("_", " ")} · {certificate.trustSystem ?? "Default"} · {certificate.source}</p><p className={days !== undefined && days <= expiryWarningDays ? "text-destructive" : "text-muted-foreground"}>{days === undefined ? "Validity not recorded" : days <= 0 ? "Expired" : `Expires in ${days} days`}</p></div>;
    })}
    <Field><FieldLabel htmlFor="certificate-profile">Certificate profile</FieldLabel><select id="certificate-profile" className="h-9 w-full rounded-md border bg-transparent px-3" value={profile} onChange={(event) => setProfile(event.target.value as CertificateProfile)}>{profiles.map((value) => <option key={value} value={value}>{value.replaceAll("_", " ")}</option>)}</select></Field>
    <details className="rounded-md border p-3"><summary>Request a production certificate</summary><div className="mt-3 space-y-3">
      <p className="text-sm text-muted-foreground">Download a CSR for your CA, then import its certificate chain below. The private key stays with the signing provider.</p>
      <Field><FieldLabel htmlFor="csr-subject">Subject (distinguished name)</FieldLabel><Input id="csr-subject" value={subject} onChange={(event) => setSubject(event.target.value)} /></Field>
      <Field><FieldLabel htmlFor="csr-dns">DNS name (optional)</FieldLabel><Input id="csr-dns" value={dnsName} onChange={(event) => setDnsName(event.target.value)} placeholder="issuer.example.org" /></Field>
      <Button variant="outline" asChild={!(!subject.trim() || !version || !["ACTIVE", "PREPARED"].includes(version.status))} disabled={!subject.trim() || !version || !["ACTIVE", "PREPARED"].includes(version.status)}>
        {!subject.trim() || !version || !["ACTIVE", "PREPARED"].includes(version.status)
          ? <span>Download CSR</span>
          : <a href={keyCsrDownloadUrl(tenantId, signingKey.keyId, versionId, subject, dnsName)} download={`${signingKey.keyId}-v${version.version}.csr.pem`}>Download CSR</a>}
      </Button>
    </div></details>
    {tenantId && profile === "CREDENTIAL_SIGNING" && trustSystem === "EUDI" && <details className="rounded-md border p-3"><summary>Create certificate from SubCA</summary><div className="mt-3 space-y-3">
      <p className="text-sm text-muted-foreground">Issue a leaf for this key version using an organisation SubCA. The SubCA key stays with the signing service.</p>
      <Field><FieldLabel htmlFor="leaf-subca">Issuing SubCA</FieldLabel><select id="leaf-subca" className="h-9 w-full rounded-md border bg-transparent px-3" value={subcaId} onChange={(event) => setSubcaId(event.target.value)}><option value="">Select SubCA</option>{subcas.filter((ca) => ca.certificateChain.length).map((ca) => <option key={ca.id} value={ca.id}>{ca.subjectDn} · {ca.certificateSource}</option>)}</select></Field>
      <Field><FieldLabel htmlFor="leaf-profile">Leaf profile</FieldLabel><select id="leaf-profile" className="h-9 w-full rounded-md border bg-transparent px-3" value={leafProfile} onChange={(event) => setLeafProfile(event.target.value as "PID" | "EAA")}><option value="PID">PID</option><option value="EAA">EAA</option></select></Field>
      <Field><FieldLabel htmlFor="leaf-name">Common name</FieldLabel><Input id="leaf-name" value={leafName} onChange={(event) => setLeafName(event.target.value)} /></Field>
      <Field><FieldLabel htmlFor="leaf-organisation">Organisation</FieldLabel><Input id="leaf-organisation" value={organisation} onChange={(event) => setOrganisation(event.target.value)} /></Field>
      <Field><FieldLabel htmlFor="leaf-organisation-id">Organisation identifier (ETSI)</FieldLabel><Input id="leaf-organisation-id" value={organisationId} onChange={(event) => setOrganisationId(event.target.value)} placeholder="VATDE-123456789" /></Field>
      <Field><FieldLabel htmlFor="leaf-country">Country code</FieldLabel><Input id="leaf-country" value={country} maxLength={2} onChange={(event) => setCountry(event.target.value.toUpperCase())} /></Field>
      <Button disabled={!subcaId || !versionId || !leafName.trim() || !organisation.trim() || !organisationId.trim() || !/^[A-Z]{2}$/.test(country) || issue.isPending} onClick={() => issue.mutate()}>Create certificate from SubCA</Button>
    </div></details>}
    <Field><FieldLabel htmlFor="certificate-trust">Trust system</FieldLabel><select id="certificate-trust" className="h-9 w-full rounded-md border bg-transparent px-3" value={trustSystem} onChange={(event) => setTrustSystem(event.target.value)}><option value="">Default</option><option value="EUDI">EUDI</option><option value="Switzerland">Switzerland</option><option value="Custom">Custom</option><option value="OIDF">OpenID Federation (OIDF)</option></select></Field>
    <Field><FieldLabel htmlFor="certificate-chain">Certificate chain (PEM or base64 DER, leaf first)</FieldLabel><textarea id="certificate-chain" className="min-h-32 rounded-md border p-2 font-mono text-xs" value={chain} onChange={(event) => setChain(event.target.value)} /></Field>
    <div className="flex flex-wrap justify-end gap-2"><Button variant="outline" onClick={onClose}>Cancel</Button><Button disabled={!chain.trim() || save.isPending} onClick={() => save.mutate("import")}>Import certificate</Button></div>
    {capabilities?.developmentCertificates && <div className="rounded-md border p-3"><p className="mb-3 text-sm text-muted-foreground">Local development only. This CA is not trusted by production wallets.</p><Button variant="outline" disabled={save.isPending} onClick={() => save.mutate("development")}>Create development certificate</Button></div>}
  </DialogContent></Dialog>;
}

function parseChain(input: string): string[] {
  const blocks = [...input.matchAll(/-----BEGIN CERTIFICATE-----([\s\S]*?)-----END CERTIFICATE-----/g)];
  if (blocks.length) return blocks.map((block) => (block[1] ?? "").replace(/\s/g, ""));
  return input.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
}

function dnValue(value: string) {
  return value.replace(/([,+=<>#;"\\])/g, "\\$1").trim();
}
