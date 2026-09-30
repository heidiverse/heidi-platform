// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconChevronDown } from "@tabler/icons-react";
import { useStore } from "@tanstack/react-form";
import { useQuery, useSuspenseQueries } from "@tanstack/react-query";
import { useAtomValue } from "jotai";
import { useEffect, useMemo } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { EcosystemProfileLabel } from "@/components/common/ecosystem-profile-label";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
  Field,
  FieldError,
  FieldGroup,
  FieldLabel,
  FieldLegend,
  FieldSet,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { getIdentityKeySlots } from "@/lib/api/identity-key-slots/api";
import { issuerDefinitionListOptions } from "@/lib/api/issuer-definitions/query-options";
import { getKeys } from "@/lib/api/keys/api";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";
import { statusListOptions } from "@/lib/api/status-lists/query-options";
import { selectedTenantAtom } from "@/lib/atoms";
import { useUser } from "@/lib/hooks/use-user";
import { IssuerItem } from "@/routes/_authenticated/credential-schemas/-components/issuer-item";
import { withForm } from "@/routes/_authenticated/credential-schemas/-form/form-context";
import {
  CredentialOfferType,
  CredentialSchemaState,
  IssuerKeyTypes,
  SupportedCredentialTypes,
} from "@/types/credential-schema";
import {
  compatibleIssuanceProfiles,
  findIssuanceProfile,
  trustFrameworkDisplayName,
  trustSystemForIssuanceProfile,
  trustSystemsForIdentity,
} from "@/types/ecosystem-profile";
import {
  configuredTrustSystems,
  normalizeConfiguredTrustSystem,
  TrustSystem,
} from "@/types/trust-system";
import type { CredentialSchemaForm } from "./schemas";

function displayNameForKeyType(keyType: IssuerKeyTypes) {
  switch (keyType) {
    case IssuerKeyTypes.HARDWARE_BIOMETRIC_AUTH:
      return "Hardware Biometric Auth";
    case IssuerKeyTypes.SOFTWARE_NO_AUTH:
      return "Software No Auth";
    case IssuerKeyTypes.NO_KEY_CLAIM_BINDING:
      return "Claim Binding (No Key-Binding)";
    default: {
      console.error("Unhandled key type", keyType);
      return keyType;
    }
  }
}

export const IssuingSettingsTab = withForm({
  defaultValues: {} as CredentialSchemaForm,
  render: ({ form }) => {
    const user = useUser();
    const { $t } = useIntl();
    const selectedTenant = useAtomValue(selectedTenantAtom);
    const tenantId = selectedTenant || user.tenantId;
    const [
      { data: issuerDefinitions },
      { data: settings },
      { data: statusLists },
    ] = useSuspenseQueries({
      queries: [
        issuerDefinitionListOptions({ tenantId }),
        settingsForOrganisationOptions(tenantId),
        statusListOptions(tenantId),
      ],
    });

    const published = useStore(
      form.store,
      (state) => state.values.state === CredentialSchemaState.Published,
    );
    const { data: platformKeys = [] } = useQuery({
      queryKey: ["platform-keys", settings.tenantId],
      queryFn: () => getKeys(settings.tenantId),
    });
    const issuerId = useStore(
      form.store,
      (state) => state.values.issuingSettings.issuerId,
    );
    const selectedIssuer = issuerDefinitions.find(
      (issuer) => issuer.id === issuerId,
    );
    const identityTrustSystems = trustSystemsForIdentity(selectedIssuer);
    const availableTrustSystems = useMemo(
      () =>
        identityTrustSystems.length > 0
          ? identityTrustSystems
          : [TrustSystem.Default],
      [identityTrustSystems.join(",")],
    );
    const { data: identitySlots = [] } = useQuery({
      queryKey: ["identity-key-slots", settings.tenantId, issuerId],
      queryFn: () =>
        getIdentityKeySlots({
          tenantId: settings.tenantId,
          identityId: issuerId!,
        }),
      enabled: Boolean(issuerId),
    });
    const credentialKeys = new Set(
      identitySlots
        .filter((slot) => slot.type === "CREDENTIAL_SIGNING")
        .map((slot) => slot.keyId)
        .filter((keyId): keyId is string => Boolean(keyId)),
    );
    const signingKeys = platformKeys.flatMap((key) => {
      if (!credentialKeys.has(key.id)) return [];
      const active =
        key.versions.find((version) => version.id === key.activeVersionId) ??
        key.versions.find((version) => version.status === "ACTIVE");
      return active ? [{ keyId: key.keyId, algorithm: active.algorithm }] : [];
    });
    const statusListId = useStore(
      form.store,
      (state) => state.values.issuingSettings.statusListId,
    );
    const issuanceProfileId = useStore(
      form.store,
      (state) => state.values.issuingSettings.issuanceProfileId,
    );
    const defaultTrustSystem = useStore(
      form.store,
      (state) => state.values.issuingSettings.defaultTrustSystem,
    );
    const signingKeyIds = useStore(
      form.store,
      (state) => state.values.issuingSettings.signingKeyIds,
    );
    const statusList = statusLists.find((item) => item.id === statusListId);
    const profileOptions = useMemo(
      () => compatibleIssuanceProfiles(availableTrustSystems),
      [availableTrustSystems],
    );
    const profileTrustSystem = trustSystemForIssuanceProfile(issuanceProfileId);
    const selectedTrustSystem =
      availableTrustSystems.length === 1
        ? availableTrustSystems.includes(profileTrustSystem)
          ? profileTrustSystem
          : undefined
        : availableTrustSystems.includes(defaultTrustSystem ?? "")
          ? defaultTrustSystem
          : undefined;
    const issuanceProfile = findIssuanceProfile(issuanceProfileId);
    const trustSystem = selectedTrustSystem;
    const credentialKeyId = trustSystem
      ? signingKeyIds?.[trustSystem] || signingKeyIds?.Default
      : signingKeyIds?.Default;
    const hasKeyMismatch =
      statusList &&
      credentialKeyId &&
      credentialKeyId !== statusList.signingKeyId;

    const issuers = Object.fromEntries(
      (published
        ? issuerDefinitions
        : issuerDefinitions.filter((i) => settings.issuerIds.includes(i.id))
      ).map((i) => [i.id, i]),
    );

    useEffect(() => {
      if (published || availableTrustSystems.length !== 1) return;

      const [onlyTrustSystem] = availableTrustSystems;
      const profile = profileOptions.find(
        (option) => option.trustSystem === onlyTrustSystem,
      );
      if (!onlyTrustSystem || !profile) return;

      form.setFieldValue(
        "issuingSettings.defaultTrustSystem",
        normalizeConfiguredTrustSystem(onlyTrustSystem),
      );
      if (!profileOptions.some((option) => option.id === issuanceProfileId)) {
        form.setFieldValue("issuingSettings.issuanceProfileId", profile.id);
      }
    }, [
      availableTrustSystems,
      form,
      issuanceProfileId,
      profileOptions,
      published,
    ]);

    function selectIssuer(value: string) {
      const selected = issuers[Number.parseInt(value, 10)];
      const trustSystems = trustSystemsForIdentity(selected);
      const available =
        trustSystems.length > 0 ? trustSystems : [TrustSystem.Default];
      const profile = compatibleIssuanceProfiles(available)[0];
      form.setFieldValue("issuingSettings.issuerId", Number.parseInt(value, 10));
      form.setFieldValue(
        "issuingSettings.defaultTrustSystem",
        available.length === 1
          ? normalizeConfiguredTrustSystem(available[0])
          : undefined,
      );
      form.setFieldValue("issuingSettings.signingKeyIds", {});
      if (profile) {
        form.setFieldValue("issuingSettings.issuanceProfileId", profile.id);
      }
    }

    function selectTrustSystem(value: string) {
      const profile = profileOptions.find(
        (option) => option.trustSystem === value,
      );
      if (!profile) return;
      form.setFieldValue(
        "issuingSettings.defaultTrustSystem",
        normalizeConfiguredTrustSystem(value),
      );
      form.setFieldValue("issuingSettings.signingKeyIds", {});
      form.setFieldValue("issuingSettings.issuanceProfileId", profile.id);
    }

    return (
      <Card className="space-y-4 p-4 sm:space-y-6 sm:p-6">
        <h3 className="text-2xl leading-none font-semibold">
          <FormattedMessage
            id="pages.credentialSchemas.tabs.issuingSettings"
            defaultMessage="Issuing Settings"
          />
        </h3>
        <div className="flex flex-col divide-y rounded-2xl border bg-background *:p-4 sm:*:p-6">
          <div className="space-y-4">
            <h4 className="text-xl font-semibold">
              <FormattedMessage id="common.general" defaultMessage="General" />
            </h4>
            <div className="flex flex-col gap-3 lg:w-2/3">
              <form.AppField name="issuingSettings.issuerId">
                {(field) => {
                  const isInvalid =
                    field.state.meta.isTouched && !field.state.meta.isValid;
                  return (
                    <Field data-invalid={isInvalid} orientation="horizontal">
                      <FieldLabel htmlFor={field.name}>
                        <FormattedMessage
                          id="common.issuer"
                          defaultMessage="Identity"
                        />
                      </FieldLabel>
                      {published ? (
                        <IssuerItem
                          issuer={issuers[field.state.value]}
                          fallbackLanguage={settings.defaultLanguage}
                        />
                      ) : (
                          <Select
                            value={field.state.value.toString()}
                            onValueChange={selectIssuer}
                        >
                          <SelectTrigger
                            id={field.name}
                            aria-invalid={isInvalid}
                          >
                            <SelectValue
                              placeholder={$t(
                                {
                                  id: "common.select.withValue",
                                  defaultMessage: "Select {value}",
                                },
                                {
                                  value: $t({
                                    id: "common.issuer",
                                    defaultMessage: "Identity",
                                  }),
                                },
                              )}
                            />
                          </SelectTrigger>
                          <SelectContent>
                            {Object.values(issuers).map((issuerDefinition) => (
                              <SelectItem
                                value={issuerDefinition.id.toString()}
                                key={issuerDefinition.id}
                              >
                                <IssuerItem
                                  issuer={issuerDefinition}
                                  fallbackLanguage={settings.defaultLanguage}
                                />
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      )}
                      {isInvalid && (
                        <FieldError errors={field.state.meta.errors} />
                      )}
                    </Field>
                  );
                }}
              </form.AppField>
              <form.AppField name="issuingSettings.issuanceProfileId">
                {(field) => (
                  <input
                    type="hidden"
                    name={field.name}
                    value={field.state.value}
                  />
                )}
              </form.AppField>
              <Field orientation="horizontal">
                <FieldLabel htmlFor="issuanceTrustSystem">
                  Trust framework
                </FieldLabel>
                {published || availableTrustSystems.length === 1 ? (
                  <div className="text-sm">
                    {trustFrameworkDisplayName(selectedTrustSystem, selectedIssuer)}
                  </div>
                ) : (
                  <Select
                    value={selectedTrustSystem ?? ""}
                    onValueChange={selectTrustSystem}
                  >
                    <SelectTrigger id="issuanceTrustSystem">
                      <SelectValue placeholder="Select trust framework" />
                    </SelectTrigger>
                    <SelectContent>
                      {availableTrustSystems.map((value) => (
                        <SelectItem key={value} value={value}>
                          {trustFrameworkDisplayName(value, selectedIssuer)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              </Field>
              <Collapsible
                defaultOpen={false}
                className="group/profile border-t pt-2"
              >
                <CollapsibleTrigger
                  type="button"
                  className="flex w-full items-center justify-between text-left text-sm font-medium text-muted-foreground hover:text-foreground"
                >
                  Advanced profile
                  <IconChevronDown className="size-4 transition-transform group-data-[state=open]/profile:rotate-180" />
                </CollapsibleTrigger>
                <CollapsibleContent className="grid gap-3 pt-3">
                  {issuanceProfile && (
                    <p className="text-sm text-muted-foreground">
                      <EcosystemProfileLabel profile={issuanceProfile} identity={selectedIssuer} />
                    </p>
                  )}
                  <Select
                    value={profileOptions.some(
                      (option) => option.id === issuanceProfileId,
                    ) ? issuanceProfileId : ""}
                    disabled={published || availableTrustSystems.length === 0}
                    onValueChange={(value) => {
                      const option = profileOptions.find(
                        (candidate) => candidate.id === value,
                      );
                      if (!option) return;
                      form.setFieldValue(
                        "issuingSettings.issuanceProfileId",
                        option.id,
                      );
                      form.setFieldValue(
                        "issuingSettings.defaultTrustSystem",
                        normalizeConfiguredTrustSystem(option.trustSystem),
                      );
                      form.setFieldValue("issuingSettings.signingKeyIds", {});
                    }}
                  >
                    <SelectTrigger id="issuanceProfileId">
                      <SelectValue placeholder="Select profile" />
                    </SelectTrigger>
                    <SelectContent>
                      {profileOptions.map((option) => (
                        <SelectItem key={option.id} value={option.id}>
                          <EcosystemProfileLabel profile={option} identity={selectedIssuer} />
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </CollapsibleContent>
              </Collapsible>
              {issuanceProfile && (
                <Field orientation="horizontal">
                  <FieldLabel>
                    <FormattedMessage
                      id="ecosystemProfile.derivedValues"
                      defaultMessage="Derived by profile"
                    />
                  </FieldLabel>
                  <div className="text-sm text-muted-foreground">
                    {issuanceProfile.derivedValues.join(" · ")}
                  </div>
                </Field>
              )}
              {configuredTrustSystems.map((trustSystem) => {
                const keys = signingKeys;
                if (keys.length === 0) return null;
                return (
                  <form.AppField
                    key={trustSystem}
                    name={`issuingSettings.signingKeyIds.${trustSystem}`}
                  >
                    {(field) => (
                      <Field orientation="horizontal">
                        <FieldLabel htmlFor={field.name}>
                          {trustSystem} credential key
                        </FieldLabel>
                        {published ? (
                          <div className="text-sm">
                            {field.state.value || "Identity default"}
                          </div>
                        ) : (
                          <Select
                            value={field.state.value || "__default__"}
                            onValueChange={(value) =>
                              field.handleChange(
                                value === "__default__" ? undefined : value,
                              )
                            }
                          >
                            <SelectTrigger id={field.name}>
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="__default__">
                                Identity default key
                              </SelectItem>
                              {keys.map((key) => (
                                <SelectItem key={key.keyId} value={key.keyId}>
                                  {key.keyId} · {key.algorithm}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        )}
                      </Field>
                    )}
                  </form.AppField>
                );
              })}
              <form.AppField name="issuingSettings.statusListId">
                {(field) => (
                  <Field orientation="horizontal">
                    <FieldLabel htmlFor={field.name}>Status list</FieldLabel>
                    {published ? (
                      <div className="text-sm">
                        {statusList?.name || "None"}
                      </div>
                    ) : (
                      <Select
                        value={field.state.value || "__none__"}
                        onValueChange={(value) =>
                          field.handleChange(
                            value === "__none__" ? undefined : value,
                          )
                        }
                      >
                        <SelectTrigger id={field.name}>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="__none__">None</SelectItem>
                          {statusLists.map((item) => (
                            <SelectItem key={item.id} value={item.id}>
                              {item.name} · {item.signingKeyId}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                    {hasKeyMismatch && (
                      <p className="text-sm text-amber-700 dark:text-amber-400">
                        The status list uses {statusList.signingKeyId}; the
                        credential uses a different key.
                      </p>
                    )}
                  </Field>
                )}
              </form.AppField>
              <form.AppField name="issuingSettings.credentialOfferType">
                {(field) => (
                  <Field orientation="horizontal">
                    <FieldLabel htmlFor={field.name}>
                      Credential offer
                    </FieldLabel>
                    {published ? (
                      <div className="text-sm">
                        {field.state.value === CredentialOfferType.Uri
                          ? "URI (credential_offer_uri)"
                          : "Value (credential_offer)"}
                      </div>
                    ) : (
                      <Select
                        value={field.state.value || CredentialOfferType.Value}
                        onValueChange={(value) =>
                          field.handleChange(value as CredentialOfferType)
                        }
                      >
                        <SelectTrigger id={field.name}>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value={CredentialOfferType.Value}>
                            By value (credential_offer)
                          </SelectItem>
                          <SelectItem value={CredentialOfferType.Uri}>
                            By URI (credential_offer_uri)
                          </SelectItem>
                        </SelectContent>
                      </Select>
                    )}
                  </Field>
                )}
              </form.AppField>
              <form.AppField name="issuingSettings.keyType">
                {(field) => {
                  const isInvalid =
                    field.state.meta.isTouched && !field.state.meta.isValid;
                  return (
                    <Field orientation="horizontal" data-invalid={isInvalid}>
                      <FieldLabel htmlFor={field.name}>
                        <FormattedMessage
                          id="issuingSettings.keyType"
                          defaultMessage="Key Type"
                        />
                      </FieldLabel>
                      {published ? (
                        <div className="text-sm">
                          {displayNameForKeyType(field.state.value)}
                        </div>
                      ) : (
                        <Select
                          value={field.state.value}
                          onValueChange={(v) =>
                            field.handleChange(v as IssuerKeyTypes)
                          }
                        >
                          <SelectTrigger
                            id={field.name}
                            aria-invalid={isInvalid}
                          >
                            <SelectValue
                              placeholder={$t(
                                {
                                  id: "common.select.withValue",
                                  defaultMessage: "Select {value}",
                                },
                                {
                                  value: $t({
                                    id: "issuingSettings.keyType",
                                    defaultMessage: "Key Type",
                                  }),
                                },
                              )}
                            />
                          </SelectTrigger>
                          <SelectContent>
                            {Object.values(IssuerKeyTypes).map((kt) => (
                              <SelectItem value={kt} key={kt}>
                                {displayNameForKeyType(kt)}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      )}
                      {isInvalid && (
                        <FieldError errors={field.state.meta.errors} />
                      )}
                    </Field>
                  );
                }}
              </form.AppField>
              <form.AppField name="maxBatchSize">
                {(field) => {
                  const isInvalid =
                    field.state.meta.isTouched && !field.state.meta.isValid;
                  return (
                    <Field orientation="horizontal" data-invalid={isInvalid}>
                      <FieldLabel htmlFor={field.name}>
                        <FormattedMessage
                          id="issuingSettings.maxBatchSize"
                          defaultMessage="Maximum batch size"
                        />
                      </FieldLabel>
                      {published ? (
                        <div className="text-sm">{field.state.value}</div>
                      ) : (
                        <Input
                          id={field.name}
                          name={field.name}
                          type="number"
                          min={1}
                          step={1}
                          value={field.state.value}
                          onChange={(event) =>
                            field.handleChange(event.target.valueAsNumber)
                          }
                          onBlur={field.handleBlur}
                          aria-invalid={isInvalid}
                        />
                      )}
                      {isInvalid && (
                        <FieldError errors={field.state.meta.errors} />
                      )}
                    </Field>
                  );
                }}
              </form.AppField>
              <form.AppField
                name="issuingSettings.supportedCredentialTypes"
                mode="array"
              >
                {(field) => {
                  const isInvalid =
                    field.state.meta.isTouched && !field.state.meta.isValid;

                  return (
                    <FieldSet disabled={published}>
                      <FieldLegend variant="label">
                        <FormattedMessage
                          id="issuingSettings.supportedCredentialTypes"
                          defaultMessage="Supported Credential Types"
                        />
                      </FieldLegend>
                      {published ? (
                        <div className="flex gap-1">
                          {field.state.value.map((credentialType) => (
                            <Badge
                              variant="outline"
                              className="bg-surface"
                              key={credentialType}
                            >
                              {credentialType}
                            </Badge>
                          ))}
                        </div>
                      ) : (
                        <FieldGroup data-slot="checkbox-group">
                          {Object.values(SupportedCredentialTypes).map(
                            (credentialType) => (
                              <Field
                                key={credentialType}
                                orientation="inline"
                                data-invalid={isInvalid}
                              >
                                <Checkbox
                                  id={`supported-credential-type-${credentialType}`}
                                  name={field.name}
                                  aria-invalid={isInvalid}
                                  checked={field.state.value.includes(
                                    credentialType,
                                  )}
                                  onCheckedChange={(checked) => {
                                    if (checked) {
                                      field.pushValue(credentialType);
                                    } else {
                                      const index =
                                        field.state.value.indexOf(
                                          credentialType,
                                        );
                                      if (index > -1) {
                                        field.removeValue(index);
                                      }
                                    }
                                  }}
                                />
                                <FieldLabel
                                  htmlFor={`supported-credential-type-${credentialType}`}
                                  className="font-normal"
                                >
                                  {credentialType}
                                </FieldLabel>
                              </Field>
                            ),
                          )}
                        </FieldGroup>
                      )}
                      {isInvalid && (
                        <FieldError errors={field.state.meta.errors} />
                      )}
                    </FieldSet>
                  );
                }}
              </form.AppField>
            </div>
          </div>
          <div className="space-y-4">
            <h4 className="text-xl font-semibold">
              <FormattedMessage
                id="issuingSettings.sections.mdoc"
                defaultMessage="mdoc"
              />
            </h4>
            <div className="flex flex-col gap-3 lg:w-2/3">
              <form.AppField name="issuingSettings.doctype">
                {(field) => {
                  const isInvalid =
                    field.state.meta.isTouched && !field.state.meta.isValid;
                  return (
                    <Field data-invalid={isInvalid} orientation="horizontal">
                      <FieldLabel htmlFor={field.name}>
                        <FormattedMessage
                          id="issuingSettings.doctype"
                          defaultMessage="Doctype"
                        />
                      </FieldLabel>
                      {published ? (
                        <div className="text-sm">
                          {field.state.value || <>&nbsp;</>}
                        </div>
                      ) : (
                        <Input
                          id={field.name}
                          name={field.name}
                          value={field.state.value}
                          onChange={(e) => field.handleChange(e.target.value)}
                          onBlur={field.handleBlur}
                          aria-invalid={isInvalid}
                        />
                      )}
                      {isInvalid && (
                        <FieldError errors={field.state.meta.errors} />
                      )}
                    </Field>
                  );
                }}
              </form.AppField>
              <form.AppField name="issuingSettings.namespace">
                {(field) => {
                  const isInvalid =
                    field.state.meta.isTouched && !field.state.meta.isValid;
                  return (
                    <Field data-invalid={isInvalid} orientation="horizontal">
                      <FieldLabel htmlFor={field.name}>
                        <FormattedMessage
                          id="issuingSettings.namespace"
                          defaultMessage="Namespace"
                        />
                      </FieldLabel>
                      {published ? (
                        <div className="text-sm">
                          {field.state.value || <>&nbsp;</>}
                        </div>
                      ) : (
                        <Input
                          id={field.name}
                          name={field.name}
                          value={field.state.value}
                          onChange={(e) => field.handleChange(e.target.value)}
                          onBlur={field.handleBlur}
                          aria-invalid={isInvalid}
                        />
                      )}
                      {isInvalid && (
                        <FieldError errors={field.state.meta.errors} />
                      )}
                    </Field>
                  );
                }}
              </form.AppField>
            </div>
          </div>
          <div className="space-y-4">
            <h4 className="text-xl font-semibold">
              <FormattedMessage
                id="issuingSettings.sections.sdJwt"
                defaultMessage="SD-JWT"
              />
            </h4>
            <form.AppField name="issuingSettings.vct">
              {(field) => {
                const isInvalid =
                  field.state.meta.isTouched && !field.state.meta.isValid;
                return (
                  <Field
                    orientation="horizontal"
                    className="lg:w-2/3"
                    data-invalid={isInvalid}
                  >
                    <FieldLabel htmlFor={field.name}>
                      <FormattedMessage
                        id="issuingSettings.vct"
                        defaultMessage="VCT"
                      />
                    </FieldLabel>
                    {published ? (
                      <div className="px-3 py-2 text-sm">
                        {field.state.value || <>&nbsp;</>}
                      </div>
                    ) : (
                      <Input
                        id={field.name}
                        name={field.name}
                        value={field.state.value}
                        onChange={(e) => field.handleChange(e.target.value)}
                        onBlur={field.handleBlur}
                        aria-invalid={isInvalid}
                      />
                    )}
                    {isInvalid && (
                      <FieldError errors={field.state.meta.errors} />
                    )}
                  </Field>
                );
              }}
            </form.AppField>
          </div>
        </div>
      </Card>
    );
  },
});
