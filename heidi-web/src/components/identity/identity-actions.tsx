// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconPencil, IconTrash } from "@tabler/icons-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { toast } from "sonner";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { deleteIssuerDefinition, type IssuerDefinition, updateIssuerDefinition } from "@/lib/api/issuer-definitions/api";
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
  const contentLanguages = Array.from(
    new Set([fallbackLanguage, ...languages, ...Object.keys(identity.displayName)]),
  );
  const [displayName, setDisplayName] = useState(() =>
    Object.fromEntries(
      contentLanguages.map((language) => [language, identity.displayName[language] ?? ""]),
    ),
  );
  const save = useMutation({
    mutationFn: () => updateIssuerDefinition({
      ...identity,
      slug,
      displayName,
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
        <Field>
          <FieldLabel htmlFor="identity-custom-profile-name"><FormattedMessage id="identity.actions.customProfileName" /></FieldLabel>
          <Input id="identity-custom-profile-name" value={customProfileName} onChange={(event) => setCustomProfileName(event.target.value)} />
          <FieldDescription><FormattedMessage id="identity.actions.customProfileName.description" /></FieldDescription>
        </Field>
        <DialogFooter><Button variant="outline" onClick={() => setOpen(false)}><FormattedMessage id="common.cancel" /></Button><Button disabled={!slug.trim() || !hasName || save.isPending} onClick={() => save.mutate()}><FormattedMessage id="common.save" /></Button></DialogFooter>
      </DialogContent>
    </Dialog>
  </>;
}
