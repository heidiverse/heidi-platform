// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconKey, IconPlus, IconRefresh, IconTrash } from "@tabler/icons-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import type { IssuerDefinition } from "@/lib/api/issuer-definitions/api";
import {
  type ActivationSlot,
  activateKeyVersion,
  createKey,
  deleteKey,
  getActivationSlots,
  getKeys,
  type KeyUsage,
  type PlatformKey,
  type PlatformKeyVersion,
  prepareKeyRotation,
} from "@/lib/api/keys/api";
import {
  fileAsBase64,
  type PrivateKeyFormat,
  privateKeyFormats,
} from "@/lib/api/keys/import";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";
import { getSigningProviders, type SigningProvider } from "@/lib/api/signing-providers/api";
import { DEFAULT_LOCALE } from "@/lib/constants";
import { getLocalizedValue } from "@/lib/utils/localized";
import { KeyCertificatesDialog } from "./key-certificates-dialog";
import { KeyRotationDialog } from "./key-rotation-dialog";
import { KeyVersionsDialog } from "./key-versions-dialog";

function activeVersion(key: PlatformKey) {
  return key.versions.find((version) => version.id === key.activeVersionId)
    ?? key.versions.find((version) => version.status === "ACTIVE");
}

export function KeyManagementCard({ tenantId, identities }: { tenantId?: string; identities: IssuerDefinition[] }) {
  const queryClient = useQueryClient();
  const scopeKey = tenantId ?? "platform";
  const queryKey = ["platform-keys", scopeKey];
  const { data: keys = [], isPending } = useQuery({
    queryKey,
    queryFn: () => getKeys(tenantId),
  });
  const { data: providers = [] } = useQuery({
    queryKey: ["signing-providers", scopeKey],
    queryFn: () => getSigningProviders(tenantId),
  });
  const { data: settings } = useQuery({
    ...settingsForOrganisationOptions(tenantId ?? ""),
    enabled: Boolean(tenantId),
  });
  const [open, setOpen] = useState(false);
  const [versionsKeyId, setVersionsKeyId] = useState<string | null>(null);
  const versionsKey = keys.find((key) => key.id === versionsKeyId);
  const [activation, setActivation] = useState<{ key: PlatformKey; version: PlatformKeyVersion; slots: ActivationSlot[] } | null>(null);
  const [certificatesBySlot, setCertificatesBySlot] = useState<Record<string, string>>({});
  const [rotationKey, setRotationKey] = useState<PlatformKey | null>(null);
  const [certificateKey, setCertificateKey] = useState<PlatformKey | null>(null);
  const [keyId, setKeyId] = useState("");
  const [algorithm, setAlgorithm] = useState("ES256");
  const [usage, setUsage] = useState<KeyUsage>("SIGN");
  const [importOpen, setImportOpen] = useState(false);
  const [privateKeyFormat, setPrivateKeyFormat] =
    useState<PrivateKeyFormat>("PKCS12");
  const [privateKey, setPrivateKey] = useState("");
  const [privateKeyPassword, setPrivateKeyPassword] = useState("");
  const [privateKeyFile, setPrivateKeyFile] = useState<File | null>(null);
  const [providerId, setProviderId] = useState<number | "">("");
  const invalidate = () => queryClient.invalidateQueries({ queryKey });
  const create = useMutation({
    mutationFn: async () => {
      const material = privateKeyFormat === "PKCS12"
        ? privateKeyFile && await fileAsBase64(privateKeyFile)
        : privateKey.trim();
      return createKey({
        tenantId,
        key: {
          keyId,
          algorithm,
          usage,
          providerId: providerId || undefined,
          privateKeyFormat: importOpen ? privateKeyFormat : undefined,
          privateKey: importOpen ? material || undefined : undefined,
          privateKeyPassword:
            importOpen && privateKeyFormat === "PKCS12"
              ? privateKeyPassword || undefined
              : undefined,
        },
      });
    },
    onSuccess: async () => {
      await invalidate();
      setOpen(false);
      setKeyId("");
      setImportOpen(false);
      setPrivateKey("");
      setPrivateKeyPassword("");
      setPrivateKeyFile(null);
      void queryClient.invalidateQueries({ queryKey: ["identity-key-slots", scopeKey] });
      toast.success("Key created.");
    },
    onError: (error) => toast.error("Could not create key.", { description: error.message }),
  });
  const rotate = useMutation({
    mutationFn: (logicalKeyId: string) => prepareKeyRotation({ tenantId, keyId: logicalKeyId }),
    onSuccess: invalidate,
    onError: (error) => toast.error("Could not prepare key rotation.", { description: error.message }),
  });
  const activate = useMutation({
    mutationFn: ({ logicalKeyId, versionId, certificatesBySlot }: { logicalKeyId: string; versionId: string; certificatesBySlot?: Record<string, string> }) =>
      activateKeyVersion({ tenantId, keyId: logicalKeyId, versionId, certificatesBySlot }),
    onSuccess: async () => {
      setActivation(null);
      await invalidate();
      await queryClient.invalidateQueries({ queryKey: ["identity-key-slots", scopeKey] });
    },
    onError: (error) => toast.error("Could not activate key version.", { description: error.message }),
  });
  const checkActivation = useMutation({
    mutationFn: async ({ key, version }: { key: PlatformKey; version: PlatformKeyVersion }) => ({
      key, version, slots: await getActivationSlots(tenantId, key.keyId, version.id),
    }),
    onSuccess: (result) => {
      if (result.slots.every((slot) => slot.certificates.length === 1 || (!slot.required && slot.certificates.length === 0))) {
        activate.mutate({ logicalKeyId: result.key.keyId, versionId: result.version.id });
        return;
      }
      setCertificatesBySlot({});
      setActivation(result);
    },
    onError: (error) => toast.error("Could not check key version.", { description: error.message }),
  });
  const remove = useMutation({
    mutationFn: (logicalKeyId: string) => deleteKey({ tenantId, keyId: logicalKeyId }),
    onSuccess: invalidate,
    onError: (error) => toast.error("Could not delete key.", { description: error.message }),
  });

  return (
    <Card className="mt-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-2xl font-semibold">Keys</h2>
          <p className="mt-1.5 text-muted-foreground">
            Create and rotate keys held by a signing provider. Assign a key to an identity slot below.
          </p>
        </div>
        <Button onClick={() => setOpen(true)}><IconPlus /> Add key</Button>
      </div>
      <div className="mt-6 space-y-3">
        {isPending && <p className="text-sm text-muted-foreground">Loading keys…</p>}
        {!isPending && keys.length === 0 && (
          <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">No keys yet.</p>
        )}
        {keys.map((key) => {
          const active = activeVersion(key);
          const prepared = key.versions.find((version) => version.status === "PREPARED");
          return (
            <div key={key.id} className="flex flex-wrap items-center gap-3 rounded-xl border p-4">
              <IconKey className="size-5" />
              <div className="min-w-48 flex-1">
                <p className="font-semibold">{key.keyId}</p>
                <p className="text-sm text-muted-foreground">
                  {active?.algorithm ?? "unknown algorithm"} · version {active?.version ?? "—"} · {key.versions.length} version{key.versions.length === 1 ? "" : "s"} · {key.rotationPolicy.mode.toLowerCase()} rotation
                </p>
              </div>
              <Button variant="outline" onClick={() => setRotationKey(key)}>Rotation policy</Button>
              <Button variant="outline" onClick={() => setVersionsKeyId(key.id)}>Versions</Button>
              {key.versions.some((version) => version.usages.includes("SIGN")) && <Button variant="outline" onClick={() => setCertificateKey(key)}>Certificates</Button>}
              {prepared && (
                <Button
                  variant="outline"
                  disabled={activate.isPending || checkActivation.isPending}
                  onClick={() => checkActivation.mutate({ key, version: prepared })}
                >Activate v{prepared.version}</Button>
              )}
              {key.rotationPolicy.mode !== "EXTERNAL" && <Button
                variant="ghost"
                size="icon"
                aria-label={`Prepare rotation for ${key.keyId}`}
                disabled={rotate.isPending || Boolean(prepared)}
                onClick={() => rotate.mutate(key.keyId)}
              ><IconRefresh className={rotate.isPending ? "animate-spin" : undefined} /></Button>}
              <Button
                variant="ghost"
                size="icon"
                aria-label={`Delete ${key.keyId}`}
                disabled={remove.isPending}
                onClick={() => remove.mutate(key.keyId)}
              ><IconTrash /></Button>
            </div>
          );
        })}
      </div>
      {rotationKey && <KeyRotationDialog tenantId={tenantId} signingKey={rotationKey} onClose={() => setRotationKey(null)} />}
      {versionsKey && <KeyVersionsDialog tenantId={tenantId} signingKey={versionsKey} onClose={() => setVersionsKeyId(null)} />}
      {certificateKey && <KeyCertificatesDialog tenantId={tenantId} signingKey={certificateKey} onClose={() => setCertificateKey(null)} />}
      <Dialog open={activation !== null} onOpenChange={(open) => { if (!open) setActivation(null); }}>
        <DialogContent>
          <DialogHeader><DialogTitle>Activate {activation?.key.keyId}</DialogTitle><DialogDescription>Select certificates for the prepared version. Existing requests keep their original certificates.</DialogDescription></DialogHeader>
          {activation?.slots.filter((slot) => slot.certificates.length !== 1 && (slot.required || slot.certificates.length > 1)).map((slot) => (
            <Field key={slot.slotId}>
              <FieldLabel htmlFor={`activation-${slot.slotId}`}>
                {(() => {
                  const identity = identities.find((candidate) => candidate.id === slot.identityId);
                  return identity
                    ? getLocalizedValue(
                        identity.displayName,
                        settings?.defaultLanguage ?? DEFAULT_LOCALE,
                      ) ?? identity.slug
                    : slot.identityId;
                })()} · {slot.trustSystem ?? "Default"} · {slot.profile.replaceAll("_", " ")}
              </FieldLabel>
              {slot.certificates.length === 0 ? <p className="text-sm text-destructive">Add a valid {slot.profile} certificate before activation.</p> : (
                <select id={`activation-${slot.slotId}`} className="h-9 w-full rounded-md border bg-transparent px-3 text-sm" value={certificatesBySlot[slot.slotId] ?? ""} onChange={(event) => setCertificatesBySlot((current) => ({ ...current, [slot.slotId]: event.target.value }))}>
                  <option value="">Choose a certificate</option>
                  {slot.certificates.map((id) => {
                    const certificate = activation.version.certificates.find((candidate) => candidate.id === id);
                    return <option key={id} value={id}>{certificate?.eudiLeafProfile ? `${certificate.eudiLeafProfile} · ` : ""}{certificate?.source} · expires {certificate?.notAfter?.slice(0, 10)} · {id.slice(0, 8)}</option>;
                  })}
                </select>
              )}
            </Field>
          ))}
          <DialogFooter><Button disabled={activate.isPending || !activation || activation.slots.some((slot) => (slot.required && slot.certificates.length === 0) || (slot.certificates.length > 1 && !certificatesBySlot[slot.slotId]))} onClick={() => { if (activation) activate.mutate({ logicalKeyId: activation.key.keyId, versionId: activation.version.id, certificatesBySlot }); }}>Activate</Button></DialogFooter>
        </DialogContent>
      </Dialog>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add key</DialogTitle>
            <DialogDescription>Create a neutral key. Its role comes from the identity slot that uses it.</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <Field><FieldLabel>Key ID</FieldLabel><Input placeholder="acme-issuer" value={keyId} onChange={(event) => setKeyId(event.target.value)} /></Field>
            <Field><FieldLabel htmlFor="key-usage">Cryptographic usage</FieldLabel><select id="key-usage" className="h-9 w-full rounded-md border bg-transparent px-3 text-sm" value={usage} onChange={(event) => { const value = event.target.value as KeyUsage; setUsage(value); setAlgorithm(value === "UNWRAP" ? "RS256" : "ES256"); }}><option value="SIGN">Signing</option><option value="KEY_AGREEMENT">Request decryption (ECDH)</option><option value="UNWRAP">Request decryption (RSA)</option></select></Field>
            <Field><FieldLabel>Algorithm</FieldLabel><Input value={algorithm} onChange={(event) => setAlgorithm(event.target.value)} /></Field>
            <Field><FieldLabel>Provider</FieldLabel><select className="h-9 w-full rounded-md border bg-transparent px-3 text-sm" value={providerId} onChange={(event) => setProviderId(event.target.value ? Number(event.target.value) : "")}><option value="">Default provider</option>{providers.map((provider: SigningProvider) => <option key={provider.id} value={provider.id}>{provider.name}</option>)}</select></Field>
            <details
              open={importOpen}
              onToggle={(event) => setImportOpen(event.currentTarget.open)}
            >
              <summary className="cursor-pointer text-sm">Import an existing key</summary>
              <div className="mt-3 space-y-3">
                <Field>
                  <FieldLabel htmlFor="private-key-format">Import format</FieldLabel>
                  <select
                    id="private-key-format"
                    className="h-9 w-full rounded-md border bg-transparent px-3 text-sm"
                    value={privateKeyFormat}
                    onChange={(event) => {
                      setPrivateKeyFormat(event.target.value as PrivateKeyFormat);
                      setPrivateKey("");
                      setPrivateKeyPassword("");
                      setPrivateKeyFile(null);
                    }}
                  >
                    {privateKeyFormats.map((format) => (
                      <option key={format.value} value={format.value}>{format.label}</option>
                    ))}
                  </select>
                </Field>
                {privateKeyFormat === "PKCS12" ? (
                  <>
                    <Field>
                      <FieldLabel htmlFor="private-key-file">PKCS#12 private key</FieldLabel>
                      <Input
                        id="private-key-file"
                        type="file"
                        accept=".p12,.pfx,application/x-pkcs12"
                        onChange={(event) => setPrivateKeyFile(event.target.files?.[0] ?? null)}
                      />
                    </Field>
                    <Field>
                      <FieldLabel htmlFor="private-key-password">PKCS#12 password</FieldLabel>
                      <Input
                        id="private-key-password"
                        type="password"
                        autoComplete="off"
                        value={privateKeyPassword}
                        onChange={(event) => setPrivateKeyPassword(event.target.value)}
                      />
                    </Field>
                  </>
                ) : (
                  <Field>
                    <FieldLabel htmlFor="private-key-material">
                      {privateKeyFormat === "PEM"
                        ? "Private PEM"
                        : privateKeyFormat === "JWKS"
                          ? "Private JWK Set"
                          : "Private JWK"}
                    </FieldLabel>
                    <textarea
                      id="private-key-material"
                      className="min-h-28 w-full rounded-md border p-2 font-mono text-xs"
                      value={privateKey}
                      onChange={(event) => setPrivateKey(event.target.value)}
                      placeholder={privateKeyFormat === "PEM"
                        ? "-----BEGIN PRIVATE KEY-----"
                        : privateKeyFormat === "JWKS"
                          ? '{"keys":[...]}'
                          : '{"kty":"EC", ...}'}
                      spellCheck={false}
                      autoComplete="off"
                    />
                  </Field>
                )}
                <p className="text-sm text-muted-foreground">
                  The private key is sent directly to the selected signing provider. Add certificates separately after import.
                </p>
              </div>
            </details>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
            <Button
              disabled={
                !keyId.trim()
                || !algorithm.trim()
                || create.isPending
                || (importOpen && privateKeyFormat === "PKCS12" && !privateKeyFile)
                || (importOpen && privateKeyFormat !== "PKCS12" && !privateKey.trim())
              }
              onClick={() => create.mutate()}
            >{create.isPending ? "Creating…" : "Create key"}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
