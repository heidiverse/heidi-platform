// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconChevronDown } from "@tabler/icons-react";
import { useQueries, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { EcosystemProfileLabel } from "@/components/common/ecosystem-profile-label";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { getSwissVerificationQueries } from "@/lib/api/identity-config/api";
import { getIdentityGrants, getIdentityKeySlots } from "@/lib/api/identity-key-slots/api";
import {
  getCredentialEncryption,
  getIdentityFederation,
  type IssuerDefinition,
} from "@/lib/api/issuer-definitions/api";
import { getKeys } from "@/lib/api/keys/api";
import {
  getSigningProviderConnection,
  getSigningProviders,
} from "@/lib/api/signing-providers/api";
import { CredentialEncryptionCard } from "@/routes/_authenticated/organisation/-components/credential-encryption-card";
import { customizedGroupCount } from "@/routes/_authenticated/organisation/-components/credential-encryption-policy";
import { FederationCard } from "@/routes/_authenticated/organisation/-components/federation-card";
import { IdentityKeySlotsCard } from "@/routes/_authenticated/organisation/-components/identity-key-slots-card";
import {
  requiredSigningClients,
  signingProviderReadiness,
} from "@/routes/_authenticated/organisation/-components/signing-provider-readiness";
import { TrustConfigurationDialog } from "@/routes/_authenticated/organisation/-components/trust-configuration-dialog";
import { TrustOverview as TrustMechanismOverview } from "@/routes/_authenticated/organisation/-components/trust-overview";
import {
  type TrustMechanism,
  trustMechanisms,
  trustStatus,
} from "@/routes/_authenticated/organisation/-components/trust-overview-status";
import { SwissVerificationQueriesOverview } from "@/routes/_authenticated/proof-schemas/-components/verifier-infos";
import {
  findPresentationProfile,
  presentationProfileForTrustSystem,
} from "@/types/ecosystem-profile";
import type { ConfiguredTrustSystem } from "@/types/trust-system";

const trustKeyTypes = ["IDENTITY_STATEMENT", "CREDENTIAL_SIGNING", "PRESENTATION_SIGNING"] as const;
const operationKeyTypes = ["OPERATION"] as const;
const decryptionKeyTypes = ["DECRYPTION"] as const;
const overviewStatusLabels = {
  SETUP_INCOMPLETE: "Incomplete",
  CONFIGURED: "Configured",
  NEEDS_ATTENTION: "Needs attention",
} as const;

export function IdentityDetail({ identity, tenantId }: {
  identity: IssuerDefinition;
  tenantId?: string;
}) {
  const editable = (identity.tenantId ?? undefined) === tenantId;

  return <Tabs defaultValue="overview">
    <TabsList>
      <TabsTrigger value="overview">Overview</TabsTrigger>
      <TabsTrigger value="trust">Profiles</TabsTrigger>
      <TabsTrigger value="encryption">Credential encryption</TabsTrigger>
      <TabsTrigger value="advanced">Operations</TabsTrigger>
    </TabsList>
    <TabsContent value="overview">
      <IdentityOverview tenantId={tenantId} identity={identity} editable={editable} />
    </TabsContent>
    <TabsContent value="trust">
      <TrustWorkspace tenantId={tenantId} identity={identity} editable={editable} />
    </TabsContent>
    <TabsContent value="encryption">
      <CredentialEncryptionCard tenantId={tenantId} issuers={[identity]} presentation="single-identity" />
      <IdentityKeySlotsCard tenantId={tenantId} identities={[identity]} types={decryptionKeyTypes}
        presentation="single-identity" title="Request decryption key"
        description="Assign keys used to decrypt encrypted Credential Requests. A key scoped to all trust frameworks is a fallback; a framework-specific key is used only for that framework." />
    </TabsContent>
    <TabsContent value="advanced">
      <IdentityKeySlotsCard tenantId={tenantId} identities={[identity]} types={operationKeyTypes}
        presentation="single-identity" title="Specialised provider operations"
        description="Assign a signing provider only when a credential or proof format requires an additional cryptographic operation." />
      <StatusListOverview tenantId={tenantId} identityId={identity.id} />
      <Diagnostics tenantId={tenantId} identityId={identity.id} />
    </TabsContent>
  </Tabs>;
}

function IdentityOverview({ tenantId, identity, editable }: {
  tenantId?: string;
  identity: IssuerDefinition;
  editable: boolean;
}) {
  const scopeKey = tenantId ?? "platform";
  const { data: slots = [] } = useQuery({
    queryKey: ["identity-key-slots", scopeKey, identity.id],
    queryFn: () => getIdentityKeySlots({ tenantId, identityId: identity.id }),
  });
  const { data: federation } = useQuery({
    queryKey: ["identity-federation", tenantId, identity.id],
    queryFn: () => getIdentityFederation({ tenantId, issuerId: identity.id }),
  });
  const { data: encryption } = useQuery({
    queryKey: ["credential-encryption", tenantId, identity.id],
    queryFn: () => getCredentialEncryption({ tenantId, issuerId: identity.id }),
  });
  const trustInput = {
    declaredTrustSystems: identity.trustSystems,
    slots,
    federation: federation?.settings,
  };
  const configuredTrust = trustMechanisms.flatMap((mechanism) => {
    const status = trustStatus(mechanism, trustInput);
    return status === "NOT_IN_USE" ? [] : [{ mechanism, status }];
  });
  const customGroups = customizedGroupCount(encryption);

  return <div className="grid gap-4 lg:grid-cols-2">
    <Card>
      <h2 className="text-xl font-semibold">Details</h2>
      <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="text-muted-foreground">Identifier</dt><dd>{identity.slug}</dd>
        <dt className="text-muted-foreground">Ownership</dt>
        <dd>{identity.tenantId ? "Organisation" : "Platform"}{editable ? "" : " · inherited · read-only"}</dd>
      </dl>
    </Card>
    <Card>
      <h2 className="text-xl font-semibold">Configuration</h2>
      <dl className="mt-4 grid grid-cols-[auto_1fr] items-start gap-x-4 gap-y-3 text-sm">
        <dt className="text-muted-foreground">Profiles</dt>
        <dd className="flex flex-wrap gap-2">
          {configuredTrust.length > 0
            ? configuredTrust.map(({ mechanism, status }) => (
                <Badge key={mechanism} variant="outline">
                  {profileLabel(mechanism, identity)} · {overviewStatusLabels[status]}
                </Badge>
              ))
            : "Not configured"}
        </dd>
        <dt className="text-muted-foreground">Credential encryption</dt>
        <dd>{customGroups > 0 ? `${customGroups} custom parameter groups` : "Policy allows all supported parameters"}</dd>
      </dl>
    </Card>
  </div>;
}

type TrustSection = "overview" | "custom" | "eudi" | "switzerland" | "oidf";

const mechanismSections: Record<TrustMechanism, TrustSection> = {
  Custom: "custom",
  EUDI: "eudi",
  Switzerland: "switzerland",
  OIDF: "oidf",
};

function TrustWorkspace({ tenantId, identity, editable }: {
  tenantId?: string;
  identity: IssuerDefinition;
  editable: boolean;
}) {
  const scopeKey = tenantId ?? "platform";
  const [section, setSection] = useState<TrustSection>("overview");
  const { data: slots = [] } = useQuery({
    queryKey: ["identity-key-slots", scopeKey, identity.id],
    queryFn: () => getIdentityKeySlots({ tenantId, identityId: identity.id }),
  });
  const { data: federation } = useQuery({
    queryKey: ["identity-federation", tenantId, identity.id],
    queryFn: () => getIdentityFederation({ tenantId, issuerId: identity.id }),
  });
  const { data: signingProviders = [] } = useQuery({
    queryKey: ["signing-providers", scopeKey],
    queryFn: () => getSigningProviders(tenantId),
  });
  const { data: keys = [] } = useQuery({
    queryKey: ["platform-keys", scopeKey],
    queryFn: () => getKeys(tenantId),
  });
  const providerRequirements = requiredSigningClients(slots, keys);
  const providerIds = [...providerRequirements.keys()];
  const providerConnections = useQueries({
    queries: providerIds.map((providerId) => {
      const provider = signingProviders.find((candidate) => candidate.id === providerId);
      return {
        queryKey: ["signing-provider-connection", scopeKey, providerId],
        queryFn: () => getSigningProviderConnection({
          tenantId,
          providerId,
          scope: provider?.scope ?? "tenant",
        }),
        enabled: Boolean(provider),
      };
    }),
  });
  const providerConnectionsWithIds = providerConnections.map((query, index) => ({
    providerId: providerIds[index]!,
    clients: query.data?.clients,
  }));
  const signingProviderReady = signingProviderReadiness(
    providerRequirements,
    providerConnectionsWithIds,
  );
  const { data: swissVqps = [] } = useQuery({
    queryKey: ["swiss-vqps", scopeKey, identity.id],
    queryFn: () => getSwissVerificationQueries({ tenantId, issuerId: identity.id }),
    enabled: section === "switzerland",
  });

  return <Tabs value={section} onValueChange={(value) => setSection(value as TrustSection)}>
    <TabsList className="overflow-x-auto">
      <TabsTrigger value="overview">Overview</TabsTrigger>
      <TabsTrigger value="eudi">{profileLabel("EUDI", identity)}</TabsTrigger>
      <TabsTrigger value="switzerland">{profileLabel("Switzerland", identity)}</TabsTrigger>
      <TabsTrigger value="oidf">{profileLabel("OIDF", identity)}</TabsTrigger>
      <TabsTrigger value="custom">{profileLabel("Custom", identity)}</TabsTrigger>
    </TabsList>
    <TabsContent value="overview">
      <TrustMechanismOverview
        declaredTrustSystems={identity.trustSystems}
        slots={slots}
        federation={federation?.settings}
        signingProviderReady={signingProviderReady}
        onSelect={(mechanism) => setSection(mechanismSections[mechanism] ?? "overview")}
      />
    </TabsContent>
    <TabsContent value="eudi" className="space-y-4">
      <TrustSystemSettings tenantId={tenantId} identity={identity} system="EUDI" slots={slots} editable={editable} />
      <IdentityKeySlotsCard tenantId={tenantId} identities={[identity]} types={trustKeyTypes}
        trustSystem="EUDI" presentation="single-identity" title="Keys and certificates"
        description="Assign access certificates for issuer and verifier identity, and certificates used to sign credentials." />
    </TabsContent>
    <TabsContent value="switzerland" className="space-y-4">
      <TrustSystemSettings tenantId={tenantId} identity={identity} system="Switzerland" slots={slots} editable={editable} />
      <SwissVerificationQueriesOverview queries={swissVqps} />
      <IdentityKeySlotsCard tenantId={tenantId} identities={[identity]} types={trustKeyTypes}
        trustSystem="Switzerland" presentation="single-identity" title="Keys and identifiers"
        description="Assign the keys and DID used with the Swiss trust infrastructure." />
    </TabsContent>
    <TabsContent value="oidf">
      <Card><FederationCard tenantId={tenantId} issuers={[identity]} presentation="single-identity" /></Card>
      <IdentityKeySlotsCard tenantId={tenantId} identities={[identity]} types={trustKeyTypes}
        trustSystem="OIDF" presentation="single-identity" title="Keys and certificates"
        description="Assign the issuer, verifier, and credential-signing keys used by OpenID Federation profiles. The issuer identity key also signs federation entity statements." />
    </TabsContent>
    <TabsContent value="custom">
      <IdentityKeySlotsCard tenantId={tenantId} identities={[identity]} types={trustKeyTypes}
        trustSystem="Custom" presentation="single-identity" title="Custom / manual"
        description="Assignments for custom profiles. Legacy unscoped signing slots remain supported, but new assignments are explicitly scoped to Custom." />
    </TabsContent>
  </Tabs>;
}

function profileLabel(mechanism: TrustMechanism, identity: IssuerDefinition) {
  const profile = findPresentationProfile(
    presentationProfileForTrustSystem(mechanism),
  );
  return profile ? (
    <EcosystemProfileLabel profile={profile} identity={identity} />
  ) : (
    mechanism
  );
}

function TrustSystemSettings({ tenantId, identity, system, slots, editable }: {
  tenantId?: string;
  identity: IssuerDefinition;
  system: ConfiguredTrustSystem;
  slots: Awaited<ReturnType<typeof getIdentityKeySlots>>;
  editable: boolean;
}) {
  const [open, setOpen] = useState(false);
  const statement = slots.find((slot) =>
    slot.type === "IDENTITY_STATEMENT" && slot.trustSystem === system);
  const eudi = system === "EUDI";

  return <Card>
    <h2 className="text-2xl font-semibold">{eudi ? "Verification trust" : "Trust registry and identity"}</h2>
    <p className="mt-1.5 text-muted-foreground">
      {eudi
        ? "Choose the certificate authorities trusted when verifying credentials. The issuer identifier override is available under advanced settings."
        : "Configure the issuer DID, trust registry, trust anchor, and protected trust statements."}
    </p>
    {!statement && <p className="mt-4 text-sm text-muted-foreground">Assign the issuer identity role below before configuring these settings.</p>}
    <Button className="mt-4" variant="outline" disabled={!editable || !statement} onClick={() => setOpen(true)}>
      {eudi ? "Edit verification trust" : "Edit trust registry settings"}
    </Button>
    {open && <TrustConfigurationDialog tenantId={tenantId} issuerId={identity.id} trustSystem={system} open onOpenChange={setOpen} />}
  </Card>;
}

function StatusListOverview({ tenantId, identityId }: { tenantId?: string; identityId: number }) {
  const scopeKey = tenantId ?? "platform";
  const { data: slots = [] } = useQuery({
    queryKey: ["identity-key-slots", scopeKey, identityId],
    queryFn: () => getIdentityKeySlots({ tenantId, identityId }),
  });
  const assignments = slots.filter((slot) => slot.type === "STATUS_LIST");

  return <Card className="mt-4">
    <h2 className="text-xl font-semibold">Status-list signing</h2>
    <p className="mt-1 text-sm text-muted-foreground">
      Status-list keys are configured with their status lists and shown here for reference.
    </p>
    <p className="mt-4 text-sm">
      {assignments.length === 0
        ? "No status list uses this identity."
        : `${assignments.length} derived key assignment${assignments.length === 1 ? "" : "s"}`}
    </p>
  </Card>;
}

function Diagnostics({ tenantId, identityId }: { tenantId?: string; identityId: number }) {
  const scopeKey = tenantId ?? "platform";
  const { data: grants = [] } = useQuery({
    queryKey: ["identity-grants", scopeKey, identityId],
    queryFn: () => getIdentityGrants({ tenantId, identityId }),
  });

  return <Collapsible>
    <Card className="mt-4">
      <CollapsibleTrigger className="flex w-full items-center justify-between gap-3 text-left">
        <span>
          <span className="block text-xl font-semibold">Diagnostics</span>
          <span className="mt-1 block text-sm text-muted-foreground">Derived signing-provider access. Read-only.</span>
        </span>
        <IconChevronDown className="size-5" />
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div className="mt-4 space-y-2 border-t pt-4 text-sm">
          {grants.map((grant) => <div key={`${grant.providerId}-${grant.scope}`} className="rounded-lg border p-3">
            <p className="font-medium">{grant.scope}</p>
            <p className="text-muted-foreground">{Object.entries(grant.desired).map(([client, purposes]) => `${client}: ${purposes.join(", ")}`).join(" · ")} · {grant.pending ? "Pending reconciliation" : grant.confirmed == null ? "Provider unavailable" : "Applied"}</p>
          </div>)}
          {grants.length === 0 && <p className="text-muted-foreground">No provider access is required.</p>}
        </div>
      </CollapsibleContent>
    </Card>
  </Collapsible>;
}
