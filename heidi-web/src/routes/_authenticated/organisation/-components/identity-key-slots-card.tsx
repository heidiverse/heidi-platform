// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconKey, IconPlus, IconTrash } from "@tabler/icons-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
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
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import type { IdentityKeySlotConsumer } from "@/lib/api/identity-key-slots/api";
import {
  deleteIdentityKeySlot,
  getIdentityKeySlots,
  type IdentityKeySlot,
  type IdentityKeySlotRequest,
  type IdentityKeySlotType,
  saveIdentityKeySlot,
} from "@/lib/api/identity-key-slots/api";
import type { IssuerDefinition } from "@/lib/api/issuer-definitions/api";
import { getKeys, type PlatformKey } from "@/lib/api/keys/api";
import { certificateOptions, keyOptions } from "@/lib/api/keys/key-options";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";
import {
  getSigningProviders,
  type SigningProvider,
} from "@/lib/api/signing-providers/api";
import { DEFAULT_LOCALE } from "@/lib/constants";
import { getLocalizedValue } from "@/lib/utils/localized";
import type { ConfiguredTrustSystem, TrustSystem } from "@/types/trust-system";
import {
  type AssignmentRow,
  advancedTypes,
  assignedKeyIds,
  certificateSourceNote,
  expectedProfile,
  hasDevelopmentCertificate,
  requiredMaterial,
  rowsFor,
  showTrustContext,
} from "./identity-key-slot-assignments";
import { KeyCertificatesDialog } from "./key-certificates-dialog";
import { SlotKeyCreate } from "./slot-key-create";

const slotTypes: IdentityKeySlotType[] = [
  "CREDENTIAL_SIGNING",
  "IDENTITY_STATEMENT",
  "PRESENTATION_SIGNING",
  "DECRYPTION",
  "OPERATION",
  "FEDERATION",
  "STATUS_LIST",
];

const defaultOperation = "w3c.bbs-data-integrity-presentation-setup";

const slotLabels: Record<IdentityKeySlotType, string> = {
  CREDENTIAL_SIGNING: "Credential signing",
  IDENTITY_STATEMENT: "Issuer identity",
  PRESENTATION_SIGNING: "Verifier identity",
  DECRYPTION: "Request decryption",
  OPERATION: "Specialised operation",
  FEDERATION: "Federation identity",
  STATUS_LIST: "Status-list signing",
};

const slotPurposes: Record<IdentityKeySlotType, string> = {
  CREDENTIAL_SIGNING: "Signs credentials issued to wallets",
  IDENTITY_STATEMENT: "Signs Credential Issuer Metadata",
  PRESENTATION_SIGNING: "Signs OID4VP authorization requests",
  DECRYPTION: "Decrypts incoming requests",
  OPERATION: "Signs a specialised provider operation",
  FEDERATION: "Signs OpenID Federation metadata",
  STATUS_LIST: "Signs status-list entries",
};

function identityName(identity: IssuerDefinition, fallbackLanguage: string) {
  return getLocalizedValue(identity.displayName, fallbackLanguage) || identity.slug;
}

function materialLabel(slot: IdentityKeySlot | undefined, keys: PlatformKey[]) {
  if (!slot) return "Not assigned";
  if (slot.operation) return slot.operation;
  const key = keys.find((candidate) => candidate.id === slot.keyId);
  if (!key)
    return slot.keyId ? "Key unavailable" : "Provider-managed operation";
  const certificate = key.versions.flatMap((version) => version.certificates)
    .find((candidate) => candidate.id === slot.certificateId);
  return slot.certificateId
    ? `${key.keyId} · ${certificate?.eudiLeafProfile ? `${certificate.eudiLeafProfile} · ` : ""}certificate ${slot.certificateId.slice(0, 8)}`
    : key.keyId;
}

function rowPurpose(type: IdentityKeySlotType) {
  return slotPurposes[type];
}

function AssignmentTable({
  rows,
  identity,
  tenantId,
  keys,
  assignedSlots,
  providers,
  editable,
  fixedTrustSystem,
  onChanged,
}: {
  rows: AssignmentRow[];
  identity: IssuerDefinition;
  tenantId?: string;
  keys: PlatformKey[];
  assignedSlots: IdentityKeySlot[];
  providers: SigningProvider[];
  editable: boolean;
  fixedTrustSystem?: TrustSystem;
  onChanged: () => void;
}) {
  return (
    <Table containerClassName="rounded-lg border px-4">
      <TableHeader>
        <TableRow>
          <TableHead>Purpose</TableHead>
          <TableHead>Requirement</TableHead>
          <TableHead>Assigned certificate or key</TableHead>
          <TableHead className="w-32" />
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <AssignmentTableRow
            key={row.id}
            row={row}
            identity={identity}
            tenantId={tenantId}
            keys={keys}
            assignedSlots={assignedSlots}
            providers={providers}
            editable={editable}
            fixedTrustSystem={fixedTrustSystem}
            onChanged={onChanged}
          />
        ))}
      </TableBody>
    </Table>
  );
}

function AssignmentTableRow({
  row,
  identity,
  tenantId,
  keys,
  assignedSlots,
  providers,
  editable,
  fixedTrustSystem,
  onChanged,
}: {
  row: AssignmentRow;
  identity: IssuerDefinition;
  tenantId?: string;
  keys: PlatformKey[];
  assignedSlots: IdentityKeySlot[];
  providers: SigningProvider[];
  editable: boolean;
  fixedTrustSystem?: TrustSystem;
  onChanged: () => void;
}) {
  const [open, setOpen] = useState(false);
  const sourceNote = row.slot
    ? certificateSourceNote(row.slot, keys)
    : undefined;

  return (
    <TableRow>
      <TableCell className="py-4! pr-4">
        <p className="font-semibold">{slotLabels[row.type]}</p>
        <p className="text-sm text-muted-foreground">
          {rowPurpose(row.type)}
        </p>
        {(row.type === "DECRYPTION" ||
          showTrustContext(row.trustSystem, fixedTrustSystem)) && (
          <Badge variant="outline" className="mt-2">
            {row.type === "DECRYPTION" && row.trustSystem === "Default"
              ? "All trust frameworks"
              : row.trustSystem}
          </Badge>
        )}
      </TableCell>
      <TableCell className="py-4! pr-4 text-sm text-muted-foreground">
        {requiredMaterial(row.type, row.trustSystem)}
      </TableCell>
      <TableCell className="py-4! pr-4 text-sm">
        <p>{materialLabel(row.slot, keys)}</p>
        {sourceNote && (
          <p className="mt-1 text-muted-foreground">
            {sourceNote}
          </p>
        )}
      </TableCell>
      <TableCell className="py-4! text-right">
        <Button
          variant="outline"
          disabled={!editable}
          onClick={() => setOpen(true)}
        >
          {row.slot ? (
            "Change"
          ) : (
            <>
              <IconPlus /> Configure
            </>
          )}
        </Button>
        <SlotAssignmentDialog
          open={open}
          onOpenChange={setOpen}
          row={row}
          identity={identity}
          tenantId={tenantId}
          keys={keys}
          assignedSlots={assignedSlots}
          providers={providers}
          fixedTrustSystem={fixedTrustSystem}
          onChanged={onChanged}
        />
      </TableCell>
    </TableRow>
  );
}

function SlotAssignmentDialog({
  open,
  onOpenChange,
  row,
  identity,
  tenantId,
  keys,
  assignedSlots,
  providers,
  fixedTrustSystem,
  onChanged,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  row: AssignmentRow;
  identity: IssuerDefinition;
  tenantId?: string;
  keys: PlatformKey[];
  assignedSlots: IdentityKeySlot[];
  providers: SigningProvider[];
  fixedTrustSystem?: TrustSystem;
  onChanged: () => void;
}) {
  const queryClient = useQueryClient();
  const type = row.type;
  const [trustSystem, setTrustSystem] = useState<TrustSystem>(row.trustSystem);
  const [keyId, setKeyId] = useState(row.slot?.keyId ?? "");
  const [operation, setOperation] = useState(
    row.slot?.operation ?? defaultOperation,
  );
  const [consumer, setConsumer] = useState<IdentityKeySlotConsumer>(
    row.slot?.consumer ?? "VERIFIER",
  );
  const [providerId, setProviderId] = useState<number | "">(
    row.slot?.providerId ?? providers[0]?.id ?? "",
  );
  const [certificateId, setCertificateId] = useState(
    row.slot?.certificateId ?? "",
  );
  const [swissDid, setSwissDid] = useState(
    (row.slot?.configuration as { swissDid?: string } | undefined)?.swissDid ??
      "",
  );
  const [creatingKey, setCreatingKey] = useState(false);
  const [editingCertificate, setEditingCertificate] = useState(false);
  const editableTrustSystem =
    fixedTrustSystem === undefined &&
    type !== "FEDERATION" &&
    type !== "STATUS_LIST";
  const swissIdentity =
    type === "IDENTITY_STATEMENT" && trustSystem === "Switzerland";
  const profile = expectedProfile(type, trustSystem);
  const certificateMode =
    trustSystem === "EUDI" &&
    (type === "IDENTITY_STATEMENT" ||
      type === "CREDENTIAL_SIGNING" ||
      type === "PRESENTATION_SIGNING");
  const certificateProfile =
    certificateMode &&
    (type === "IDENTITY_STATEMENT" || type === "PRESENTATION_SIGNING")
      ? "ACCESS"
      : profile;
  const assigned = assignedKeyIds(
    assignedSlots,
    type,
    trustSystem,
    row.slot?.id,
  );
  const eligibleKeys = keyOptions(keys, type).filter(
    (key) => !assigned.has(key.id),
  );
  const eligibleCertificates = certificateOptions(
    keys,
    certificateProfile,
    trustSystem,
  ).filter((certificate) => !assigned.has(certificate.keyId));
  const resolvedKeyId =
    keyId ||
    (certificateMode && eligibleCertificates.length === 1
      ? eligibleCertificates[0]?.keyId
      : "");
  const selectedKey = keys.find((key) => key.id === resolvedKeyId);
  const selectedVersion = selectedKey?.versions.find(
    (version) => version.id === selectedKey.activeVersionId,
  );
  const certificates =
    selectedVersion?.certificates?.filter(
      (certificate) =>
        certificate.profile === profile &&
        (certificate.trustSystem ?? "Default") ===
          (type === "STATUS_LIST" || type === "FEDERATION"
            ? "Default"
            : trustSystem) &&
        certificate.notBefore &&
        Date.parse(certificate.notBefore) <= Date.now() &&
        certificate.notAfter &&
        Date.parse(certificate.notAfter) > Date.now(),
    ) ?? [];
  const effectiveCertificateId =
    certificateId ||
    (certificates.length === 1 ? certificates[0]?.id : undefined);

  const invalidate = () => {
    const scopeKey = tenantId ?? "platform";
    void queryClient.invalidateQueries({
      queryKey: ["identity-key-slots", scopeKey, identity.id],
    });
    void queryClient.invalidateQueries({
      queryKey: ["identity-grants", scopeKey, identity.id],
    });
    void queryClient.invalidateQueries({
      queryKey: ["identity-federation", tenantId, identity.id],
    });
    void queryClient.invalidateQueries({
      queryKey: ["credential-encryption", tenantId, identity.id],
    });
    void queryClient.invalidateQueries({ queryKey: ["issuer-definitions"] });
  };

  const save = useMutation({
    mutationFn: (slot: IdentityKeySlotRequest) =>
      saveIdentityKeySlot({ tenantId, identityId: identity.id, slot }),
    onSuccess: () => {
      invalidate();
      onChanged();
      onOpenChange(false);
      toast.success("Identity key assignment applied.");
    },
    onError: (error) =>
      toast.error("Could not save identity key slot.", {
        description: error.message,
      }),
  });
  const remove = useMutation({
    mutationFn: () =>
      deleteIdentityKeySlot({
        tenantId,
        identityId: identity.id,
        slotId: row.slot?.id ?? "",
      }),
    onSuccess: () => {
      invalidate();
      onChanged();
      onOpenChange(false);
      toast.success("Identity key assignment removed.");
    },
    onError: (error) =>
      toast.error("Could not delete identity key slot.", {
        description: error.message,
      }),
  });

  const trustSystemValue =
    type === "FEDERATION" || type === "STATUS_LIST"
      ? null
      : type === "DECRYPTION" && trustSystem === "Default"
        ? null
        : trustSystem;
  const canApply =
    !save.isPending &&
    (!resolvedKeyId || !assigned.has(resolvedKeyId)) &&
    (!certificateMode || Boolean(effectiveCertificateId)) &&
    (!swissIdentity || Boolean(swissDid.trim())) &&
    (type === "OPERATION"
      ? Boolean(providerId) && Boolean(operation.trim())
      : Boolean(resolvedKeyId));

  return (
    <>
      <Dialog open={open && !editingCertificate} onOpenChange={onOpenChange}>
        <DialogContent className="max-h-[90vh] w-[calc(100%-2rem)] max-w-2xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {row.slot ? "Change" : "Configure"} {slotLabels[row.type]}
            </DialogTitle>
            <DialogDescription>
              {rowPurpose(row.type)}. Apply this assignment
              when the certificate or key is correct.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            {editableTrustSystem && (
              <Field>
                <FieldLabel>
                  {type === "DECRYPTION" ? "Trust framework" : "Trust context"}
                </FieldLabel>
                <select
                  className="h-9 w-full rounded-md border bg-transparent px-3 text-sm"
                  value={trustSystem}
                  onChange={(event) => {
                    setTrustSystem(event.target.value as TrustSystem);
                    setKeyId("");
                    setCertificateId("");
                  }}
                >
                  <option value="Default">
                    {type === "DECRYPTION"
                      ? "All trust frameworks"
                      : "Custom / no trust framework"}
                  </option>
                  <option value="EUDI">EUDI</option>
                  <option value="Switzerland">Switzerland</option>
                  <option value="Custom">Custom</option>
                  <option value="OIDF">OpenID Federation (OIDF)</option>
                </select>
              </Field>
            )}
            {type === "DECRYPTION" && (
              <Field>
                <FieldLabel>Scope</FieldLabel>
                <FieldDescription>
                  {trustSystem === "Default"
                    ? "This key applies to all trust frameworks."
                    : `This key is used only for ${trustSystem} issuance profiles.`}
                </FieldDescription>
              </Field>
            )}
            {type === "OPERATION" && (
              <>
                <Field>
                  <FieldLabel>Operation</FieldLabel>
                  <Input
                    value={operation}
                    onChange={(event) => setOperation(event.target.value)}
                  />
                </Field>
                <Field>
                  <FieldLabel>Consumer</FieldLabel>
                  <select
                    className="h-9 w-full rounded-md border bg-transparent px-3 text-sm"
                    value={consumer}
                    onChange={(event) =>
                      setConsumer(event.target.value as IdentityKeySlotConsumer)
                    }
                  >
                    <option value="ISSUER">Issuer</option>
                    <option value="VERIFIER">Verifier</option>
                  </select>
                </Field>
                <Field>
                  <FieldLabel>Provider</FieldLabel>
                  <select
                    className="h-9 w-full rounded-md border bg-transparent px-3 text-sm"
                    value={providerId}
                    onChange={(event) =>
                      setProviderId(
                        event.target.value ? Number(event.target.value) : "",
                      )
                    }
                  >
                    <option value="">Choose a provider</option>
                    {providers.map((provider) => (
                      <option key={provider.id} value={provider.id}>
                        {provider.name}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field>
                  <FieldLabel>Key (optional)</FieldLabel>
                  <select
                    className="h-9 w-full rounded-md border bg-transparent px-3 text-sm"
                    value={keyId}
                    onChange={(event) => {
                      setKeyId(event.target.value);
                      setCertificateId("");
                    }}
                  >
                    <option value="">Keyless operation</option>
                    {eligibleKeys.map((key) => (
                      <option key={key.id} value={key.id}>
                        {key.label}
                      </option>
                    ))}
                  </select>
                </Field>
              </>
            )}
            {type !== "OPERATION" && (
              <Field>
                <FieldLabel>
                  {certificateMode
                    ? certificateProfile === "ACCESS"
                      ? "EUDI access certificate"
                      : "EUDI credential signing certificate"
                    : "Key"}
                </FieldLabel>
                {certificateMode ? (
                  eligibleCertificates.length === 1 &&
                  effectiveCertificateId ? (
                    <p className="text-sm">
                      {eligibleCertificates[0]?.label} · selected automatically
                    </p>
                  ) : (
                    <select
                      className="h-9 w-full rounded-md border bg-transparent px-3 text-sm"
                      value={effectiveCertificateId ?? ""}
                      onChange={(event) => {
                        const certificate = eligibleCertificates.find(
                          (candidate) => candidate.id === event.target.value,
                        );
                        setCertificateId(certificate?.id ?? "");
                        setKeyId(certificate?.keyId ?? "");
                      }}
                    >
                      <option value="">
                        Choose{" "}
                        {certificateProfile === "ACCESS"
                          ? "an EUDI access certificate"
                          : "a certificate"}
                      </option>
                      {eligibleCertificates.map((certificate) => (
                        <option key={certificate.id} value={certificate.id}>
                          {certificate.label}
                        </option>
                      ))}
                    </select>
                  )
                ) : (
                  <select
                    className="h-9 w-full rounded-md border bg-transparent px-3 text-sm"
                    value={keyId}
                    onChange={(event) => {
                      setKeyId(event.target.value);
                      setCertificateId("");
                    }}
                  >
                    <option value="">Choose a key</option>
                    {eligibleKeys.map((key) => (
                      <option key={key.id} value={key.id}>
                        {key.label}
                      </option>
                    ))}
                  </select>
                )}
                {certificateMode && (
                  <FieldDescription>
                    The certificate determines the signing key. Only valid EUDI
                    certificates of the required profile are offered.
                  </FieldDescription>
                )}
              </Field>
            )}
            {type !== "OPERATION" && (
              <Button
                variant="outline"
                onClick={() => setCreatingKey(!creatingKey)}
              >
                {creatingKey ? "Choose an existing key" : "Create a new key"}
              </Button>
            )}
            {creatingKey && (
              <SlotKeyCreate
                key={`${type}-${trustSystem}`}
                tenantId={tenantId}
                suggestedName={`${identity.slug}-${trustSystem.toLowerCase()}-${type.toLowerCase().replaceAll("_", "-")}`}
                usage={type === "DECRYPTION" ? "KEY_AGREEMENT" : "SIGN"}
                profile={profile}
                trustSystem={
                  type === "STATUS_LIST" || type === "FEDERATION"
                    ? "Default"
                    : trustSystem
                }
                onCreated={(key) => {
                  setKeyId(key.id);
                  setCertificateId("");
                  setCreatingKey(false);
                }}
              />
            )}
            {selectedKey && type !== "DECRYPTION" && (
              <Button
                variant="outline"
                onClick={() => setEditingCertificate(true)}
              >
                {certificateMode
                  ? "Access certificate / CSR"
                  : "Certificates / CSR"}
              </Button>
            )}
            {swissIdentity && selectedVersion && (
              <Field>
                <FieldLabel htmlFor="slot-swiss-jwk">
                  Public JWK to publish
                </FieldLabel>
                <Textarea
                  id="slot-swiss-jwk"
                  rows={6}
                  readOnly
                  value={selectedVersion.publicJwk}
                />
                <FieldDescription>
                  Publish this public key in the Swiss DID document. Use its kid
                  as the verification-method fragment.
                </FieldDescription>
              </Field>
            )}
            {swissIdentity && (
              <Field>
                <FieldLabel htmlFor="slot-swiss-did">Published DID</FieldLabel>
                <Input
                  id="slot-swiss-did"
                  value={swissDid}
                  onChange={(event) => setSwissDid(event.target.value)}
                  placeholder="did:webvh:…"
                />
                <FieldDescription>
                  The platform verifies the signed DID history and matching key
                  before saving.
                </FieldDescription>
              </Field>
            )}
            {!certificateMode &&
              type !== "DECRYPTION" &&
              certificates.length > 1 && (
                <Field>
                  <FieldLabel>Certificate</FieldLabel>
                  <select
                    className="h-9 w-full rounded-md border bg-transparent px-3 text-sm"
                    value={certificateId}
                    onChange={(event) => setCertificateId(event.target.value)}
                  >
                    <option value="">Choose a certificate</option>
                    {certificates.map((certificate) => (
                      <option key={certificate.id} value={certificate.id}>
                        {certificate.eudiLeafProfile ? `${certificate.eudiLeafProfile} · ` : ""}{certificate.profile} · {certificate.source} · expires{" "}
                        {certificate.notAfter?.slice(0, 10) ?? "unknown"}
                      </option>
                    ))}
                  </select>
                </Field>
              )}
            {!certificateMode &&
              type !== "DECRYPTION" &&
              certificates.length === 1 && (
                <p className="text-sm text-muted-foreground">
                  Certificate selected automatically · expires{" "}
                  {certificates[0]?.notAfter?.slice(0, 10) ?? "unknown"}
                </p>
              )}
          </div>
          <DialogFooter className="flex-wrap">
            {row.slot ? (
              <Button
                variant="ghost"
                className="text-destructive"
                disabled={remove.isPending}
                onClick={() => remove.mutate()}
              >
                <IconTrash /> Remove assignment
              </Button>
            ) : (
              <span />
            )}
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => onOpenChange(false)}>
                Cancel
              </Button>
              <Button
                disabled={!canApply}
                onClick={() =>
                  save.mutate({
                    id: row.slot?.id,
                    order: row.slot?.order,
                    configuration: swissIdentity
                      ? {
                          ...((row.slot?.configuration as Record<
                            string,
                            unknown
                          >) ?? {}),
                          swissDid: swissDid.trim(),
                        }
                      : row.slot?.configuration,
                    type,
                    trustSystem: trustSystemValue,
                    operation: type === "OPERATION" ? operation : null,
                    consumer: type === "OPERATION" ? consumer : null,
                    providerId:
                      type === "OPERATION" && providerId !== ""
                        ? providerId
                        : null,
                    keyId: resolvedKeyId || null,
                    certificateId:
                      type === "DECRYPTION"
                        ? null
                        : (effectiveCertificateId ?? null),
                  })
                }
              >
                Apply assignment
              </Button>
            </div>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      {editingCertificate && selectedKey && (
        <KeyCertificatesDialog
          tenantId={tenantId}
          signingKey={selectedKey}
          initialProfile={certificateProfile}
          initialTrustSystem={
            type === "STATUS_LIST" ||
            type === "FEDERATION" ||
            trustSystem === "Default"
              ? ""
              : trustSystem
          }
          onClose={() => setEditingCertificate(false)}
        />
      )}
    </>
  );
}

function ConfigureAssignmentButton({
  row,
  identity,
  tenantId,
  keys,
  assignedSlots,
  providers,
  editable,
  fixedTrustSystem,
  onChanged,
}: {
  row: AssignmentRow;
  identity: IssuerDefinition;
  tenantId?: string;
  keys: PlatformKey[];
  assignedSlots: IdentityKeySlot[];
  providers: SigningProvider[];
  editable: boolean;
  fixedTrustSystem?: TrustSystem;
  onChanged: () => void;
}) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <Button
        variant="outline"
        disabled={!editable}
        onClick={() => setOpen(true)}
      >
        <IconPlus /> Configure {slotLabels[row.type]}
      </Button>
      <SlotAssignmentDialog
        open={open}
        onOpenChange={setOpen}
        row={row}
        identity={identity}
        tenantId={tenantId}
        keys={keys}
        assignedSlots={assignedSlots}
        providers={providers}
        fixedTrustSystem={fixedTrustSystem}
        onChanged={onChanged}
      />
    </>
  );
}

function configurationRows(
  types: readonly IdentityKeySlotType[],
  rows: AssignmentRow[],
  addTrustSystems: readonly ConfiguredTrustSystem[] | undefined,
  fixedTrustSystem: TrustSystem | undefined,
): AssignmentRow[] {
  return types.flatMap((type) => {
    const assigned = rows.filter((row) => row.type === type);

    if (type === "DECRYPTION") {
      return [{
        id: `${type}-configure`,
        type,
        trustSystem: fixedTrustSystem ?? "Default",
      } as AssignmentRow];
    }

    if (assigned.length > 0) return [];

    const contexts: TrustSystem[] = fixedTrustSystem
      ? [fixedTrustSystem]
      : type === "IDENTITY_STATEMENT" && addTrustSystems?.length
        ? [...addTrustSystems]
        : ["Default"];

    return contexts.map((trustSystem) => ({
      id: `${type}-${trustSystem}-configure`,
      type,
      trustSystem,
    }));
  });
}

function IdentitySlotsRow({
  tenantId,
  identity,
  keys,
  types,
  addTrustSystems,
  trustSystem,
  presentation,
  providers,
  fallbackLanguage,
}: {
  tenantId?: string;
  identity: IssuerDefinition;
  keys: PlatformKey[];
  types: readonly IdentityKeySlotType[];
  addTrustSystems?: readonly ConfiguredTrustSystem[];
  trustSystem?: TrustSystem;
  presentation: "grouped" | "single-identity";
  providers: SigningProvider[];
  fallbackLanguage?: string;
}) {
  const queryClient = useQueryClient();
  const scopeKey = tenantId ?? "platform";
  const queryKey = ["identity-key-slots", scopeKey, identity.id];
  const { data: slots = [], isPending } = useQuery({
    queryKey,
    queryFn: () => getIdentityKeySlots({ tenantId, identityId: identity.id }),
  });
  const editable = (identity.tenantId ?? undefined) === tenantId;
  const rows = rowsFor(
    slots.filter((slot) => types.includes(slot.type)),
    types,
    addTrustSystems,
    trustSystem,
  );
  const primaryRows = rows.filter((row) => !advancedTypes.has(row.type));
  const advancedRows = rows.filter((row) => advancedTypes.has(row.type));
  const primaryTypes = types.filter((type) => !advancedTypes.has(type));
  const advancedTypeRows = types.filter((type) => advancedTypes.has(type));
  const primaryConfigurationRows = configurationRows(
    primaryTypes,
    primaryRows,
    addTrustSystems,
    trustSystem,
  );
  const advancedConfigurationRows = configurationRows(
    advancedTypeRows,
    advancedRows,
    addTrustSystems,
    trustSystem,
  );
  const visibleSlots = rows.flatMap((row) => row.slot ? [row.slot] : []);
  const developmentMaterial = trustSystem === "Default"
    && hasDevelopmentCertificate(visibleSlots, keys);
  const onChanged = () => {
    void queryClient.invalidateQueries({ queryKey });
  };

  return (
    <div className={presentation === "grouped" ? "rounded-xl border p-4" : undefined}>
      {presentation === "grouped" && (
        <div className="flex items-start gap-3">
          <IconKey className="mt-1 size-5" />
          <div>
            <p className="font-semibold">
              {identityName(identity, fallbackLanguage ?? DEFAULT_LOCALE)}
            </p>
            {!editable && (
              <p className="text-sm text-muted-foreground">
                Inherited from platform · read-only
              </p>
            )}
          </div>
        </div>
      )}
      {isPending ? (
        <p className="mt-4 text-sm text-muted-foreground">
          Loading assignments…
        </p>
      ) : (
        <div className={presentation === "grouped" ? "mt-4 space-y-6" : "space-y-6"}>
          {developmentMaterial && (
            <p className="text-sm text-muted-foreground">
              The local development setup generated the certificates shown
              below automatically.
            </p>
          )}
          {(primaryRows.length > 0 || primaryConfigurationRows.length > 0) && (
            <div className="space-y-3">
              {primaryRows.length > 0 && (
                <AssignmentTable
                  rows={primaryRows}
                  identity={identity}
                  tenantId={tenantId}
                  keys={keys}
                  assignedSlots={slots}
                  providers={providers}
                  editable={editable}
                  fixedTrustSystem={trustSystem}
                  onChanged={onChanged}
                />
              )}
              {primaryConfigurationRows.length > 0 && (
                <div className="flex flex-wrap gap-2">
                  {primaryConfigurationRows.map((row) => (
                    <ConfigureAssignmentButton
                      key={row.id}
                      row={row}
                      identity={identity}
                      tenantId={tenantId}
                      keys={keys}
                      assignedSlots={slots}
                      providers={providers}
                      editable={editable}
                      fixedTrustSystem={trustSystem}
                      onChanged={onChanged}
                    />
                  ))}
                </div>
              )}
            </div>
          )}
          {(advancedRows.length > 0 || advancedConfigurationRows.length > 0) && (
            <section
              className={
                primaryRows.length > 0 || primaryConfigurationRows.length > 0
                  ? "border-t pt-5"
                  : undefined
              }
            >
              {(primaryRows.length > 0 || primaryConfigurationRows.length > 0) && (
                <>
                  <h3 className="font-semibold">Advanced</h3>
                  <p className="mb-3 text-sm text-muted-foreground">
                    Low-level operations, federation, decryption, and
                    status-list capabilities.
                  </p>
                </>
              )}
              {advancedRows.length > 0 && (
                <AssignmentTable
                  rows={advancedRows}
                  identity={identity}
                  tenantId={tenantId}
                  keys={keys}
                  assignedSlots={slots}
                  providers={providers}
                  editable={editable}
                  fixedTrustSystem={trustSystem}
                  onChanged={onChanged}
                />
              )}
              {advancedConfigurationRows.length > 0 && (
                <div className="flex flex-wrap gap-2">
                  {advancedConfigurationRows.map((row) => (
                    <ConfigureAssignmentButton
                      key={row.id}
                      row={row}
                      identity={identity}
                      tenantId={tenantId}
                      keys={keys}
                      assignedSlots={slots}
                      providers={providers}
                      editable={editable}
                      fixedTrustSystem={trustSystem}
                      onChanged={onChanged}
                    />
                  ))}
                </div>
              )}
            </section>
          )}
        </div>
      )}
    </div>
  );
}

export function IdentityKeySlotsCard({
  tenantId,
  identities,
  types = slotTypes,
  addTrustSystems,
  trustSystem,
  presentation = "grouped",
  title = "Key assignments",
  description = "Assign keys to this identity. Service access follows actual use.",
}: {
  tenantId?: string;
  identities: IssuerDefinition[];
  types?: readonly IdentityKeySlotType[];
  addTrustSystems?: readonly ConfiguredTrustSystem[];
  trustSystem?: TrustSystem;
  presentation?: "grouped" | "single-identity";
  title?: string;
  description?: string;
}) {
  const scopeKey = tenantId ?? "platform";
  const { data: keys = [] } = useQuery({
    queryKey: ["platform-keys", scopeKey],
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

  return (
    <Card className="mt-4">
      <div>
        <h2 className="text-2xl font-semibold">{title}</h2>
        <p className="mt-1.5 text-muted-foreground">{description}</p>
      </div>
      <div className="mt-6 space-y-3">
        {identities.map((identity) => (
          <IdentitySlotsRow
            key={identity.id}
            tenantId={tenantId}
            identity={identity}
            keys={keys}
            types={types}
            addTrustSystems={addTrustSystems}
            trustSystem={trustSystem}
            presentation={presentation}
            providers={providers}
            fallbackLanguage={settings?.defaultLanguage}
          />
        ))}
      </div>
    </Card>
  );
}
