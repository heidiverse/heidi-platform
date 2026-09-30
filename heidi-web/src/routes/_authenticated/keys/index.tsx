// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQuery, useQueryClient, useSuspenseQueries } from "@tanstack/react-query";
import { createFileRoute, getRouteApi } from "@tanstack/react-router";
import { useAtomValue } from "jotai";
import { toast } from "sonner";
import { PageHeader } from "@/components/common/page-header";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { issuerDefinitionListOptions } from "@/lib/api/issuer-definitions/query-options";
import { getKeys, type KeyCertificate, removeKeyCertificate } from "@/lib/api/keys/api";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";
import { selectedTenantAtom } from "@/lib/atoms";
import { UserRole } from "@/lib/auth/identity";
import { useUser } from "@/lib/hooks/use-user";
import { KeyManagementCard } from "@/routes/_authenticated/organisation/-components/key-management-card";
import { SigningProviderCard } from "@/routes/_authenticated/organisation/-components/signing-provider-card";
import { certificateRemovalCopy } from "./-certificate-removal";
import { IssuingPki } from "./-issuing-pki";

export const Route = createFileRoute("/_authenticated/keys/")({
  component: RouteComponent,
});

const routeApi = getRouteApi("/_authenticated/keys");

function RouteComponent() {
  const { crumb } = routeApi.useLoaderData();
  const user = useUser();
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const tenantId = user.roles.includes(UserRole.SuperAdmin)
    ? selectedTenant || user.tenantId
    : user.tenantId;
  const [{ data: settings }, { data: identities }] = useSuspenseQueries({
    queries: [
      settingsForOrganisationOptions(tenantId),
      issuerDefinitionListOptions({ tenantId }),
    ],
  });
  const assigned = identities.filter((identity) => settings.issuerIds.includes(identity.id));

  return <>
    <PageHeader heading={crumb} />
    <Tabs defaultValue="keys">
      <TabsList>
        <TabsTrigger value="keys">Keys</TabsTrigger>
        <TabsTrigger value="certificates">Certificates</TabsTrigger>
        <TabsTrigger value="providers">Providers</TabsTrigger>
        <TabsTrigger value="issuing-pki">Issuing PKI</TabsTrigger>
      </TabsList>
      <TabsContent value="keys"><KeyManagementCard tenantId={tenantId} identities={assigned} /></TabsContent>
      <TabsContent value="certificates"><CertificateInventory tenantId={tenantId} /></TabsContent>
      <TabsContent value="providers"><SigningProviderCard tenantId={tenantId} /></TabsContent>
      <TabsContent value="issuing-pki"><IssuingPki tenantId={tenantId} /></TabsContent>
    </Tabs>
  </>;
}

function CertificateInventory({ tenantId }: { tenantId: string }) {
  const queryClient = useQueryClient();
  const { data: keys = [], isPending } = useQuery({
    queryKey: ["platform-keys", tenantId],
    queryFn: () => getKeys(tenantId),
  });
  const certificates = keys.flatMap((key) => key.versions.flatMap((version) =>
    version.certificates.map((certificate) => ({ key, version, certificate }))));

  return <Card className="mt-4">
    <div>
      <h2 className="text-2xl font-semibold">Certificates</h2>
      <p className="mt-1.5 text-muted-foreground">Expiry and trust context across all key versions.</p>
    </div>
    <div className="mt-6 space-y-3">
      {isPending && <p className="text-sm text-muted-foreground">Loading certificates…</p>}
      {!isPending && certificates.length === 0 && <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">No certificates.</p>}
      {certificates.map(({ key, version, certificate }) => {
        const expiry = certificate.notAfter ? new Date(certificate.notAfter) : null;
        const valid = expiry && expiry.getTime() > Date.now();
        return <div key={certificate.id} className="grid gap-2 rounded-xl border p-4 sm:grid-cols-[1fr_auto]">
          <div>
            <p className="font-semibold">{key.keyId} · v{version.version}</p>
            <p className="text-sm text-muted-foreground">{certificate.eudiLeafProfile ? `${certificate.eudiLeafProfile} · ` : ""}{certificate.profile.replaceAll("_", " ")} · {certificate.trustSystem ?? "Default"} · {certificate.source} · {certificate.retiredAt ? "RETIRED" : "ACTIVE"} · {certificate.id.slice(0, 8)}</p>
          </div>
          <div className="flex items-center gap-3">
            <p className={valid ? "text-sm text-muted-foreground" : "text-sm text-destructive"}>{expiry ? `${valid ? "Expires" : "Expired"} ${expiry.toLocaleDateString()}` : "Validity unknown"}</p>
            {!certificate.retiredAt && <CertificateRemoval
              tenantId={tenantId}
              keyId={key.keyId}
              versionId={version.id}
              certificate={certificate}
              onRemoved={() => queryClient.invalidateQueries({ queryKey: ["platform-keys", tenantId] })}
            />}
          </div>
        </div>;
      })}
    </div>
  </Card>;
}

function CertificateRemoval({ tenantId, keyId, versionId, certificate, onRemoved }: {
  tenantId: string;
  keyId: string;
  versionId: string;
  certificate: KeyCertificate;
  onRemoved: () => Promise<unknown>;
}) {
  const copy = certificateRemovalCopy(certificate);
  const remove = useMutation({
    mutationFn: () => removeKeyCertificate({ tenantId, keyId, versionId, certificateId: certificate.id }),
    onSuccess: async () => {
      await onRemoved();
      toast.success(copy.success);
    },
    onError: (error) => toast.error("Could not remove certificate.", { description: error.message }),
  });

  return <AlertDialog>
    <AlertDialogTrigger asChild><Button className="h-8" variant="outline">{copy.action}</Button></AlertDialogTrigger>
    <AlertDialogContent>
      <AlertDialogHeader>
        <AlertDialogTitle>{copy.title}</AlertDialogTitle>
        <AlertDialogDescription>{copy.description}</AlertDialogDescription>
      </AlertDialogHeader>
      <AlertDialogFooter>
        <AlertDialogCancel>Cancel</AlertDialogCancel>
        <AlertDialogAction disabled={remove.isPending} onClick={() => remove.mutate()}>
          {copy.action}
        </AlertDialogAction>
      </AlertDialogFooter>
    </AlertDialogContent>
  </AlertDialog>;
}
