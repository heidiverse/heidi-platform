// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { Button } from "@/components/ui/button";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { getSchema } from "@/lib/api/credential-schemas/api";
import { useCopySchemaToOrganisationMutation } from "@/lib/api/credential-schemas/mutations";
import { organisationListOptions } from "@/lib/api/organisations/query-options";
import type { CredentialSchemaLite } from "@/types/credential-schema";

export function CopyToOrganisationDialog({
  children,
  schema,
}: {
  children: ReactNode;
  schema: CredentialSchemaLite;
}) {
  const [isOpen, setIsOpen] = useState(false);
  const [organisation, setOrganisation] = useState("");
  const { $t } = useIntl();

  const { mutate: copySchema, isPending } =
    useCopySchemaToOrganisationMutation();

  const { data: organisations } = useQuery({
    ...organisationListOptions(),
    enabled: isOpen,
  });

  async function handleSubmit() {
    if (!organisation) {
      return;
    }
    const detailSchema = await getSchema({ schemaId: schema.id });
    detailSchema.tenantId = organisation;

    copySchema(detailSchema);
  }

  return (
    <Dialog
      open={isOpen}
      onOpenChange={(open) => {
        setIsOpen(open);
        if (open) {
          setOrganisation("");
        }
      }}
    >
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            <FormattedMessage
              id="pages.credentialSchemas.copyToOrganisation.title"
              defaultMessage="Copy to Organisation"
            />
          </DialogTitle>
          <DialogDescription>
            <FormattedMessage
              id="pages.credentialSchemas.copyToOrganisation.description"
              defaultMessage="Copy this credential schema to another organisation"
            />
          </DialogDescription>
        </DialogHeader>
        <Select value={organisation} onValueChange={setOrganisation}>
          <SelectTrigger>
            <SelectValue
              placeholder={$t(
                {
                  id: "common.select.withValue",
                  defaultMessage: "Select {value}",
                },
                {
                  value: $t({
                    id: "common.organisation",
                    defaultMessage: "Organisation",
                  }),
                },
              )}
            />
          </SelectTrigger>
          <SelectContent>
            {organisations?.map((org) => (
              <SelectItem value={org.tenantId} key={org.tenantId}>
                {org.displayName}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <DialogFooter>
          <DialogClose asChild>
            <Button variant="outline">
              <FormattedMessage id="common.cancel" defaultMessage="Cancel" />
            </Button>
          </DialogClose>
          <Button
            disabled={!organisation || isPending}
            type="button"
            className="ml-auto"
            onClick={handleSubmit}
          >
            <FormattedMessage id="common.copy" defaultMessage="Copy" />
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
