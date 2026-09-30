// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
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
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import {
  getTrustConfiguration,
  refreshSwissTrustConfiguration,
  type TrustConfigurationUpdate,
  type TrustSystem,
  updateTrustConfiguration,
} from "@/lib/api/identity-config/api";
import { getSubcas } from "@/lib/api/keys/issuing-pki";
import { certificatesFromText } from "@/routes/_authenticated/organisation/-components/certificate-input";

async function readCertificateFile(file: File) {
  const bytes = new Uint8Array(await file.arrayBuffer());
  const text = new TextDecoder().decode(bytes);
  if (text.includes("-----BEGIN CERTIFICATE-----")) return text.trim();
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

export function TrustConfigurationDialog({
  tenantId,
  issuerId,
  trustSystem,
  open,
  onOpenChange,
}: {
  tenantId?: string;
  issuerId: number;
  trustSystem: TrustSystem;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const queryKey = [
    "issuer-trust-configuration",
    tenantId,
    issuerId,
    trustSystem,
  ];
  const { data } = useQuery({
    queryKey,
    queryFn: () => getTrustConfiguration({ tenantId, issuerId, trustSystem }),
  });
  const [issuerClaim, setIssuerClaim] = useState("");
  const [swissDid, setSwissDid] = useState("");
  const [swissRegistryBaseUrl, setSwissRegistryBaseUrl] = useState("");
  const [swissStatusRegistryApiUrl, setSwissStatusRegistryApiUrl] =
    useState("");
  const [swissStatusRegistryPartnerId, setSwissStatusRegistryPartnerId] =
    useState("");
  const [swissTrustRegistryAuthoringUrl, setSwissTrustRegistryAuthoringUrl] =
    useState("");
  const [swissTrustRegistryTokenUrl, setSwissTrustRegistryTokenUrl] =
    useState("");
  const [swissTrustRegistryClientId, setSwissTrustRegistryClientId] =
    useState("");
  const [swissTrustRegistryClientSecret, setSwissTrustRegistryClientSecret] =
    useState("");
  const [swissTrustRegistryRefreshToken, setSwissTrustRegistryRefreshToken] =
    useState("");
  const [identityStatement, setIdentityStatement] = useState("");
  const [issuanceStatements, setIssuanceStatements] = useState("{}");
  const [eudiVerificationTrustAnchors, setEudiVerificationTrustAnchors] =
    useState("");
  const [swissVerificationTrustAnchor, setSwissVerificationTrustAnchor] =
    useState("");
  const [trustOwnPki, setTrustOwnPki] = useState(false);

  // Own Issuing PKI exists only for organisations, and only matters for EUDI.
  const ownPkiAvailable = Boolean(tenantId) && trustSystem === "EUDI";
  const { data: subcas = [] } = useQuery({
    queryKey: ["issuing-pki", tenantId],
    queryFn: () => getSubcas(tenantId),
    enabled: open && ownPkiAvailable,
  });
  const certifiedSubcas = subcas.filter((ca) => ca.certificateChain.length);

  useEffect(() => {
    if (!data) return;
    setIssuerClaim(data.issuerClaim ?? data.swissDid ?? "");
    setSwissDid(data.swissDid ?? "");
    setSwissRegistryBaseUrl(data.swissRegistryBaseUrl ?? "");
    setSwissStatusRegistryApiUrl(data.swissStatusRegistryApiUrl ?? "");
    setSwissStatusRegistryPartnerId(data.swissStatusRegistryPartnerId ?? "");
    setSwissTrustRegistryAuthoringUrl(
      data.swissTrustRegistryAuthoringUrl ?? "",
    );
    setSwissTrustRegistryTokenUrl(data.swissTrustRegistryTokenUrl ?? "");
    setSwissTrustRegistryClientId(data.swissTrustRegistryClientId ?? "");
    setSwissTrustRegistryClientSecret("");
    setSwissTrustRegistryRefreshToken("");
    setIdentityStatement(data.swissIdentityStatement ?? "");
    setIssuanceStatements(
      JSON.stringify(data.swissIssuanceStatements ?? {}, null, 2),
    );
    setEudiVerificationTrustAnchors(
      (data.eudiVerificationTrustAnchors ?? []).join("\n"),
    );
    setSwissVerificationTrustAnchor(data.swissVerificationTrustAnchor ?? "");
    setTrustOwnPki(data.eudiTrustOwnPki ?? false);
  }, [data]);

  const save = useMutation({
    mutationFn: updateTrustConfiguration,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey });
      toast.success("Trust-system configuration saved.");
      onOpenChange(false);
    },
    onError: (error) =>
      toast.error("Could not save trust configuration.", {
        description: error.message,
      }),
  });
  // The registry call reads the DID and base URL from the stored binding, so the form has to be
  // persisted first. Refreshing straight after typing them would otherwise query the registry with
  // the previously saved values, or fail outright while nothing has been saved yet.
  const refresh = useMutation({
    mutationFn: async (configuration: TrustConfigurationUpdate) => {
      await updateTrustConfiguration({
        tenantId,
        issuerId,
        trustSystem,
        configuration,
      });
      return refreshSwissTrustConfiguration({ tenantId, issuerId });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey });
      toast.success("Swiss trust statements refreshed.");
    },
    onError: (error) =>
      toast.error("Could not refresh statements.", {
        description: error.message,
      }),
  });
  /** Turns the form into an update request, or reports the JSON error and returns null. */
  function buildConfiguration(): TrustConfigurationUpdate | null {
    if (trustSystem === "EUDI") {
      return {
        issuerClaim,
        eudiVerificationTrustAnchors: certificatesFromText(
          eudiVerificationTrustAnchors,
        ),
        ...(ownPkiAvailable ? { eudiTrustOwnPki: trustOwnPki } : {}),
      };
    }
    let statements: Record<string, string> = {};
    try {
      const parsed = JSON.parse(issuanceStatements);
      if (
        typeof parsed !== "object" ||
        parsed === null ||
        Array.isArray(parsed) ||
        !Object.values(parsed).every((value) => typeof value === "string")
      ) {
        throw new Error("Expected an object of compact JWT strings");
      }
      statements = parsed as Record<string, string>;
    } catch {
      toast.error("Issuance statements must be valid JSON.");
      return null;
    }
    return {
      issuerClaim,
      swissDid,
      swissRegistryBaseUrl,
      swissStatusRegistryApiUrl,
      swissStatusRegistryPartnerId,
      swissTrustRegistryAuthoringUrl,
      swissTrustRegistryTokenUrl,
      swissTrustRegistryClientId,
      ...(swissTrustRegistryClientSecret.trim()
        ? { swissTrustRegistryClientSecret: swissTrustRegistryClientSecret.trim() }
        : {}),
      ...(swissTrustRegistryRefreshToken.trim()
        ? { swissTrustRegistryRefreshToken: swissTrustRegistryRefreshToken.trim() }
        : {}),
      swissIdentityStatement: identityStatement,
      swissIssuanceStatements: statements,
      swissVerificationTrustAnchor,
    };
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] w-[calc(100%-2rem)] max-w-3xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            {trustSystem === "EUDI"
              ? "EUDI verification trust"
              : `${trustSystem} trust-system setup`}
          </DialogTitle>
          <DialogDescription>
            {trustSystem === "EUDI"
              ? "Configure which credential issuers this identity trusts. Keys and certificates are managed in the assignments below."
              : "Configure ecosystem metadata and trust sources. Operational key and certificate assignments remain in the tables below."}
          </DialogDescription>
        </DialogHeader>
        {trustSystem === "EUDI" ? (
          <div className="space-y-4">
            {ownPkiAvailable && (
              <Field orientation="horizontal" className="rounded-md border p-3">
                <Switch
                  id="trust-own-pki"
                  checked={trustOwnPki}
                  onCheckedChange={setTrustOwnPki}
                />
                <div className="space-y-1">
                  <FieldLabel htmlFor="trust-own-pki">
                    Trust this organisation's Issuing PKI
                  </FieldLabel>
                  <FieldDescription>
                    {certifiedSubcas.length
                      ? `Accepts credentials chaining to: ${certifiedSubcas.map((ca) => ca.subjectDn).join("; ")}. Renewed certificates apply automatically.`
                      : "No certified SubCA yet. Create one under Keys → Issuing PKI."}
                  </FieldDescription>
                </div>
              </Field>
            )}
            <Field>
              <FieldLabel>Trusted credential issuer CAs</FieldLabel>
              <FieldDescription>
                Credentials are accepted only when their certificate chain ends
                at one of these CAs. Add multiple CAs when accepting issuers
                from several trust lists.
              </FieldDescription>
              <Textarea
                rows={8}
                value={eudiVerificationTrustAnchors}
                onChange={(event) =>
                  setEudiVerificationTrustAnchors(event.target.value)
                }
                placeholder="-----BEGIN CERTIFICATE-----"
              />
              <Input
                type="file"
                multiple
                accept=".pem,.cer,.crt,application/pkix-cert"
                onChange={async (event) => {
                  const certificates = await Promise.all(
                    Array.from(event.target.files ?? []).map(
                      readCertificateFile,
                    ),
                  );
                  setEudiVerificationTrustAnchors(certificates.join("\n"));
                }}
              />
            </Field>
            <details className="rounded-md border p-3">
              <summary className="cursor-pointer text-sm font-medium">
                Advanced issuer identifier
              </summary>
              <Field className="mt-3">
                <FieldLabel>Credential issuer identifier override</FieldLabel>
                <FieldDescription>
                  Usually leave this empty. Set it only when issued credentials
                  must use an established identifier instead of the credential
                  issuer URL.
                </FieldDescription>
                <Input
                  placeholder="Uses the credential issuer URL"
                  value={issuerClaim}
                  onChange={(event) => setIssuerClaim(event.target.value)}
                />
              </Field>
            </details>
          </div>
        ) : (
          <div className="space-y-4">
            <Field>
              <FieldLabel>Issuer DID</FieldLabel>
              <FieldDescription>
                A did:webvh identifier. The selected physical key ID becomes its
                fragment in credential KIDs.
              </FieldDescription>
              <Input
                placeholder="did:webvh:…"
                value={swissDid}
                onChange={(event) => {
                  const previousDid = swissDid;
                  const nextDid = event.target.value;
                  setSwissDid(nextDid);
                  if (!issuerClaim || issuerClaim === previousDid) {
                    setIssuerClaim(nextDid);
                  }
                }}
              />
            </Field>
            <Field>
              <FieldLabel>Credential issuer claim</FieldLabel>
              <FieldDescription>
                The exact did:webvh value placed in issued credentials.
              </FieldDescription>
              <Input
                placeholder="did:webvh:…"
                value={issuerClaim}
                onChange={(event) => setIssuerClaim(event.target.value)}
              />
            </Field>
            <Field>
              <FieldLabel>Trust registry base URL</FieldLabel>
              <FieldDescription>
                Read-only registry URL used for identity and issuance trust
                statements.
              </FieldDescription>
              <Input
                placeholder="https://registry.example"
                value={swissRegistryBaseUrl}
                onChange={(event) =>
                  setSwissRegistryBaseUrl(event.target.value)
                }
              />
            </Field>
            <Field>
              <FieldLabel>Trust management authoring URL</FieldLabel>
              <FieldDescription>
                Optional write API URL used to publish verification query trust
                statements. It is different from the read-only registry URL.
              </FieldDescription>
              <Input
                placeholder="https://trust-management.example"
                value={swissTrustRegistryAuthoringUrl}
                onChange={(event) =>
                  setSwissTrustRegistryAuthoringUrl(event.target.value)
                }
              />
            </Field>
            <Field>
              <FieldLabel>Status registry API URL</FieldLabel>
              <FieldDescription>
                API URL used to create and upload this issuer&apos;s status list.
              </FieldDescription>
              <Input
                placeholder="https://status.example"
                value={swissStatusRegistryApiUrl}
                onChange={(event) =>
                  setSwissStatusRegistryApiUrl(event.target.value)
                }
              />
            </Field>
            <Field>
              <FieldLabel>Swiss business partner ID</FieldLabel>
              <FieldDescription>
                Partner UUID used by the Swiss status registry.
              </FieldDescription>
              <Input
                value={swissStatusRegistryPartnerId}
                onChange={(event) =>
                  setSwissStatusRegistryPartnerId(event.target.value)
                }
              />
            </Field>
            <Field>
              <FieldLabel>OAuth token URL</FieldLabel>
              <Input
                placeholder="https://auth.example/token"
                value={swissTrustRegistryTokenUrl}
                onChange={(event) =>
                  setSwissTrustRegistryTokenUrl(event.target.value)
                }
              />
            </Field>
            <Field>
              <FieldLabel>OAuth client ID</FieldLabel>
              <Input
                value={swissTrustRegistryClientId}
                onChange={(event) =>
                  setSwissTrustRegistryClientId(event.target.value)
                }
              />
            </Field>
            <Field>
              <FieldLabel>OAuth client secret</FieldLabel>
              <Input
                type="password"
                placeholder={
                  data?.swissTrustRegistryCredentialsConfigured
                    ? "Leave blank to keep the saved secret"
                    : "Optional"
                }
                value={swissTrustRegistryClientSecret}
                onChange={(event) =>
                  setSwissTrustRegistryClientSecret(event.target.value)
                }
              />
            </Field>
            <Field>
              <FieldLabel>OAuth refresh token</FieldLabel>
              <Input
                type="password"
                placeholder={
                  data?.swissTrustRegistryCredentialsConfigured
                    ? "Leave blank to keep the saved token"
                    : "Optional"
                }
                value={swissTrustRegistryRefreshToken}
                onChange={(event) =>
                  setSwissTrustRegistryRefreshToken(event.target.value)
                }
              />
              <FieldDescription>
                Optional. The access token is requested on demand and the
                rotated refresh token is stored with this identity. Without
                usable credentials, no vqPS is requested.
              </FieldDescription>
            </Field>
            <Field>
              <FieldLabel>Trust statement signing DID</FieldLabel>
              <FieldDescription>
                The DID whose keys are trusted to sign identity trust
                statements loaded from this registry.
              </FieldDescription>
              <Input
                placeholder="did:webvh:…"
                value={swissVerificationTrustAnchor}
                onChange={(event) =>
                  setSwissVerificationTrustAnchor(event.target.value)
                }
              />
            </Field>
            <Field>
              <FieldLabel>Identity Trust Statement</FieldLabel>
              <Textarea
                rows={5}
                value={identityStatement}
                onChange={(event) => setIdentityStatement(event.target.value)}
              />
            </Field>
            <Field>
              <FieldLabel>Protected issuance statements by VCT</FieldLabel>
              <FieldDescription>
                JSON object mapping each VCT to its compact JWT. Registry
                refresh populates this automatically.
              </FieldDescription>
              <Textarea
                rows={7}
                value={issuanceStatements}
                onChange={(event) => setIssuanceStatements(event.target.value)}
              />
            </Field>
            <Button
              variant="outline"
              disabled={
                !swissDid ||
                !swissRegistryBaseUrl ||
                refresh.isPending ||
                save.isPending
              }
              onClick={() => {
                const configuration = buildConfiguration();
                if (configuration) refresh.mutate(configuration);
              }}
            >
              Refresh from registry
            </Button>
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            disabled={save.isPending}
            onClick={() => {
              const configuration = buildConfiguration();
              if (!configuration) return;
              save.mutate({
                tenantId,
                issuerId,
                trustSystem,
                configuration,
              });
            }}
          >
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
