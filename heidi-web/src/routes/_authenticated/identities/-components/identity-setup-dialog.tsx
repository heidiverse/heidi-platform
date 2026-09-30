// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { useMemo, useState } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { saveIdentityKeySlot } from "@/lib/api/identity-key-slots/api";
import { createIssuerDefinition } from "@/lib/api/issuer-definitions/api";
import {
  addKeyCertificate,
  createKey,
  getKeys,
  keyCapabilities,
  type PlatformKey,
} from "@/lib/api/keys/api";
import { certificateOptions } from "@/lib/api/keys/key-options";
import { getSigningProviders } from "@/lib/api/signing-providers/api";
import { DEFAULT_LOCALE } from "@/lib/constants";
import { KeyCertificatesDialog } from "../../organisation/-components/key-certificates-dialog";
import {
  type EudiRole,
  eudiRoleSlot,
  eudiRoles,
  eudiSetupReady,
} from "./identity-setup";

type IntendedUse = "issuing" | "verification" | "both";
type SetupTrust = "EUDI" | "Switzerland";
type Step = 1 | 2 | 3 | 4;

const algorithm = "ES256";
const eudiFinalStep = 3;
const legacyFinalStep = 4;
const defaultRoles: EudiRole[] = ["issuer", "verifier"];

function profileName(trust: SetupTrust) {
  return trust === "EUDI"
    ? "identity.setup.trust.eudi"
    : "identity.setup.trust.switzerland";
}

function slug(name: string) {
  return name.trim().toLowerCase().replaceAll(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

function roleLabel(role: EudiRole) {
  return eudiRoles.find((candidate) => candidate.id === role)?.label ?? role;
}

export function IdentitySetupDialog({
  tenantId,
  fallbackLanguage = DEFAULT_LOCALE,
  children,
  onCreated,
}: {
  tenantId?: string;
  fallbackLanguage?: string;
  children: ReactNode;
  onCreated: (identityId: number) => void;
}) {
  const { $t, formatList } = useIntl();
  const queryClient = useQueryClient();
  const scopeKey = tenantId ?? "platform";
  const [open, setOpen] = useState(false);
  const [step, setStep] = useState<Step>(1);
  const [name, setName] = useState("");
  const [customProfileName, setCustomProfileName] = useState("");
  const [intendedUse, setIntendedUse] = useState<IntendedUse>("both");
  const [trust, setTrust] = useState<SetupTrust>("EUDI");
  const [roles, setRoles] = useState<EudiRole[]>(defaultRoles);
  const [accessCertificates, setAccessCertificates] = useState<Record<EudiRole, string>>({
    issuer: "",
    verifier: "",
  });
  const [credentialSigning, setCredentialSigning] = useState(true);
  const [saving, setSaving] = useState(false);
  const [creatingAccessKey, setCreatingAccessKey] = useState(false);
  const [certificateKey, setCertificateKey] = useState<PlatformKey | null>(null);

  const { data: providers = [] } = useQuery({
    queryKey: ["signing-providers", scopeKey],
    queryFn: () => getSigningProviders(tenantId),
    enabled: open,
  });
  const { data: capabilities } = useQuery({
    queryKey: ["key-capabilities", scopeKey],
    queryFn: () => keyCapabilities(tenantId),
    enabled: open,
  });
  const { data: keys = [] } = useQuery({
    queryKey: ["platform-keys", scopeKey],
    queryFn: () => getKeys(tenantId),
    enabled: open,
  });

  const provider = providers.find((candidate) => candidate.defaultProvider && candidate.canCreate)
    ?? providers.find((candidate) => candidate.canCreate);
  const selectedAlgorithm = provider?.supportedAlgorithms.includes(algorithm)
    ? algorithm
    : provider?.supportedAlgorithms[0];
  const accessOptions = useMemo(
    () => certificateOptions(keys, "ACCESS", "EUDI"),
    [keys],
  );
  const selectedAccess = roles.map((role) => accessCertificates[role]).filter(Boolean);
  const selectedAccessKeys = new Set(
    selectedAccess.flatMap((certificateId) => accessOptions
      .filter((option) => option.id === certificateId)
      .map((option) => option.keyId)),
  );
  const eudiReady = eudiSetupReady(roles, accessCertificates);
  const finalStep = trust === "EUDI" ? eudiFinalStep : legacyFinalStep;
  const createsCredentialKey = trust === "EUDI"
    ? roles.includes("issuer") && credentialSigning
    : intendedUse !== "verification";
  const needsProvider = trust !== "EUDI" || createsCredentialKey;

  function reset() {
    setStep(1);
    setName("");
    setCustomProfileName("");
    setIntendedUse("both");
    setTrust("EUDI");
    setRoles(defaultRoles);
    setAccessCertificates({ issuer: "", verifier: "" });
    setCredentialSigning(true);
    setSaving(false);
    setCreatingAccessKey(false);
    setCertificateKey(null);
  }

  function changeTrust(value: SetupTrust) {
    setTrust(value);
    setStep(1);
  }

  function toggleRole(role: EudiRole) {
    setRoles((current) => current.includes(role)
      ? current.filter((candidate) => candidate !== role)
      : [...current, role]);
  }

  function selectCertificate(role: EudiRole, certificateId: string) {
    setAccessCertificates((current) => ({ ...current, [role]: certificateId }));
  }

  async function createAccessKey() {
    if (!provider || !selectedAlgorithm) return;

    setCreatingAccessKey(true);
    try {
      const key = await createKey({
        tenantId,
        key: {
          keyId: `${slug(name) || "identity"}-access`,
          algorithm: selectedAlgorithm,
          usage: "SIGN",
          providerId: provider.id,
        },
      });
      let certificateId: string | undefined;
      if (capabilities?.developmentCertificates && key.activeVersionId) {
        const certificate = await addKeyCertificate({
          tenantId,
          keyId: key.keyId,
          versionId: key.activeVersionId,
          profile: "ACCESS",
          trustSystem: "EUDI",
        });
        if ("certificateId" in certificate) certificateId = certificate.certificateId;
      }
      await queryClient.invalidateQueries({ queryKey: ["platform-keys", scopeKey] });
      if (certificateId) {
        setAccessCertificates((current) => ({
          issuer: roles.includes("issuer") ? certificateId : current.issuer,
          verifier: roles.includes("verifier") ? certificateId : current.verifier,
        }));
        toast.success($t({ id: "identity.setup.toast.accessCreated" }));
      } else {
        setCertificateKey(key);
        toast.success($t({ id: "identity.setup.toast.keyCreated" }));
      }
    } catch (error) {
      toast.error($t({ id: "identity.setup.error.accessKey" }), {
        description: error instanceof Error ? error.message : undefined,
      });
    } finally {
      setCreatingAccessKey(false);
    }
  }

  async function provisionCredentialKey(identityId: number, identitySlug: string) {
    if (!provider || !selectedAlgorithm) {
      throw new Error($t({ id: "identity.setup.error.noProvider" }));
    }

    const key = await createKey({
      tenantId,
      key: {
        keyId: `${identitySlug}-credentials`,
        algorithm: selectedAlgorithm,
        usage: "SIGN",
        providerId: provider.id,
      },
    });
    if (!key.activeVersionId || trust !== "EUDI" || !capabilities?.developmentCertificates) return false;

    const certificate = await addKeyCertificate({
      tenantId,
      keyId: key.keyId,
      versionId: key.activeVersionId,
      profile: "CREDENTIAL_SIGNING",
      trustSystem: trust,
    });
    if (!("certificateId" in certificate)) return false;

    await saveIdentityKeySlot({
      tenantId,
      identityId,
      slot: {
        type: "CREDENTIAL_SIGNING",
        trustSystem: trust,
        keyId: key.id,
        certificateId: certificate.certificateId,
        order: 0,
      },
    });
    return true;
  }

  async function provisionLegacyKey(identityId: number, identitySlug: string, role: "credentials" | "identity") {
    if (!provider || !selectedAlgorithm) {
      throw new Error($t({ id: "identity.setup.error.noProvider" }));
    }

    const key = await createKey({
      tenantId,
      key: {
        keyId: `${identitySlug}-${role}`,
        algorithm: selectedAlgorithm,
        usage: "SIGN",
        providerId: provider.id,
      },
    });
    if (!key.activeVersionId || trust !== "EUDI" || !capabilities?.developmentCertificates) return false;

    const certificate = await addKeyCertificate({
      tenantId,
      keyId: key.keyId,
      versionId: key.activeVersionId,
      profile: role === "credentials" ? "CREDENTIAL_SIGNING" : "ACCESS",
      trustSystem: trust,
    });
    if (!("certificateId" in certificate)) return false;

    await saveIdentityKeySlot({
      tenantId,
      identityId,
      slot: {
        type: role === "credentials" ? "CREDENTIAL_SIGNING" : "IDENTITY_STATEMENT",
        trustSystem: trust,
        keyId: key.id,
        certificateId: certificate.certificateId,
        order: 0,
      },
    });
    return true;
  }

  async function create() {
    if (trust === "EUDI" && !eudiReady) return;

    setSaving(true);
    let identityId: number | undefined;
    try {
      const identity = await createIssuerDefinition({
        tenantId,
        logo: "",
        slug: "",
        displayName: { [fallbackLanguage]: name.trim() },
        defaultTrustSystem: trust,
        trustSystems: [trust],
        customProfileName: customProfileName.trim() || null,
      });
      identityId = identity.id;

      let complete = true;
      if (trust === "EUDI") {
        await Promise.all(roles.map(async (role) => {
          const certificateId = accessCertificates[role];
          const option = accessOptions.find((candidate) => candidate.id === certificateId);
          if (!option) {
            throw new Error($t(
              { id: "identity.setup.error.selectCertificate" },
              { role: $t({ id: roleLabel(role) }).toLowerCase() },
            ));
          }
          await saveIdentityKeySlot({
            tenantId,
            identityId: identity.id,
            slot: {
              type: eudiRoleSlot(role),
              trustSystem: "EUDI",
              keyId: option.keyId,
              certificateId,
              order: 0,
            },
          });
        }));
        if (createsCredentialKey) complete = await provisionCredentialKey(identity.id, identity.slug);
      } else {
        const assignments = [];
        if (createsCredentialKey) assignments.push(await provisionLegacyKey(identity.id, identity.slug, "credentials"));
        assignments.push(await provisionLegacyKey(identity.id, identity.slug, "identity"));
        complete = assignments.every(Boolean);
      }

      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["issuer-definitions"] }),
        tenantId ? queryClient.invalidateQueries({ queryKey: ["settings", tenantId] }) : Promise.resolve(),
        queryClient.invalidateQueries({ queryKey: ["platform-keys", scopeKey] }),
      ]);
      toast.success($t({
        id: complete
          ? "identity.setup.toast.created"
          : "identity.setup.toast.incomplete",
      }));
      setOpen(false);
      reset();
      onCreated(identity.id);
    } catch (error) {
      toast.error($t({
        id: identityId
          ? "identity.setup.error.incomplete"
          : "identity.setup.error.create",
      }), {
        description: error instanceof Error ? error.message : undefined,
      });
      if (identityId) {
        setOpen(false);
        onCreated(identityId);
      }
    } finally {
      setSaving(false);
    }
  }

  function continueStep() {
    setStep((current) => Math.min(finalStep, current + 1) as Step);
  }

  function previousStep() {
    setStep((current) => Math.max(1, current - 1) as Step);
  }

  const canContinue = step === 1
    ? Boolean(name.trim()) && (trust !== "EUDI" || roles.length > 0)
    : step === 2 && trust === "EUDI"
      ? eudiReady
      : true;

  return (
    <Dialog open={open} onOpenChange={(next) => { setOpen(next); if (!next) reset(); }}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle><FormattedMessage id="identity.setup.title" /></DialogTitle>
          <DialogDescription>
            <FormattedMessage
              id="identity.setup.stepDescription"
              values={{ step, finalStep }}
            />
          </DialogDescription>
        </DialogHeader>

        {step === 1 && <div className="space-y-4">
          <Field>
            <FieldLabel htmlFor="identity-setup-name"><FormattedMessage id="identity.setup.name" /></FieldLabel>
            <Input id="identity-setup-name" autoFocus value={name} onChange={(event) => setName(event.target.value)} />
          </Field>
          <Field>
            <FieldLabel htmlFor="identity-setup-custom-profile-name"><FormattedMessage id="identity.setup.customProfileName" /></FieldLabel>
            <Input id="identity-setup-custom-profile-name" value={customProfileName} onChange={(event) => setCustomProfileName(event.target.value)} />
            <FieldDescription><FormattedMessage id="identity.setup.customProfileName.description" /></FieldDescription>
          </Field>
          <Field>
            <FieldLabel htmlFor="identity-setup-trust"><FormattedMessage id="identity.setup.trustSystem" /></FieldLabel>
            <select id="identity-setup-trust" className="h-9 w-full rounded-md border bg-transparent px-3 text-sm" value={trust} onChange={(event) => changeTrust(event.target.value as SetupTrust)}>
              <option value="EUDI"><FormattedMessage id="identity.setup.trust.eudi" /></option>
              <option value="Switzerland"><FormattedMessage id="identity.setup.trust.switzerland" /></option>
            </select>
          </Field>
          {trust === "EUDI" ? <>
            <div>
              <p className="text-sm font-medium"><FormattedMessage id="identity.setup.roles.title" /></p>
              <p className="mt-1 text-sm text-muted-foreground"><FormattedMessage id="identity.setup.roles.description" /></p>
            </div>
            <div className="space-y-3">
              {eudiRoles.map((role) => <label key={role.id} className="flex cursor-pointer gap-3 rounded-md border p-3 has-checked:border-primary has-checked:bg-primary/5">
                <input className="mt-1 size-4 accent-primary" type="checkbox" checked={roles.includes(role.id)} onChange={() => toggleRole(role.id)} />
                <span className="space-y-1"><span className="block font-medium"><FormattedMessage id={role.label} /></span><span className="block text-sm text-muted-foreground"><FormattedMessage id={role.technical} /></span></span>
              </label>)}
            </div>
          </> : <p className="text-sm text-muted-foreground"><FormattedMessage id="identity.setup.usage.continue" /></p>}
        </div>}

        {step === 2 && trust === "EUDI" && <div className="space-y-4">
          <div>
            <p className="font-medium"><FormattedMessage id="identity.setup.certificates.title" /></p>
            <p className="mt-1 text-sm text-muted-foreground"><FormattedMessage id="identity.setup.certificates.description" /></p>
          </div>
          {roles.map((role) => <Field key={role}>
            <FieldLabel htmlFor={`identity-setup-${role}-certificate`}><FormattedMessage id={roleLabel(role)} /></FieldLabel>
            <select id={`identity-setup-${role}-certificate`} className="h-9 w-full rounded-md border bg-transparent px-3 text-sm" value={accessCertificates[role]} onChange={(event) => selectCertificate(role, event.target.value)}>
              <option value=""><FormattedMessage id="identity.setup.certificates.choose" /></option>
              {accessOptions.map((option) => <option key={option.id} value={option.id}>{option.label}</option>)}
            </select>
            <FieldDescription><FormattedMessage id="identity.setup.certificates.valid" /></FieldDescription>
          </Field>)}
          <div className="rounded-md border p-3">
            <label className="flex cursor-pointer gap-3">
              <input className="mt-1 size-4 accent-primary" type="checkbox" checked={credentialSigning} onChange={(event) => setCredentialSigning(event.target.checked)} disabled={!roles.includes("issuer")} />
              <span className="space-y-1"><span className="block font-medium"><FormattedMessage id="identity.setup.credentialSigning.label" /></span><span className="block text-sm text-muted-foreground"><FormattedMessage id="identity.setup.credentialSigning.description" /></span></span>
            </label>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" disabled={!provider || !selectedAlgorithm || creatingAccessKey || !name.trim()} onClick={createAccessKey}>
              <FormattedMessage id={creatingAccessKey ? "identity.setup.certificates.creating" : "identity.setup.certificates.create"} />
            </Button>
            {accessOptions.length === 0 && <p className="self-center text-sm text-muted-foreground"><FormattedMessage id="identity.setup.certificates.empty" /></p>}
          </div>
          <Field>
            <FieldLabel htmlFor="identity-setup-certificate-key"><FormattedMessage id="identity.setup.certificates.import" /></FieldLabel>
            <select id="identity-setup-certificate-key" className="h-9 w-full rounded-md border bg-transparent px-3 text-sm" value={certificateKey?.id ?? ""} onChange={(event) => setCertificateKey(keys.find((key) => key.id === event.target.value) ?? null)}>
              <option value=""><FormattedMessage id="identity.setup.certificates.chooseKey" /></option>
              {keys.filter((key) => key.versions.some((version) => version.usages.includes("SIGN"))).map((key) => <option key={key.id} value={key.id}>{key.keyId}</option>)}
            </select>
            <FieldDescription><FormattedMessage id="identity.setup.certificates.importDescription" /></FieldDescription>
          </Field>
          {selectedAccessKeys.size > 0 && <p className="text-sm text-muted-foreground"><FormattedMessage id="identity.setup.certificates.selected" values={{ count: selectedAccessKeys.size }} /></p>}
        </div>}

        {step === 3 && trust === "EUDI" && <div className="space-y-3 rounded-md border p-4 text-sm">
          <p><span className="text-muted-foreground"><FormattedMessage id="identity.setup.summary.identity" /></span> · {name}</p>
          <p><span className="text-muted-foreground"><FormattedMessage id="identity.setup.summary.trust" /></span> · <FormattedMessage id={profileName(trust)} /></p>
          <p><span className="text-muted-foreground"><FormattedMessage id="identity.setup.summary.roles" /></span> · {formatList(roles.map((role) => $t({ id: roleLabel(role) })))}</p>
          {roles.map((role) => <p key={role}><span className="text-muted-foreground"><FormattedMessage id={roleLabel(role)} /></span> · <FormattedMessage id="identity.setup.summary.accessCertificate" values={{ value: accessCertificates[role].slice(0, 8) }} /></p>)}
          <p><span className="text-muted-foreground"><FormattedMessage id="identity.setup.summary.credentialSigning" /></span> · <FormattedMessage id={credentialSigning && roles.includes("issuer") ? "identity.setup.summary.createCredentialKey" : "identity.setup.summary.notConfigured"} /></p>
          <p className="text-muted-foreground"><FormattedMessage id="identity.setup.summary.eudiDescription" /></p>
        </div>}

        {trust !== "EUDI" && step === 2 && <Field>
          <FieldLabel htmlFor="identity-setup-use"><FormattedMessage id="identity.setup.use" /></FieldLabel>
          <select id="identity-setup-use" className="h-9 w-full rounded-md border bg-transparent px-3 text-sm" value={intendedUse} onChange={(event) => setIntendedUse(event.target.value as IntendedUse)}>
            <option value="issuing"><FormattedMessage id="identity.setup.use.issuing" /></option>
            <option value="verification"><FormattedMessage id="identity.setup.use.verification" /></option>
            <option value="both"><FormattedMessage id="identity.setup.use.both" /></option>
          </select>
          <FieldDescription><FormattedMessage id="identity.setup.use.description" /></FieldDescription>
        </Field>}
        {trust !== "EUDI" && step === 3 && <div className="space-y-4">
          <Field>
            <FieldLabel><FormattedMessage id="identity.setup.profile" /></FieldLabel>
            <p className="rounded-md border p-3 text-sm"><FormattedMessage id={profileName(trust)} /></p>
          </Field>
        </div>}
        {trust !== "EUDI" && step === 4 && <div className="space-y-3 rounded-md border p-4 text-sm">
          <p><span className="text-muted-foreground"><FormattedMessage id="identity.setup.summary.identity" /></span> · {name}</p>
          <p><span className="text-muted-foreground"><FormattedMessage id="identity.setup.use" /></span> · <FormattedMessage id={`identity.setup.use.${intendedUse}`} /></p>
          <p><span className="text-muted-foreground"><FormattedMessage id="identity.setup.summary.trust" /></span> · <FormattedMessage id={profileName(trust)} /></p>
          <p><span className="text-muted-foreground"><FormattedMessage id="identity.setup.summary.provider" /></span> · {provider?.name ?? $t({ id: "identity.setup.provider.none" })}</p>
          {createsCredentialKey && <p><span className="text-muted-foreground"><FormattedMessage id="identity.setup.summary.credentialSigning" /></span> · <FormattedMessage id="identity.setup.summary.createKey" values={{ value: `${slug(name)}-credentials` }} /></p>}
          <p><span className="text-muted-foreground"><FormattedMessage id="identity.setup.summary.identitySigning" /></span> · <FormattedMessage id="identity.setup.summary.createKey" values={{ value: `${slug(name)}-identity` }} /></p>
          <p className="text-muted-foreground"><FormattedMessage id={trust === "Switzerland" ? "identity.setup.summary.switzerlandDescription" : "identity.setup.summary.eudiLegacyDescription"} /></p>
        </div>}

        <DialogFooter>
          {step > 1 && <Button variant="outline" disabled={saving} onClick={previousStep}><FormattedMessage id="common.back" /></Button>}
          {step < finalStep
            ? <Button disabled={!canContinue} onClick={continueStep}><FormattedMessage id="common.continue" /></Button>
            : <Button disabled={saving || (needsProvider && (!provider || !selectedAlgorithm)) || (trust === "EUDI" && !eudiReady)} onClick={create}><FormattedMessage id={saving ? "identity.setup.creating" : "identity.setup.create"} /></Button>}
        </DialogFooter>
      </DialogContent>
      {certificateKey && <KeyCertificatesDialog
        tenantId={tenantId}
        signingKey={certificateKey}
        initialProfile="ACCESS"
        initialTrustSystem="EUDI"
        onClose={() => {
          setCertificateKey(null);
          void queryClient.invalidateQueries({ queryKey: ["platform-keys", scopeKey] });
        }}
      />}
    </Dialog>
  );
}
