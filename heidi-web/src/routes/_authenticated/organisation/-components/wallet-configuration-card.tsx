// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconPlus, IconTrash } from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { FormattedMessage } from "react-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Field,
  FieldDescription,
  FieldError,
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
import type {
  ClientConfiguration,
  Settings,
  SupportedWallet,
  WalletCatalogEntry,
} from "@/lib/api/settings/api";
import { useUpdateSettingsMutation } from "@/lib/api/settings/mutations";

const NO_DEFAULT_WALLET = "__none__";

type WalletFormValues = {
  name: string;
  logoUrl: string;
  supportedWallets: SupportedWallet[];
  defaultWallet: string;
};

const emptyWallet: SupportedWallet = {
  name: "",
  displayName: "",
  appIconUri: "",
  universalLink: "",
};

const rasterDataIcon =
  /^data:image\/(?:png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function asString(value: unknown) {
  return typeof value === "string" ? value : "";
}

function readWalletConfiguration(
  clientConfiguration: ClientConfiguration | null,
): WalletFormValues {
  const wallet = isRecord(clientConfiguration?.wallet)
    ? clientConfiguration.wallet
    : {};
  const rawSupportedWallets: unknown = wallet.supportedWallets;
  const supportedWallets = Array.isArray(rawSupportedWallets)
    ? rawSupportedWallets.filter(
        (candidate: unknown): candidate is SupportedWallet =>
          isRecord(candidate) &&
          ["name", "displayName", "appIconUri", "universalLink"].every(
            (key) => typeof candidate[key] === "string",
          ),
      )
    : [];

  return {
    name: asString(wallet.name),
    logoUrl: asString(wallet.logoUrl),
    supportedWallets,
    defaultWallet: asString(wallet.defaultWallet),
  };
}

function buildClientConfiguration(
  existingConfiguration: ClientConfiguration | null,
  values: WalletFormValues,
): ClientConfiguration {
  const existing = isRecord(existingConfiguration)
    ? existingConfiguration
    : {};
  const existingWallet = isRecord(existing.wallet) ? existing.wallet : {};
  const knownWalletKeys = new Set([
    "name",
    "logoUrl",
    "supportedWallets",
    "defaultWallet",
  ]);
  const wallet: Record<string, unknown> = Object.fromEntries(
    Object.entries(existingWallet).filter(([key]) => !knownWalletKeys.has(key)),
  );

  const name = values.name.trim();
  const logoUrl = values.logoUrl.trim();
  const defaultWallet = values.defaultWallet.trim();
  const supportedWallets = values.supportedWallets.map((supportedWallet) => ({
    name: supportedWallet.name.trim(),
    displayName: supportedWallet.displayName.trim(),
    appIconUri: supportedWallet.appIconUri.trim(),
    universalLink: supportedWallet.universalLink.trim(),
  }));

  if (name) wallet.name = name;
  if (logoUrl) wallet.logoUrl = logoUrl;
  if (supportedWallets.length > 0) wallet.supportedWallets = supportedWallets;
  if (defaultWallet) wallet.defaultWallet = defaultWallet;

  const configuration = Object.fromEntries(
    Object.entries(existing).filter(([key]) => key !== "wallet"),
  );
  if (Object.keys(wallet).length > 0) {
    configuration.wallet = wallet;
  }
  return configuration;
}

function isHttpsUrl(value: string) {
  try {
    return new URL(value).protocol === "https:";
  } catch {
    return false;
  }
}

function isSafeAppIconUri(value: string) {
  return rasterDataIcon.test(value) || isHttpsUrl(value);
}

function validateWalletConfiguration(values: WalletFormValues) {
  if (values.logoUrl && !isHttpsUrl(values.logoUrl)) {
    return "The wallet logo URL must be an HTTPS URL.";
  }

  for (const wallet of values.supportedWallets) {
    if (
      !wallet.name.trim() ||
      !wallet.displayName.trim() ||
      !wallet.appIconUri.trim() ||
      !wallet.universalLink.trim()
    ) {
      return "Complete all fields for every supported wallet.";
    }
    if (!isSafeAppIconUri(wallet.appIconUri) || !isHttpsUrl(wallet.universalLink)) {
      return "Wallet icons must use HTTPS or a raster image data URI, and universal links must use HTTPS.";
    }
  }

  const walletNames = values.supportedWallets
    .map((wallet) => wallet.name.trim())
    .filter(Boolean);
  if (new Set(walletNames).size !== walletNames.length) {
    return "Wallet identifiers must be unique.";
  }

  if (
    values.defaultWallet &&
    !values.supportedWallets.some(
      (wallet) => wallet.name.trim() === values.defaultWallet.trim(),
    )
  ) {
    return "The default wallet must be one of the supported wallets.";
  }

  return null;
}

export function WalletConfigurationCard({
  settings,
  catalog,
}: {
  settings: Settings;
  catalog: WalletCatalogEntry[];
}) {
  const [values, setValues] = useState(() =>
    readWalletConfiguration(settings.clientConfiguration),
  );
  const [usePlatformDefaults, setUsePlatformDefaults] = useState(
    settings.clientConfiguration === null,
  );
  const { mutateAsync: updateSettings, isPending } =
    useUpdateSettingsMutation({ showToast: false });

  useEffect(() => {
    setValues(readWalletConfiguration(settings.clientConfiguration));
    setUsePlatformDefaults(settings.clientConfiguration === null);
  }, [settings.clientConfiguration]);

  function updateValues(
    updater: (values: WalletFormValues) => WalletFormValues,
  ) {
    setUsePlatformDefaults(false);
    setValues((current) =>
      updater(
        usePlatformDefaults
          ? { ...current, supportedWallets: catalog }
          : current,
      ),
    );
  }

  function toggleCatalogWallet(wallet: WalletCatalogEntry, checked: boolean) {
    updateValues((current) => {
      const currentWallets = current.supportedWallets;
      const walletIndex = currentWallets.findIndex(
        (currentWallet) =>
          currentWallet.universalLink === wallet.universalLink,
      );
      if (checked && walletIndex === -1) {
        return {
          ...current,
          supportedWallets: [...currentWallets, wallet],
        };
      }
      if (!checked && walletIndex !== -1) {
        return {
          ...current,
          supportedWallets: currentWallets.filter(
            (_, index) => index !== walletIndex,
          ),
          defaultWallet:
            current.defaultWallet === currentWallets[walletIndex]?.name
              ? ""
              : current.defaultWallet,
        };
      }
      return current;
    });
  }

  const nextConfiguration = useMemo(
    () =>
      buildClientConfiguration(
        settings.clientConfiguration,
        usePlatformDefaults ? { ...values, supportedWallets: catalog } : values,
      ),
    [catalog, settings.clientConfiguration, usePlatformDefaults, values],
  );
  const hasChanges =
    !usePlatformDefaults &&
    JSON.stringify(nextConfiguration) !==
      JSON.stringify(settings.clientConfiguration ?? {});

  async function save() {
    const valuesToSave = usePlatformDefaults
      ? { ...values, supportedWallets: catalog }
      : values;
    const validationError = validateWalletConfiguration(valuesToSave);
    if (validationError) {
      toast.error(validationError);
      return;
    }

    try {
      await updateSettings({
        tenantId: settings.tenantId,
        thumbnail: null,
        clientConfiguration: nextConfiguration,
      });
      toast.success("Wallet configuration updated.");
    } catch (error) {
      toast.error("Wallet configuration was not updated.", {
        description: (error as Error).message,
      });
    }
  }

  async function restorePlatformDefaults() {
    try {
      await updateSettings({
        tenantId: settings.tenantId,
        thumbnail: null,
        clearClientConfiguration: true,
      });
      toast.success("Platform wallet defaults restored.");
    } catch (error) {
      toast.error("Platform wallet defaults were not restored.", {
        description: (error as Error).message,
      });
    }
  }

  return (
    <Card className="mt-4">
      <div>
        <h2 className="text-2xl font-semibold">
          <FormattedMessage
            id="pages.organisation.walletConfiguration.title"
            defaultMessage="Wallet apps"
          />
        </h2>
        <p className="mt-1.5 text-muted-foreground">
          <FormattedMessage
            id="pages.organisation.walletConfiguration.description"
            defaultMessage="The platform catalog is used by default. Select the wallet apps available to this organisation to save an organisation-specific override. This public configuration must not contain secrets."
          />
        </p>
      </div>

      <div className="mt-6 flex flex-col gap-4">
        <FieldSet>
          <FieldLegend variant="label">
            <FormattedMessage
              id="pages.organisation.walletConfiguration.catalog"
              defaultMessage="Platform wallet catalog"
            />
          </FieldLegend>
          <FieldDescription>
            {usePlatformDefaults ? (
              <FormattedMessage
                id="pages.organisation.walletConfiguration.catalogDefault"
                defaultMessage="These apps are active because this organisation has no override. Change a selection below to create an organisation-specific list."
              />
            ) : (
              <FormattedMessage
                id="pages.organisation.walletConfiguration.catalogOverride"
                defaultMessage="These selections are the apps shown by the web components for this organisation."
              />
            )}
          </FieldDescription>
          {catalog.length > 0 ? (
            <div className="flex flex-col gap-3">
              {catalog.map((wallet) => {
                const checked =
                  usePlatformDefaults ||
                  values.supportedWallets.some(
                    (currentWallet) =>
                      currentWallet.universalLink === wallet.universalLink,
                  );
                const id = `catalog-wallet-${wallet.name}`;
                return (
                  <Field key={wallet.universalLink} orientation="inline">
                    <Checkbox
                      id={id}
                      checked={checked}
                      onCheckedChange={(nextChecked) =>
                        toggleCatalogWallet(wallet, nextChecked === true)
                      }
                    />
                    <img
                      src={wallet.appIconUri}
                      alt=""
                      className="size-8 rounded-lg object-cover"
                    />
                    <FieldLabel htmlFor={id} className="font-normal">
                      {wallet.displayName}
                    </FieldLabel>
                  </Field>
                );
              })}
            </div>
          ) : (
            <FieldDescription>
              <FormattedMessage
                id="pages.organisation.walletConfiguration.catalogEmpty"
                defaultMessage="No catalog entries are currently available. The web components will show the generic wallet action until an override is configured."
              />
            </FieldDescription>
          )}
        </FieldSet>

        <Field>
          <FieldLabel htmlFor="wallet-name">
            <FormattedMessage
              id="pages.organisation.walletConfiguration.name"
              defaultMessage="Wallet name"
            />
          </FieldLabel>
          <Input
            id="wallet-name"
            value={values.name}
            onChange={(event) =>
              updateValues((current) => ({
                ...current,
                name: event.target.value,
              }))
            }
            placeholder="Heidi Wallet"
          />
          <FieldDescription>
            <FormattedMessage
              id="pages.organisation.walletConfiguration.nameDescription"
              defaultMessage="Overrides the wallet name shown in the instructions. The default is Heidi Wallet."
            />
          </FieldDescription>
        </Field>

        <Field>
          <FieldLabel htmlFor="wallet-logo-url">
            <FormattedMessage
              id="pages.organisation.walletConfiguration.logoUrl"
              defaultMessage="Wallet logo URL"
            />
          </FieldLabel>
          <Input
            id="wallet-logo-url"
            type="url"
            value={values.logoUrl}
            onChange={(event) =>
              updateValues((current) => ({
                ...current,
                logoUrl: event.target.value,
              }))
            }
            placeholder="https://example.org/wallet-icon.png"
          />
        </Field>

        <FieldSet>
          <FieldLegend variant="label">
            <FormattedMessage
              id="pages.organisation.walletConfiguration.supportedWallets"
              defaultMessage="Supported wallets"
            />
          </FieldLegend>
          <FieldDescription>
            <FormattedMessage
              id="pages.organisation.walletConfiguration.supportedWalletsDescription"
              defaultMessage="Optional app buttons shown to users. If none are configured, only the generic wallet action is shown."
            />
          </FieldDescription>

          <div className="flex flex-col gap-4">
            {values.supportedWallets.map((wallet, index) => (
              <div
                className="rounded-2xl border bg-background p-4"
                key={`wallet-${index.toString()}`}
              >
                <div className="grid gap-4 md:grid-cols-2">
                  <Field>
                    <FieldLabel htmlFor={`wallet-${index}-name`}>
                      <FormattedMessage
                        id="pages.organisation.walletConfiguration.identifier"
                        defaultMessage="Identifier"
                      />
                    </FieldLabel>
                    <Input
                      id={`wallet-${index}-name`}
                      value={wallet.name}
                      onChange={(event) =>
                        updateValues((current) => ({
                          ...current,
                          supportedWallets: current.supportedWallets.map(
                            (currentWallet, currentIndex) =>
                              currentIndex === index
                                ? { ...currentWallet, name: event.target.value }
                                : currentWallet,
                          ),
                        }))
                      }
                      placeholder="heidi"
                    />
                  </Field>
                  <Field>
                    <FieldLabel htmlFor={`wallet-${index}-display-name`}>
                      <FormattedMessage
                        id="pages.organisation.walletConfiguration.displayName"
                        defaultMessage="Display name"
                      />
                    </FieldLabel>
                    <Input
                      id={`wallet-${index}-display-name`}
                      value={wallet.displayName}
                      onChange={(event) =>
                        updateValues((current) => ({
                          ...current,
                          supportedWallets: current.supportedWallets.map(
                            (currentWallet, currentIndex) =>
                              currentIndex === index
                                ? {
                                    ...currentWallet,
                                    displayName: event.target.value,
                                  }
                                : currentWallet,
                          ),
                        }))
                      }
                      placeholder="Heidi Wallet"
                    />
                  </Field>
                  <Field>
                    <FieldLabel htmlFor={`wallet-${index}-icon-url`}>
                      <FormattedMessage
                        id="pages.organisation.walletConfiguration.iconUrl"
                        defaultMessage="App icon URL"
                      />
                    </FieldLabel>
                    <Input
                      id={`wallet-${index}-icon-url`}
                      type="url"
                      value={wallet.appIconUri}
                      onChange={(event) =>
                        updateValues((current) => ({
                          ...current,
                          supportedWallets: current.supportedWallets.map(
                            (currentWallet, currentIndex) =>
                              currentIndex === index
                                ? {
                                    ...currentWallet,
                                    appIconUri: event.target.value,
                                  }
                                : currentWallet,
                          ),
                        }))
                      }
                      placeholder="https://example.org/wallet-icon.png"
                    />
                  </Field>
                  <Field>
                    <FieldLabel htmlFor={`wallet-${index}-universal-link`}>
                      <FormattedMessage
                        id="pages.organisation.walletConfiguration.universalLink"
                        defaultMessage="Universal link"
                      />
                    </FieldLabel>
                    <Input
                      id={`wallet-${index}-universal-link`}
                      type="url"
                      value={wallet.universalLink}
                      onChange={(event) =>
                        updateValues((current) => ({
                          ...current,
                          supportedWallets: current.supportedWallets.map(
                            (currentWallet, currentIndex) =>
                              currentIndex === index
                                ? {
                                    ...currentWallet,
                                    universalLink: event.target.value,
                                  }
                                : currentWallet,
                          ),
                        }))
                      }
                      placeholder="https://wallet.example/open"
                    />
                  </Field>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  className="mt-4"
                  onClick={() =>
                    updateValues((current) => ({
                      ...current,
                      supportedWallets: current.supportedWallets.filter(
                        (_, currentIndex) => currentIndex !== index,
                      ),
                      defaultWallet:
                        current.defaultWallet === wallet.name
                          ? ""
                          : current.defaultWallet,
                    }))
                  }
                >
                  <IconTrash />
                  <FormattedMessage
                    id="pages.organisation.walletConfiguration.remove"
                    defaultMessage="Remove wallet"
                  />
                </Button>
              </div>
            ))}
          </div>

          <Button
            type="button"
            variant="outline"
            className="self-start"
            onClick={() =>
              updateValues((current) => ({
                ...current,
                supportedWallets: [...current.supportedWallets, { ...emptyWallet }],
              }))
            }
          >
            <IconPlus />
            <FormattedMessage
              id="pages.organisation.walletConfiguration.add"
              defaultMessage="Add wallet"
            />
          </Button>
        </FieldSet>

        <Field>
          <FieldLabel htmlFor="default-wallet">
            <FormattedMessage
              id="pages.organisation.walletConfiguration.default"
              defaultMessage="Default wallet"
            />
          </FieldLabel>
          <FieldDescription>
            <FormattedMessage
              id="pages.organisation.walletConfiguration.defaultDescription"
              defaultMessage="Optional wallet app opened directly on mobile. Leave empty to let the user choose."
            />
          </FieldDescription>
          <Select
            value={values.defaultWallet || NO_DEFAULT_WALLET}
            onValueChange={(value) =>
              updateValues((current) => ({
                ...current,
                defaultWallet: value === NO_DEFAULT_WALLET ? "" : value,
              }))
            }
          >
            <SelectTrigger id="default-wallet">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NO_DEFAULT_WALLET}>
                <FormattedMessage
                  id="pages.organisation.walletConfiguration.noDefault"
                  defaultMessage="No default"
                />
              </SelectItem>
              {values.supportedWallets
                .filter((wallet) => wallet.name.trim())
                .map((wallet, index) => (
                  <SelectItem
                    key={`wallet-option-${index.toString()}`}
                    value={wallet.name}
                  >
                    {wallet.displayName || wallet.name}
                  </SelectItem>
                ))}
            </SelectContent>
          </Select>
        </Field>

        <FieldError>
          {validateWalletConfiguration(values) ?? undefined}
        </FieldError>

        <div className="flex justify-end border-t pt-4">
          {!usePlatformDefaults && (
            <Button
              type="button"
              variant="outline"
              className="mr-auto"
              onClick={restorePlatformDefaults}
              disabled={isPending}
            >
              Use platform defaults
            </Button>
          )}
          <Button type="button" onClick={save} disabled={!hasChanges || isPending}>
            <FormattedMessage
              id="pages.organisation.walletConfiguration.save"
              defaultMessage="Save wallet configuration"
            />
          </Button>
        </div>
      </div>
    </Card>
  );
}
