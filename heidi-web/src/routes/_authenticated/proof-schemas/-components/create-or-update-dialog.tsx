// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconChevronDown } from "@tabler/icons-react";
import { useQuery } from "@tanstack/react-query";
import { useAtomValue } from "jotai";
import { type ReactNode, useState } from "react";
import { FormattedMessage } from "react-intl";
import {
  ComboboxSchemaItem,
  CredentialSchemaCombobox,
} from "@/components/common/credential-schema-combobox";
import { EcosystemProfileLabel } from "@/components/common/ecosystem-profile-label";
import { Button } from "@/components/ui/button";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { schemaListOptions } from "@/lib/api/credential-schemas/query-options";
import { getIdentityKeySlots } from "@/lib/api/identity-key-slots/api";
import { issuerDefinitionListOptions } from "@/lib/api/issuer-definitions/query-options";
import { getKeys } from "@/lib/api/keys/api";
import {
  useCreateProofSchemaMutation,
  useUpdateProofSchemaMutation,
} from "@/lib/api/proof-schemas/mutations";
import {
  CREDENTIAL_IDENTITY,
  verifierIdentityId as resolveVerifierIdentityId,
} from "@/lib/api/proof-schemas/verifier-identity";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";
import { getSigningProviders } from "@/lib/api/signing-providers/api";
import { selectedTenantAtom } from "@/lib/atoms";
import { DEFAULT_LOCALE } from "@/lib/constants";
import { useUser } from "@/lib/hooks/use-user";
import { getLocalizedValue } from "@/lib/utils/localized";
import { CredentialSchemaState } from "@/types/credential-schema";
import {
  compatiblePresentationProfiles,
  findPresentationProfile,
  type PresentationProfileId,
  presentationProfileForIssuanceProfile,
  presentationProfileOptions,
  trustFrameworkDisplayName,
  trustSystemForPresentationProfile,
  trustSystemsForIdentity,
} from "@/types/ecosystem-profile";
import type { ProofSchema } from "@/types/proof-schema";

const BBS_PRESENTATION_SETUP_OPERATION =
  "w3c.bbs-data-integrity-presentation-setup";

export function CreateOrUpdateDialog({
  children = (
    <Button>
      <FormattedMessage
        id="pages.proofSchemas.createDialog.createSchema"
        defaultMessage="Create Schema"
      />
    </Button>
  ),
  defaultValues,
  proofSchema,
}: {
  children: ReactNode;
  defaultValues?: { title: string; purpose: string };
  proofSchema?: ProofSchema;
}) {
  const [open, setOpen] = useState(false);
  const [verifierIdentityId, setVerifierIdentityId] = useState(
    proofSchema?.verifierIdentity?.id?.toString() ?? CREDENTIAL_IDENTITY,
  );
  const [verifierSigningKeyId, setVerifierSigningKeyId] = useState(
    proofSchema?.verifierSigningKeyId ?? "primary",
  );
  const [presentationProfileOverride, setPresentationProfileOverride] =
    useState<PresentationProfileId | undefined>(
      proofSchema?.presentationProfileId,
    );
  const [proofSigningProviderId, setProofSigningProviderId] = useState(
    proofSchema?.proofSigningProviderId?.toString() ?? "none",
  );
  const [isCredentialSchemaPickerOpen, setIsCredentialSchemaPickerOpen] =
    useState(false);
  const [selectedCredentialSchemaId, setSelectedCredentialSchemaId] = useState<
    string | undefined
  >();
  const user = useUser();
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const tenantId = proofSchema?.tenantId ?? selectedTenant ?? user.tenantId;
  const { data: identities = [] } = useQuery(
    issuerDefinitionListOptions({ tenantId }),
  );
  const { data: settings } = useQuery(settingsForOrganisationOptions(tenantId));
  const { data: credentialSchemas = [] } = useQuery({
    ...schemaListOptions({
      statesToExclude: [
        CredentialSchemaState.Archived,
        CredentialSchemaState.Created,
      ],
      includeImages: false,
    }),
    enabled: !proofSchema,
    select(data) {
      return selectedTenant
        ? data.filter((schema) => schema.tenantId === selectedTenant)
        : data;
    },
  });
  const { data: platformKeys = [] } = useQuery({
    queryKey: ["platform-keys", tenantId],
    queryFn: () => getKeys(tenantId),
    enabled: Boolean(tenantId),
  });
  const { data: signingProviders = [] } = useQuery({
    queryKey: ["signing-providers", tenantId],
    queryFn: () => getSigningProviders(tenantId),
    enabled: Boolean(tenantId),
  });
  const proofProviders = signingProviders.filter((provider) =>
    provider.keylessOperations.includes(BBS_PRESENTATION_SETUP_OPERATION),
  );
  const selectedCredentialSchema = credentialSchemas.find(
    (schema) => schema.id === selectedCredentialSchemaId,
  );
  const inferredPresentationProfileId = presentationProfileForIssuanceProfile(
    selectedCredentialSchema?.issuerSettings.issuanceProfileId,
  );
  const basePresentationProfileId =
    proofSchema?.presentationProfileId ?? inferredPresentationProfileId;
  const baseTrustSystem = basePresentationProfileId
    ? trustSystemForPresentationProfile(basePresentationProfileId)
    : undefined;
  const profileOptions = baseTrustSystem
    ? compatiblePresentationProfiles([baseTrustSystem])
    : presentationProfileOptions;
  const presentationProfileId = profileOptions.some(
    (option) => option.id === presentationProfileOverride,
  )
    ? presentationProfileOverride
    : basePresentationProfileId;
  const presentationProfile = findPresentationProfile(presentationProfileId);
  const isEudiPresentation = presentationProfile?.family === "eudi";
  const isSwissPresentation = presentationProfile?.family === "swiss";
  const compatibleIdentities = identities.filter((identity) => {
    if (!presentationProfile) return true;
    const identityTrustSystems = trustSystemsForIdentity(identity);
    return identityTrustSystems.length === 0
      || identityTrustSystems.includes(presentationProfile.trustSystem ?? "Default");
  });
  const compatibleVerifierIdentity =
    verifierIdentityId === CREDENTIAL_IDENTITY ||
    compatibleIdentities.some(
      (identity) => identity.id.toString() === verifierIdentityId,
    );
  const verifierIdentitySelection = compatibleVerifierIdentity
    ? verifierIdentityId
    : CREDENTIAL_IDENTITY;
  const effectiveVerifierIdentityId = resolveVerifierIdentityId(
    verifierIdentitySelection,
    proofSchema?.verifierIdentity?.id,
    selectedCredentialSchema?.issuerSettings.id,
  );
  const presentationIdentity = identities.find(
    (identity) => identity.id === effectiveVerifierIdentityId,
  );
  const { data: identitySlots = [] } = useQuery({
    queryKey: ["identity-key-slots", tenantId, effectiveVerifierIdentityId],
    queryFn: () =>
      getIdentityKeySlots({
        tenantId,
        identityId: effectiveVerifierIdentityId!,
      }),
    enabled: Boolean(effectiveVerifierIdentityId),
  });
  const verifierKeys = [
    ...new Set(
      identitySlots
        .map((slot) => platformKeys.find((key) => key.id === slot.keyId)?.keyId)
        .filter((keyId): keyId is string => Boolean(keyId)),
    ),
  ];
  const { mutate: create, isPending: isCreating } =
    useCreateProofSchemaMutation();
  const { mutate: update, isPending: isUpdating } =
    useUpdateProofSchemaMutation({
      onSuccess: () => setOpen(false),
    });
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-131.25">
        <DialogHeader>
          <DialogTitle>
            <FormattedMessage
              id="pages.proofSchemas.createDialog.title"
              defaultMessage="Verifier template"
            />
          </DialogTitle>
          <DialogDescription>
            <FormattedMessage
              id="pages.proofSchemas.createDialog.description"
              defaultMessage="Please name your template"
            />
          </DialogDescription>
        </DialogHeader>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            const formData = new FormData(e.currentTarget);
            const title = formData.get("title") as string | null;
            const purpose = formData.get("purpose") as string | null;
            if (
              !title ||
              !purpose ||
              !presentationProfileId ||
              (!proofSchema && !selectedCredentialSchema)
            ) {
              return;
            }
            const verifierConfiguration = {
              verifierIdentityId: resolveVerifierIdentityId(
                verifierIdentitySelection,
                proofSchema?.verifierIdentity?.id,
                selectedCredentialSchema?.issuerSettings.id,
              ),
              presentationProfileId,
              verifierTrustSystem: presentationProfile?.trustSystem,
              verifierSigningKeyId:
                verifierSigningKeyId === "primary"
                  ? null
                  : verifierSigningKeyId,
              proofSigningProviderId:
                proofSigningProviderId === "none"
                  ? null
                  : Number(proofSigningProviderId),
              registrationCertificate: isEudiPresentation
                ? (formData.get("registrationCertificate") as string) || null
                : null,
              swissIdentityStatement: isSwissPresentation
                ? (formData.get("swissIdentityStatement") as string) || null
                : null,
              swissVerificationQueryStatement: isSwissPresentation
                ? (formData.get("swissVerificationQueryStatement") as string) ||
                  null
                : null,
              swissProtectedVerificationStatements: isSwissPresentation
                ? (
                    (formData.get(
                      "swissProtectedVerificationStatements",
                    ) as string) || ""
                  )
                    .split(/\r?\n/)
                    .map((statement) => statement.trim())
                    .filter(Boolean)
                : [],
              verifierInfos: proofSchema?.verifierInfos ?? [],
            };
            if (proofSchema) {
              update({
                ...proofSchema,
                purpose,
                title,
                ...verifierConfiguration,
                credentialSchemes: proofSchema.credentialSchemes.map((s) => ({
                  id: s.id,
                  attributes: s.attributes.map((a) => a.id),
                })),
              });
            } else {
              if (!selectedCredentialSchema) return;
              create({
                title,
                purpose,
                credentialSchemes: [
                  { id: selectedCredentialSchema.id, attributes: [] },
                ],
                validationLogic: "",
                validationMode: "DISABLED",
                redirectUri: "",
                ...verifierConfiguration,
              });
            }
          }}
        >
          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="title">
                <FormattedMessage id="common.title" defaultMessage="Title" />*
              </Label>
              <Input
                defaultValue={defaultValues?.title}
                id="title"
                name="title"
                required
                className="col-span-3"
              />
            </div>
            {!proofSchema && (
              <div className="grid grid-cols-4 items-center gap-4">
                <Label>
                  Issuance schema<span aria-hidden="true">*</span>
                </Label>
                {credentialSchemas.length > 0 ? (
                  <div className="col-span-3">
                    <CredentialSchemaCombobox
                      schemas={credentialSchemas}
                      popoverProps={{
                        open: isCredentialSchemaPickerOpen,
                        onOpenChange: setIsCredentialSchemaPickerOpen,
                      }}
                      buttonProps={{
                        variant: "outline",
                        className: "w-full justify-start",
                      }}
                      commandItemProps={(schema) => ({
                        onSelect: () => {
                          setSelectedCredentialSchemaId(schema.id);
                          setPresentationProfileOverride(undefined);
                          setVerifierIdentityId(CREDENTIAL_IDENTITY);
                          setVerifierSigningKeyId("primary");
                          setIsCredentialSchemaPickerOpen(false);
                        },
                      })}
                    >
                      <ComboboxSchemaItem schema={selectedCredentialSchema} />
                    </CredentialSchemaCombobox>
                  </div>
                ) : (
                  <p className="col-span-3 text-sm text-destructive">
                    No published credential schemas are available.
                  </p>
                )}
              </div>
            )}
            <div className="grid grid-cols-4 items-start gap-4">
              <Label>Profile</Label>
              <div className="col-span-3 space-y-2">
                {presentationProfile ? (
                  <>
                    <p className="text-sm">
                      <EcosystemProfileLabel
                        profile={presentationProfile}
                        identity={presentationIdentity}
                      />
                    </p>
                    <p className="text-sm text-muted-foreground">
                      Trust framework: {trustFrameworkDisplayName(
                        presentationProfile.trustSystem,
                        presentationIdentity,
                      )}
                    </p>
                  </>
                ) : (
                  <p className="text-sm">Not selected</p>
                )}
              </div>
            </div>
            <Collapsible
              defaultOpen={false}
              className="group/overrides border-t"
            >
              <CollapsibleTrigger
                type="button"
                className="flex w-full items-center justify-between py-2 text-left text-sm font-medium text-muted-foreground hover:text-foreground"
              >
                Overrides
                <IconChevronDown className="size-4 transition-transform group-data-[state=open]/overrides:rotate-180" />
              </CollapsibleTrigger>
              <CollapsibleContent className="grid gap-4 border-t pt-4">
                <div className="grid grid-cols-4 items-center gap-4">
                  <Label>Verifier identity</Label>
                  <Select
                    value={verifierIdentityId}
                    onValueChange={(value) => {
                      setVerifierIdentityId(value);
                      setVerifierSigningKeyId("primary");
                    }}
                  >
                    <SelectTrigger className="col-span-3">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={CREDENTIAL_IDENTITY}>
                        Credential identity (default)
                      </SelectItem>
                      {compatibleIdentities.map((identity) => (
                        <SelectItem
                          key={identity.id}
                          value={identity.id.toString()}
                        >
                          {getLocalizedValue(
                            identity.displayName,
                            settings?.defaultLanguage ?? DEFAULT_LOCALE,
                          ) ?? identity.slug}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="grid grid-cols-4 items-center gap-4">
                  <Label>Authorization request key</Label>
                  <Select
                    value={verifierSigningKeyId}
                    onValueChange={setVerifierSigningKeyId}
                  >
                    <SelectTrigger className="col-span-3">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="primary">
                        Primary identity key (default)
                      </SelectItem>
                      {verifierKeys.map((keyId) => (
                        <SelectItem key={keyId} value={keyId}>
                          {keyId}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="grid grid-cols-4 items-center gap-4">
                  <Label>Proof setup provider</Label>
                  <Select
                    value={proofSigningProviderId}
                    onValueChange={setProofSigningProviderId}
                  >
                    <SelectTrigger className="col-span-3">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="none">Not configured</SelectItem>
                      {proofProviders.map((provider) => (
                        <SelectItem
                          key={provider.id}
                          value={provider.id.toString()}
                        >
                          {provider.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="grid grid-cols-4 items-center gap-4">
                  <Label>Client ID scheme</Label>
                  <div className="col-span-3 text-sm text-muted-foreground">
                    {presentationProfile?.clientIdScheme ??
                      "Derived by profile"}
                  </div>
                </div>
                <div className="grid grid-cols-4 items-start gap-4">
                  <Label htmlFor="presentationProfileId">Profile</Label>
                  <Select
                    value={presentationProfileId ?? ""}
                    onValueChange={(value) =>
                      setPresentationProfileOverride(
                        value as PresentationProfileId,
                      )
                    }
                  >
                    <SelectTrigger id="presentationProfileId" className="col-span-3">
                      <SelectValue placeholder="Select profile" />
                    </SelectTrigger>
                    <SelectContent>
                      {profileOptions.map((option) => (
                        <SelectItem key={option.id} value={option.id}>
                          <EcosystemProfileLabel
                            profile={option}
                            identity={presentationIdentity}
                          />
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                {isEudiPresentation && (
                  <div className="grid grid-cols-4 items-start gap-4">
                    <Label htmlFor="registrationCertificate">
                      EUDI registration certificate
                    </Label>
                    <Textarea
                      id="registrationCertificate"
                      name="registrationCertificate"
                      className="col-span-3"
                      defaultValue={proofSchema?.registrationCertificate ?? ""}
                    />
                  </div>
                )}
                {isSwissPresentation && (
                  <>
                    <div className="grid grid-cols-4 items-start gap-4">
                      <Label htmlFor="swissIdentityStatement">
                        Swiss identity statement
                      </Label>
                      <Textarea
                        id="swissIdentityStatement"
                        name="swissIdentityStatement"
                        className="col-span-3"
                        defaultValue={proofSchema?.swissIdentityStatement ?? ""}
                      />
                    </div>
                    <div className="grid grid-cols-4 items-start gap-4">
                      <Label htmlFor="swissVerificationQueryStatement">
                        Swiss query statement (optional)
                      </Label>
                      <Textarea
                        id="swissVerificationQueryStatement"
                        name="swissVerificationQueryStatement"
                        className="col-span-3"
                        defaultValue={
                          proofSchema?.swissVerificationQueryStatement ?? ""
                        }
                      />
                      <p className="col-span-3 col-start-2 text-sm text-muted-foreground">
                        If empty, it is requested from the Swiss Trust Registry
                        when the proof schema is saved. The schema remains
                        usable if no statement is available.
                      </p>
                    </div>
                    <div className="grid grid-cols-4 items-start gap-4">
                      <Label htmlFor="swissProtectedVerificationStatements">
                        Swiss protected-verification statements
                      </Label>
                      <Textarea
                        id="swissProtectedVerificationStatements"
                        name="swissProtectedVerificationStatements"
                        className="col-span-3"
                        placeholder="One compact JWT per line"
                        defaultValue={
                          proofSchema?.swissProtectedVerificationStatements?.join(
                            "\n",
                          ) ?? ""
                        }
                      />
                    </div>
                  </>
                )}
              </CollapsibleContent>
            </Collapsible>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="purpose">
                <FormattedMessage
                  id="common.purpose"
                  defaultMessage="Purpose"
                />
                *
              </Label>
              <Input
                defaultValue={defaultValues?.purpose}
                id="purpose"
                name="purpose"
                required
                className="col-span-3"
              />
            </div>
          </div>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">
                <FormattedMessage id="common.cancel" defaultMessage="Cancel" />
              </Button>
            </DialogClose>
            <Button
              type="submit"
              disabled={
                isCreating ||
                isUpdating ||
                (!proofSchema &&
                  (!selectedCredentialSchema || !presentationProfileId))
              }
            >
              <FormattedMessage id="common.save" defaultMessage="Save" />
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
