// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconPencil, IconTrash, IconX } from "@tabler/icons-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { toast } from "sonner";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  deleteIssuerDefinition,
  type IssuerDefinition,
  type IssuerDisplayConfig,
  updateIssuerDefinition,
} from "@/lib/api/issuer-definitions/api";
import { DEFAULT_LOCALE } from "@/lib/constants";

export function IdentityActions({ identity, languages = [DEFAULT_LOCALE], fallbackLanguage = DEFAULT_LOCALE, onDeleted }: {
  identity: IssuerDefinition;
  languages?: string[];
  fallbackLanguage?: string;
  onDeleted: () => void;
}) {
  const { $t } = useIntl();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [slug, setSlug] = useState(identity.slug);
  const [customProfileName, setCustomProfileName] = useState(identity.customProfileName ?? "");
  const issuerDisplayDefaults = identity.issuerDisplay ?? [];
  const contentLanguages = Array.from(
    new Set([
      fallbackLanguage,
      ...languages,
      ...Object.keys(identity.displayName),
      ...issuerDisplayDefaults.map((entry) => entry.locale),
    ]),
  );
  const [displayName, setDisplayName] = useState(() =>
    Object.fromEntries(
      contentLanguages.map((language) => [language, identity.displayName[language] ?? ""]),
    ),
  );
  const [issuerDisplay, setIssuerDisplay] = useState<IssuerDisplayConfig[]>(
    issuerDisplayDefaults,
  );
  const [selectedDisplayLocale, setSelectedDisplayLocale] = useState(
    issuerDisplayDefaults[0]?.locale ?? fallbackLanguage,
  );
  const save = useMutation({
    mutationFn: () => updateIssuerDefinition({
      ...identity,
      slug,
      displayName,
      issuerDisplay,
      customProfileName: customProfileName.trim() || null,
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["issuer-definitions"] });
      setOpen(false);
      toast.success($t({ id: "issuerDefinition.update" }));
    },
    onError: (error) => toast.error($t({ id: "issuerDefinition.update.error" }), { description: error.message }),
  });
  const remove = useMutation({
    mutationFn: () => deleteIssuerDefinition(identity),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["issuer-definitions"] });
      onDeleted();
    },
    onError: (error) => toast.error($t({ id: "issuerDefinition.delete.error" }), { description: error.message }),
  });
  const hasName = Object.values(displayName).some((value) => value.trim());
  const issuerDisplayValid = issuerDisplay.every(
    (entry) => entry.locale.trim() && entry.name.trim(),
  );
  const selectedDisplay = issuerDisplay.find(
    (entry) => entry.locale === selectedDisplayLocale,
  );

  function toggleDisplayLocale(locale: string) {
    const existing = issuerDisplay.some((entry) => entry.locale === locale);
    if (existing) {
      const next = issuerDisplay.filter((entry) => entry.locale !== locale);
      setIssuerDisplay(next);
      if (selectedDisplayLocale === locale) {
        setSelectedDisplayLocale(next[0]?.locale ?? "");
      }
      return;
    }

    setIssuerDisplay((current) => [
      ...current,
      {
        locale,
        name: displayName[locale] ?? "",
        logoUri: identity.logo || "",
        logoAltText: "",
      },
    ]);
    setSelectedDisplayLocale(locale);
  }

  function updateDisplayLocale(
    locale: string,
    update: Partial<IssuerDisplayConfig>,
  ) {
    setIssuerDisplay((current) => current.map((entry) => (
      entry.locale === locale ? { ...entry, ...update } : entry
    )));
  }

  return <>
    <div className="mb-4 flex justify-end gap-2">
      <Button variant="outline" onClick={() => setOpen(true)}><IconPencil /> <FormattedMessage id="identity.actions.edit" /></Button>
      <AlertDialog>
        <AlertDialogTrigger asChild><Button variant="outline"><IconTrash /> <FormattedMessage id="common.delete" /></Button></AlertDialogTrigger>
        <AlertDialogContent>
          <AlertDialogHeader><AlertDialogTitle><FormattedMessage id="identity.actions.delete.title" /></AlertDialogTitle><AlertDialogDescription><FormattedMessage id="identity.actions.delete.description" /></AlertDialogDescription></AlertDialogHeader>
          <AlertDialogFooter><AlertDialogCancel><FormattedMessage id="common.cancel" /></AlertDialogCancel><AlertDialogAction disabled={remove.isPending} onClick={() => remove.mutate()}><FormattedMessage id="common.delete" /></AlertDialogAction></AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent>
        <DialogHeader><DialogTitle><FormattedMessage id="identity.actions.edit" /></DialogTitle><DialogDescription><FormattedMessage id="identity.actions.edit.description" /></DialogDescription></DialogHeader>
        <Field><FieldLabel htmlFor="identity-slug"><FormattedMessage id="common.slug" /></FieldLabel><Input id="identity-slug" value={slug} onChange={(event) => setSlug(event.target.value)} /></Field>
        {contentLanguages.map((locale) => <Field key={locale}>
          <FieldLabel htmlFor={`identity-name-${locale}`}><FormattedMessage id="identity.actions.displayName" /> {locale.toUpperCase()}</FieldLabel>
          <Input id={`identity-name-${locale}`} value={displayName[locale]} onChange={(event) => setDisplayName((current) => ({ ...current, [locale]: event.target.value }))} />
        </Field>)}
        <div className="space-y-3">
          <div>
            <h3 className="font-medium"><FormattedMessage id="identity.actions.issuerDisplay.title" /></h3>
            <p className="text-sm text-muted-foreground"><FormattedMessage id="identity.actions.issuerDisplay.description" /></p>
          </div>
          <div className="flex flex-wrap gap-2">
            {contentLanguages.map((locale) => {
              const configured = issuerDisplay.some((entry) => entry.locale === locale);
              const selected = selectedDisplayLocale === locale && configured;
              return <div key={locale} className="inline-flex items-center gap-1">
                <Button
                  type="button"
                  size="default"
                  variant={selected ? "default" : configured ? "secondary" : "outline"}
                  className="h-7 rounded-full px-2 text-xs"
                  aria-pressed={selected}
                  onClick={() => configured
                    ? setSelectedDisplayLocale(locale)
                    : toggleDisplayLocale(locale)}
                >
                  {locale.toUpperCase()}
                </Button>
                {configured && <Button
                  type="button"
                  size="icon"
                  variant="ghost"
                  className="size-6 rounded-full"
                  aria-label={$t(
                    { id: "identity.actions.issuerDisplay.removeLocale", defaultMessage: "Remove {locale}" },
                    { locale },
                  )}
                  onClick={() => toggleDisplayLocale(locale)}
                >
                  <IconX className="size-3" />
                </Button>}
              </div>;
            })}
          </div>
          {selectedDisplay && <>
            <Field>
              <FieldLabel htmlFor="identity-issuer-display-name"><FormattedMessage id="identity.actions.issuerDisplay.name" /></FieldLabel>
              <FieldDescription><FormattedMessage id="identity.actions.issuerDisplay.nameRequired" /></FieldDescription>
              <Input
                id="identity-issuer-display-name"
                value={selectedDisplay.name}
                onChange={(event) => updateDisplayLocale(selectedDisplay.locale, { name: event.target.value })}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="identity-issuer-display-logo"><FormattedMessage id="identity.actions.issuerDisplay.logoUri" /></FieldLabel>
              <Input
                id="identity-issuer-display-logo"
                value={selectedDisplay.logoUri ?? ""}
                onChange={(event) => updateDisplayLocale(selectedDisplay.locale, { logoUri: event.target.value })}
              />
            </Field>
            <Field>
              <FieldLabel htmlFor="identity-issuer-display-logo-alt"><FormattedMessage id="identity.actions.issuerDisplay.logoAltText" /></FieldLabel>
              <Input
                id="identity-issuer-display-logo-alt"
                value={selectedDisplay.logoAltText ?? ""}
                onChange={(event) => updateDisplayLocale(selectedDisplay.locale, { logoAltText: event.target.value })}
              />
            </Field>
          </>}
        </div>
        <Field>
          <FieldLabel htmlFor="identity-custom-profile-name"><FormattedMessage id="identity.actions.customProfileName" /></FieldLabel>
          <Input id="identity-custom-profile-name" value={customProfileName} onChange={(event) => setCustomProfileName(event.target.value)} />
          <FieldDescription><FormattedMessage id="identity.actions.customProfileName.description" /></FieldDescription>
        </Field>
        <DialogFooter><Button variant="outline" onClick={() => setOpen(false)}><FormattedMessage id="common.cancel" /></Button><Button disabled={!slug.trim() || !hasName || !issuerDisplayValid || save.isPending} onClick={() => save.mutate()}><FormattedMessage id="common.save" /></Button></DialogFooter>
      </DialogContent>
    </Dialog>
  </>;
}
