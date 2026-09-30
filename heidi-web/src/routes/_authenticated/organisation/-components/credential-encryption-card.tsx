// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconAlertTriangle, IconLock } from "@tabler/icons-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
  FieldLegend,
  FieldSet,
} from "@/components/ui/field";
import {
  SegmentedControl,
  SegmentedControlItem,
} from "@/components/ui/segmented-control";
import {
  type CredentialEncryptionPolicy,
  getCredentialEncryption,
  getSupportedCredentialEncryption,
  type IssuerDefinition,
  updateCredentialEncryption,
} from "@/lib/api/issuer-definitions/api";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";
import { DEFAULT_LOCALE } from "@/lib/constants";
import { getLocalizedValue } from "@/lib/utils/localized";
import {
  customizedGroupCount,
  hasMissingRequestDecryptionKey,
  missingRequestDecryptionTrustSystems,
  type PolicyList,
  policyMode,
  requestEncryptionRequiredTrustSystems,
  setPolicyListMode,
} from "./credential-encryption-policy";

const emptyPolicy: CredentialEncryptionPolicy = {
  requestAlgValues: [],
  requestEncValues: [],
  requestZipValues: [],
  responseAlgValues: [],
  responseEncValues: [],
  responseZipValues: [],
  requestEncryptionRequired: null,
  responseEncryptionRequired: null,
};

function requestDecryptionScopeLabel(scope: string) {
  if (scope === "Default") return "all trust frameworks";
  if (scope === "OIDF") return "OpenID Federation";
  return scope;
}

export function CredentialEncryptionCard({
  tenantId,
  issuers,
  presentation = "grouped",
}: {
  tenantId?: string;
  issuers: IssuerDefinition[];
  presentation?: "grouped" | "single-identity";
}) {
  const [editing, setEditing] = useState<IssuerDefinition | null>(null);
  const { data: supported } = useQuery({
    queryKey: ["credential-encryption-supported"],
    queryFn: getSupportedCredentialEncryption,
    staleTime: Number.POSITIVE_INFINITY,
  });
  const { data: settings } = useQuery({
    ...settingsForOrganisationOptions(tenantId ?? ""),
    enabled: Boolean(tenantId),
  });

  return (
    <>
      <Card className="mt-4">
        <div>
          <h2 className="text-2xl font-semibold">Credential message encryption</h2>
          <p className="mt-1.5 text-muted-foreground">
            Choose the JWE parameters this identity advertises in Credential
            Issuer Metadata. All supported parameters are enabled by default.
          </p>
        </div>
        <div className="mt-6 space-y-3">
          {issuers.length === 0 && (
            <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">
              This organisation has no identities yet.
            </p>
          )}
          {issuers.map((issuer) => (
            <IssuerRow
              key={issuer.id}
              tenantId={tenantId}
              issuer={issuer}
              showIdentity={presentation === "grouped"}
              fallbackLanguage={settings?.defaultLanguage ?? DEFAULT_LOCALE}
              onEdit={() => setEditing(issuer)}
            />
          ))}
        </div>
      </Card>

      {editing && supported && (
        <EncryptionDialog
          tenantId={tenantId}
          issuer={editing}
          fallbackLanguage={settings?.defaultLanguage ?? DEFAULT_LOCALE}
          supported={supported}
          onClose={() => setEditing(null)}
        />
      )}
    </>
  );
}

function IssuerRow({
  tenantId,
  issuer,
  showIdentity,
  fallbackLanguage,
  onEdit,
}: {
  tenantId?: string;
  issuer: IssuerDefinition;
  showIdentity: boolean;
  fallbackLanguage: string;
  onEdit: () => void;
}) {
  const { data: policy } = useQuery({
    queryKey: ["credential-encryption", tenantId, issuer.id],
    queryFn: () => getCredentialEncryption({ tenantId, issuerId: issuer.id }),
  });
  const customized = customizedGroupCount(policy);
  const editable = (issuer.tenantId ?? undefined) === tenantId;
  const trustSystems = [
    ...(issuer.trustSystems ?? []),
    ...(issuer.defaultTrustSystem ? [issuer.defaultTrustSystem] : []),
  ];
  const missingRequestDecryptionKey = hasMissingRequestDecryptionKey(
    policy,
    trustSystems,
  );
  const missingRequestDecryptionTrustSystem = missingRequestDecryptionTrustSystems(
    policy,
    trustSystems,
  );
  const requestEncryptionRequired =
    requestEncryptionRequiredTrustSystems(policy, trustSystems).length > 0;

  return (
    <div className="flex flex-wrap items-center gap-3 rounded-xl border p-4">
      {showIdentity && <IconLock className="size-5" />}
      {showIdentity && (
        <div className="min-w-48 flex-1">
          <p className="font-semibold">
            {getLocalizedValue(issuer.displayName, fallbackLanguage) ?? issuer.slug}
          </p>
          <p className="text-sm text-muted-foreground">
            {issuer.slug}{editable ? "" : " · inherited from platform · read-only"}
          </p>
        </div>
      )}
      {!showIdentity && <div className="min-w-8 flex-1" />}
      <Badge variant={customized > 0 ? "outline" : "secondary"}>
        {customized > 0
          ? `${customized} custom parameter group${customized > 1 ? "s" : ""}`
          : "All supported parameters"}
      </Badge>
      {requestEncryptionRequired && (
        <Badge variant="outline">Request encryption required</Badge>
      )}
      {policy?.responseEncryptionRequired === true && (
        <Badge variant="outline">Response encryption required</Badge>
      )}
      {missingRequestDecryptionKey && (
        <div
          className="basis-full flex items-start gap-2 rounded-md border border-current/25 bg-infocard-warning-light p-3 text-sm text-infocard-warning"
          role="alert"
        >
          <IconAlertTriangle className="mt-0.5 size-4 shrink-0" />
          <span>
            Credential Request encryption is required, but no decryption key is
            configured for{" "}
            {missingRequestDecryptionTrustSystem
              .map(requestDecryptionScopeLabel)
              .join(" and ")}. An unscoped key applies to all trust frameworks.
            Wallets cannot complete issuance until a matching request decryption
            key is assigned.
          </span>
        </div>
      )}
      <Button variant="outline" disabled={!editable} onClick={onEdit}>
        Edit policy
      </Button>
    </div>
  );
}

function EncryptionDialog({
  tenantId,
  issuer,
  fallbackLanguage,
  supported,
  onClose,
}: {
  tenantId?: string;
  issuer: IssuerDefinition;
  fallbackLanguage: string;
  supported: { algValues: string[]; encValues: string[]; zipValues: string[] };
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const queryKey = ["credential-encryption", tenantId, issuer.id];
  const { data } = useQuery({
    queryKey,
    queryFn: () => getCredentialEncryption({ tenantId, issuerId: issuer.id }),
  });
  const [policy, setPolicy] = useState<CredentialEncryptionPolicy>(emptyPolicy);
  const trustSystems = [
    ...(issuer.trustSystems ?? []),
    ...(issuer.defaultTrustSystem ? [issuer.defaultTrustSystem] : []),
  ];

  useEffect(() => {
    if (data) setPolicy({ ...emptyPolicy, ...data, requestKeys: undefined });
  }, [data]);

  const missingRequestDecryptionKey = hasMissingRequestDecryptionKey(
    { ...policy, requestKeys: data?.requestKeys },
    trustSystems,
  );
  const missingRequestDecryptionTrustSystem = missingRequestDecryptionTrustSystems(
    { ...policy, requestKeys: data?.requestKeys },
    trustSystems,
  );

  const save = useMutation({
    mutationFn: updateCredentialEncryption,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey });
      toast.success("Encryption policy saved.");
      onClose();
    },
    onError: (error) =>
      toast.error("Could not save the encryption policy.", {
        description: error.message,
      }),
  });

  function toggle(list: PolicyList, value: string, checked: boolean) {
    setPolicy((current) => ({
      ...current,
      [list]: checked
        ? [...current[list], value]
        : current[list].filter((entry) => entry !== value),
    }));
  }

  function values(
    legend: string,
    description: string,
    list: PolicyList,
    options: string[],
  ) {
    const mode = policyMode(policy[list]);

    return (
      <FieldSet>
        <FieldLegend variant="label">{legend}</FieldLegend>
        <FieldDescription>{description}</FieldDescription>
        <SegmentedControl
          className="mt-3 max-w-md"
          value={`${list}-${mode}`}
          onValueChange={(value) =>
            setPolicy((current) =>
              setPolicyListMode(
                current,
                list,
                options,
                value.endsWith("-custom") ? "custom" : "all",
              ),
            )
          }
        >
          <SegmentedControlItem value={`${list}-all`}>
            All supported
          </SegmentedControlItem>
          <SegmentedControlItem value={`${list}-custom`} disabled={options.length === 0}>
            Custom selection
          </SegmentedControlItem>
        </SegmentedControl>
        {mode === "custom" && (
          <FieldGroup className="mt-3 grid grid-cols-2 gap-2">
            {options.map((option) => (
              <Field key={option} orientation="inline">
                <Checkbox
                  id={`${list}-${option}`}
                  checked={policy[list].includes(option)}
                  onCheckedChange={(checked) =>
                    toggle(list, option, checked === true)
                  }
                />
                <FieldLabel htmlFor={`${list}-${option}`} className="font-normal">
                  {option}
                </FieldLabel>
              </Field>
            ))}
          </FieldGroup>
        )}
      </FieldSet>
    );
  }

  function required(
    label: string,
    key: "requestEncryptionRequired" | "responseEncryptionRequired",
  ) {
    return (
      <Field orientation="inline">
        <Checkbox
          id={key}
          checked={policy[key] === true}
          onCheckedChange={(checked) =>
            setPolicy((current) => ({
              ...current,
              [key]: checked === true ? true : null,
            }))
          }
        />
        <FieldLabel htmlFor={key} className="font-normal">
          {label}
        </FieldLabel>
      </Field>
    );
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            Encryption for {getLocalizedValue(issuer.displayName, fallbackLanguage) ?? issuer.slug}
          </DialogTitle>
          <DialogDescription>
            Choose all supported values or define a custom allowlist for each
            parameter group. The resulting policy is published in Credential
            Issuer Metadata and enforced at the credential endpoints.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-6">
          {values(
            "Credential Request alg",
            "Selects which of the issuer's decryption keys are published, by their JWK alg.",
            "requestAlgValues",
            supported.algValues,
          )}
          {values(
            "Credential Request enc",
            "Content encryption a wallet may use when it encrypts a request.",
            "requestEncValues",
            supported.encValues,
          )}
          {values(
            "Credential Request zip",
            "Compression a wallet may apply before encrypting a request.",
            "requestZipValues",
            supported.zipValues,
          )}
          {required(
            "Credential Requests must be encrypted",
            "requestEncryptionRequired",
          )}
          {missingRequestDecryptionKey && (
            <div
              className="flex items-start gap-2 rounded-md border border-current/25 bg-infocard-warning-light p-3 text-sm text-infocard-warning"
              role="alert"
            >
              <IconAlertTriangle className="mt-0.5 size-4 shrink-0" />
              <span>
                No matching request decryption key is configured for{" "}
                {missingRequestDecryptionTrustSystem
                  .map(requestDecryptionScopeLabel)
                  .join(" and ")}. Assign one in the Request decryption section,
                or assign an unscoped key that applies to all trust frameworks.
              </span>
            </div>
          )}
          {values(
            "Credential Response alg",
            "Key management a wallet's response encryption key may use.",
            "responseAlgValues",
            supported.algValues,
          )}
          {values(
            "Credential Response enc",
            "Content encryption a wallet may request for its response.",
            "responseEncValues",
            supported.encValues,
          )}
          {values(
            "Credential Response zip",
            "Compression a wallet may request for its response.",
            "responseZipValues",
            supported.zipValues,
          )}
          {required(
            "Wallets must request an encrypted Credential Response",
            "responseEncryptionRequired",
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button
            disabled={save.isPending}
            onClick={() =>
              save.mutate({ tenantId, issuerId: issuer.id, policy })
            }
          >
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
