// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconHierarchy2 } from "@tabler/icons-react";
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
import { Textarea } from "@/components/ui/textarea";
import { getIdentityKeySlots } from "@/lib/api/identity-key-slots/api";
import {
  getFederationAuthorities,
  getIdentityFederation,
  type IdentityFederation,
  type IssuerDefinition,
  updateIdentityFederation,
} from "@/lib/api/issuer-definitions/api";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";
import { DEFAULT_LOCALE } from "@/lib/constants";
import { getLocalizedValue } from "@/lib/utils/localized";
import { authorityHintsFromText, toggled } from "./federation-input";

const disabled: IdentityFederation = {
  signingKeyId: null,
  authorityHints: [],
  authority: false,
  subordinateIds: [],
  credentialSchemeIds: null,
};

function name(
  identity: Pick<IssuerDefinition, "displayName" | "slug">,
  fallbackLanguage: string = DEFAULT_LOCALE,
) {
  return getLocalizedValue(identity.displayName, fallbackLanguage) || identity.slug;
}

export function FederationCard({
  tenantId,
  issuers,
  presentation = "card",
}: {
  tenantId?: string;
  issuers: IssuerDefinition[];
  presentation?: "card" | "embedded" | "single-identity";
}) {
  const [editing, setEditing] = useState<IssuerDefinition | null>(null);
  const { data: settings } = useQuery({
    ...settingsForOrganisationOptions(tenantId ?? ""),
    enabled: Boolean(tenantId),
  });

  const content = (
    <>
      <div>
        <h2 className="text-2xl font-semibold">OpenID Federation</h2>
        <p className="mt-1.5 text-muted-foreground">
          {presentation === "single-identity"
            ? "Publish this identity as a federation entity. Assign its identity-signing key below, then configure superiors, issuer schemes, and authority relationships."
            : "Publish identities as federation entities. Each identity uses its assigned federation identity key, names its superiors, and can act as an authority."}
        </p>
      </div>
      <div className="mt-6 space-y-3">
        {issuers.length === 0 && (
          <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">
            This organisation has no identities yet.
          </p>
        )}
        {issuers.map((issuer) => (
          <IdentityRow
            key={issuer.id}
            tenantId={tenantId}
              issuer={issuer}
              showIdentity={presentation !== "single-identity"}
              fallbackLanguage={settings?.defaultLanguage ?? DEFAULT_LOCALE}
            onEdit={() => setEditing(issuer)}
          />
        ))}
      </div>
    </>
  );

  return (
    <>
      {presentation !== "card" ? (
        <section>{content}</section>
      ) : (
        <Card className="mt-4">{content}</Card>
      )}

      {editing && (
        <FederationDialog
          tenantId={tenantId}
          issuer={editing}
          onClose={() => setEditing(null)}
        />
      )}
    </>
  );
}

function IdentityRow({
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
  const { data } = useQuery({
    queryKey: ["identity-federation", tenantId, issuer.id],
    queryFn: () => getIdentityFederation({ tenantId, issuerId: issuer.id }),
  });
  const settings = data?.settings ?? disabled;
  const enabled = settings.signingKeyId !== null;
  const editable = (issuer.tenantId ?? undefined) === tenantId;

  return (
    <div className="flex flex-wrap items-center gap-3 rounded-xl border p-4">
      {showIdentity && <IconHierarchy2 className="size-5" />}
      {showIdentity ? (
        <div className="min-w-48 flex-1">
          <p className="font-semibold">{name(issuer, fallbackLanguage)}</p>
          <p className="text-sm text-muted-foreground">
            {issuer.slug}
            {editable ? "" : " · inherited from platform · read-only"}
          </p>
        </div>
      ) : (
        <div className="min-w-8 flex-1" />
      )}
      <Badge variant={enabled ? "outline" : "secondary"}>
        {enabled ? "Federation entity" : "Not federated"}
      </Badge>
      {enabled && settings.authority && (
        <Badge variant="outline">Authority</Badge>
      )}
      {enabled && settings.authorityHints.length > 0 && (
        <Badge variant="outline">
          {settings.authorityHints.length} superior
          {settings.authorityHints.length > 1 ? "s" : ""}
        </Badge>
      )}
      <Button variant="outline" disabled={!editable} onClick={onEdit}>
        Edit federation
      </Button>
    </div>
  );
}

function FederationDialog({
  tenantId,
  issuer,
  onClose,
}: {
  tenantId?: string;
  issuer: IssuerDefinition;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const queryKey = ["identity-federation", tenantId, issuer.id];
  const { data } = useQuery({
    queryKey,
    queryFn: () => getIdentityFederation({ tenantId, issuerId: issuer.id }),
  });
  const { data: slots = [] } = useQuery({
    queryKey: ["identity-key-slots", tenantId ?? "platform", issuer.id],
    queryFn: () => getIdentityKeySlots({ tenantId, identityId: issuer.id }),
  });
  const { data: authorities = [] } = useQuery({
    queryKey: ["federation-authorities"],
    queryFn: getFederationAuthorities,
  });

  const [settings, setSettings] = useState<IdentityFederation>(disabled);
  const [hintsText, setHintsText] = useState("");
  const [credentialSchemeIds, setCredentialSchemeIds] = useState<number[]>([]);
  const [federationEnabled, setFederationEnabled] = useState(false);

  useEffect(() => {
    if (!data) return;
    const federationSlot = slots.find(
      (slot) => slot.type === "IDENTITY_STATEMENT" && slot.trustSystem === "OIDF",
    );
    setSettings({
      ...data.settings,
      signingKeyId: federationSlot?.keyId ?? data.settings.signingKeyId,
    });
    setHintsText(data.settings.authorityHints.join("\n"));
    setFederationEnabled(Boolean(data.settings.signingKeyId));
    setCredentialSchemeIds(
      (data.credentialIssuers ?? [])
        .filter((scheme) => scheme.eligible && scheme.enabled)
        .map((scheme) => scheme.id),
    );
  }, [data, slots]);

  const credentialIssuers = (data?.credentialIssuers ?? []).filter(
    (scheme) => scheme.eligible,
  );
  const hints = authorityHintsFromText(hintsText);
  const suggestedAuthorities = authorities.filter(
    (authority) =>
      authority.entityId !== data?.authorityEntityId &&
      !hints.includes(authority.entityId),
  );

  const save = useMutation({
    mutationFn: updateIdentityFederation,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey }),
        queryClient.invalidateQueries({ queryKey: ["federation-authorities"] }),
      ]);
      toast.success("Federation settings saved.");
      onClose();
    },
    onError: (error) =>
      toast.error("Could not save the federation settings.", {
        description: error.message,
      }),
  });

  function submit() {
    save.mutate({
      tenantId,
      issuerId: issuer.id,
      settings: {
        ...settings,
        signingKeyId: federationEnabled ? settings.signingKeyId : null,
        authorityHints: hints,
        credentialSchemeIds,
      },
    });
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>Federation for {name(issuer)}</DialogTitle>
          <DialogDescription>
            A superior publishes a statement about this identity only once both
            sides agree: this identity names the superior, and the superior
            accepts this identity.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-6">
          <Field>
            <FieldLabel>Federation identity key</FieldLabel>
            <FieldDescription>
              The key is assigned as the identity's federation signing purpose
              in the key assignments below. OpenID Federation publishes that
              key in the entity configuration; it is not selected separately
              in this trust configuration.
            </FieldDescription>
            <p className="text-sm">
              {settings.signingKeyId
                ? "Assigned"
                : "Not assigned — configure the Federation identity key first."}
            </p>
          </Field>

          <Field orientation="inline">
            <Checkbox
              id="federation-enabled"
              checked={federationEnabled}
              disabled={!settings.signingKeyId}
              onCheckedChange={(checked) => setFederationEnabled(checked === true)}
            />
            <FieldLabel htmlFor="federation-enabled" className="font-normal">
              Publish this identity as an OpenID Federation entity
            </FieldLabel>
          </Field>

          {federationEnabled && settings.signingKeyId && (
            <>
              <FieldSet>
                <FieldLegend variant="label">Credential issuer entities</FieldLegend>
                <FieldDescription>
                  Select the published credential schemes that should have an
                  OpenID Federation issuer entity. Heidi uses the selected
                  issuer URL for these schemes; clearing all selections
                  publishes none.
                </FieldDescription>
                <FieldGroup className="gap-2">
                  {credentialIssuers.map((scheme) => (
                    <Field key={scheme.id} orientation="inline">
                      <Checkbox
                        id={`credential-issuer-${scheme.id}`}
                        checked={credentialSchemeIds.includes(scheme.id)}
                        onCheckedChange={(checked) =>
                          setCredentialSchemeIds((current) =>
                            checked === true
                              ? [...new Set([...current, scheme.id])]
                              : current.filter((id) => id !== scheme.id),
                          )
                        }
                      />
                      <FieldLabel
                        htmlFor={`credential-issuer-${scheme.id}`}
                        className="font-normal"
                      >
                        {scheme.displayName || scheme.credentialIdentifier}
                        {scheme.version ? ` · ${scheme.version}` : ""}
                      </FieldLabel>
                    </Field>
                  ))}
                </FieldGroup>
                {credentialIssuers.length === 0 && (
                  <p className="text-sm text-muted-foreground">
                    No published URL-based credential schemes use this identity.
                  </p>
                )}
              </FieldSet>

              <Field>
                <FieldLabel htmlFor="authority-hints">Superiors</FieldLabel>
                <Textarea
                  id="authority-hints"
                  rows={3}
                  placeholder="https://anchor.example"
                  value={hintsText}
                  onChange={(event) => setHintsText(event.target.value)}
                />
                <FieldDescription>
                  Entity identifiers of intermediates or trust anchors, one per
                  line. Published as authority hints.
                </FieldDescription>
                {suggestedAuthorities.length > 0 && (
                  <div className="flex flex-wrap gap-2">
                    {suggestedAuthorities.map((authority) => (
                      <Button
                        key={authority.entityId}
                        type="button"
                        variant="outline"
                        onClick={() =>
                          setHintsText(
                            [...hints, authority.entityId].join("\n"),
                          )
                        }
                      >
                        + {name(authority)}
                      </Button>
                    ))}
                  </div>
                )}
              </Field>

              <Field orientation="inline">
                <Checkbox
                  id="federation-authority"
                  checked={settings.authority}
                  onCheckedChange={(checked) =>
                    setSettings((current) => ({
                      ...current,
                      authority: checked === true,
                    }))
                  }
                />
                <FieldLabel
                  htmlFor="federation-authority"
                  className="font-normal"
                >
                  Act as intermediate or trust anchor
                </FieldLabel>
              </Field>

              {settings.authority && data?.authorityEntityId && (
                <FieldSet>
                  <FieldLegend variant="label">Subordinates</FieldLegend>
                  <FieldDescription>
                    Authority entity: <code>{data.authorityEntityId}</code>.
                    Identities marked as naming this authority are published
                    once accepted.
                  </FieldDescription>
                  <FieldGroup className="gap-2">
                    {data.subordinateCandidates.map((candidate) => (
                      <Field key={candidate.id} orientation="inline">
                        <Checkbox
                          id={`subordinate-${candidate.id}`}
                          checked={settings.subordinateIds.includes(
                            candidate.id,
                          )}
                          onCheckedChange={(checked) =>
                            setSettings((current) => ({
                              ...current,
                              subordinateIds: toggled(
                                current.subordinateIds,
                                candidate.id,
                                checked === true,
                              ),
                            }))
                          }
                        />
                        <FieldLabel
                          htmlFor={`subordinate-${candidate.id}`}
                          className="font-normal"
                        >
                          {name(candidate)}
                        </FieldLabel>
                        <Badge
                          variant={
                            candidate.hintsAuthority ? "outline" : "secondary"
                          }
                        >
                          {candidate.hintsAuthority
                            ? "Names this authority"
                            : "Does not name this authority"}
                        </Badge>
                      </Field>
                    ))}
                  </FieldGroup>
                </FieldSet>
              )}
              {settings.authority && !data?.authorityEntityId && (
                <p className="text-sm text-muted-foreground">
                  Save to create the authority entity, then accept subordinates.
                </p>
              )}

              {data && data.entityIds.length > 0 && (
                <FieldSet>
                  <FieldLegend variant="label">Published entities</FieldLegend>
                  <FieldDescription>
                    One per credential schema version issued under this
                    identity's URL, and its verifier.
                  </FieldDescription>
                  <ul className="space-y-1 text-sm">
                    {data.entityIds.map((entityId) => (
                      <li key={entityId}>
                        <code className="break-all">{entityId}</code>
                      </li>
                    ))}
                  </ul>
                </FieldSet>
              )}
            </>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button disabled={save.isPending} onClick={submit}>
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
