// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconArchive,
  IconCopy,
  IconDots,
  IconPencil,
} from "@tabler/icons-react";
import { useSuspenseQueries } from "@tanstack/react-query";
import { getRouteApi, Link } from "@tanstack/react-router";
import { useAtomValue } from "jotai";
import { FormattedMessage, useIntl } from "react-intl";
import { toast } from "sonner";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { schemaListOptions } from "@/lib/api/credential-schemas/query-options";
import { useArchiveProofSchemaMutation } from "@/lib/api/proof-schemas/mutations";
import { proofSchemaListOptions } from "@/lib/api/proof-schemas/query-options";
import { selectedTenantAtom } from "@/lib/atoms";
import { useUser } from "@/lib/hooks/use-user";
import { argbToHex } from "@/lib/utils/color";
import { isEditor } from "@/lib/utils/user";
import { DateCell } from "@/routes/_authenticated/credential-schemas/-components/schema-list";
import { CredentialSchemaState, TextColor } from "@/types/credential-schema";

const routeApi = getRouteApi("/_authenticated/proof-schemas/");

export function ProofSchemaList() {
  const { $t } = useIntl();
  const [{ data: credentialSchemas }, { data: proofSchemas }] =
    useSuspenseQueries({
      queries: [
        schemaListOptions({
          statesToExclude: [
            CredentialSchemaState.Archived,
            CredentialSchemaState.Created,
          ],
          includeImages: false,
          isPublic: true,
        }),
        proofSchemaListOptions(),
      ],
    });
  const { mutate: archiveSchema } = useArchiveProofSchemaMutation();
  const { q: schemaSearch = "" } = routeApi.useSearch();
  const user = useUser();

  const selectedTenant = useAtomValue(selectedTenantAtom);

  // TODO: smarter way of handling filtering & remove null check once we dont need it anymore
  const filteredProofSchemas = proofSchemas.filter(
    (s) =>
      (selectedTenant ? s.tenantId === selectedTenant : true) &&
      schemaSearch
        .trim()
        .toLowerCase()
        .split(" ")
        .every((search) => `${s.title}`.toLowerCase().includes(search)),
  );

  if (filteredProofSchemas.length === 0) {
    return (
      <Card className="mt-3 flex justify-center border-dashed border-muted-foreground/50 py-5 text-muted-foreground">
        {schemaSearch ? (
          <FormattedMessage
            id="pages.proofSchemas.notFound.withValue"
            defaultMessage="Could not find any Proof Schemas with the name {value}"
            values={{
              value: <span className="font-semibold">{schemaSearch}</span>,
            }}
          />
        ) : (
          <FormattedMessage
            id="pages.proofSchemas.none"
            defaultMessage="No Proof Schemas"
          />
        )}
      </Card>
    );
  }

  const headers = [
    `${$t({ id: "common.title", defaultMessage: "Title" })} & ${$t({ id: "common.purpose", defaultMessage: "Purpose" })}`,
    $t({ id: "common.createdOn", defaultMessage: "Created on" }),
    $t({ id: "common.lastEdit", defaultMessage: "Last edit" }),
  ];

  return (
    <Table
      className="border-separate border-spacing-y-3"
      containerClassName="-mx-4 px-4 sm:-mx-8 sm:px-8 -mt-4 -mb-3"
    >
      <TableHeader>
        <TableRow className="*:border-b">
          {headers.map((header) => (
            <TableHead className="min-w-0 whitespace-nowrap" key={header}>
              {header}
            </TableHead>
          ))}
          <TableHead />
        </TableRow>
      </TableHeader>
      <TableBody>
        {filteredProofSchemas.map((proofSchema) => (
          <TableRow
            key={proofSchema.uuid}
            className="*:last-border-r rounded-2xl shadow-xs *:border-y *:bg-card *:p-4! *:first:rounded-l-2xl *:first:border-l *:last:rounded-r-2xl *:last:border-r"
          >
            <TableCell className="max-w-sm">
              <h2 className="-mt-1 truncate text-xl font-semibold">
                {proofSchema.title}
              </h2>
              <p className="mb-4 text-muted-foreground">
                {proofSchema.purpose}
              </p>
              <div className="flex flex-col gap-2">
                {proofSchema.credentialSchemes.map((credentialSchema) => {
                  const detailSchema = credentialSchemas.find(
                    (s) => s.id === credentialSchema.id,
                  );

                  if (!detailSchema) return null;
                  return (
                    <div
                      key={credentialSchema.id}
                      className="flex items-center gap-2"
                    >
                      <img
                        src={
                          detailSchema.credentialSchemeStyleDetails[0]?.style
                            .textColor === TextColor.Dark
                            ? "/assets/card-overlay-light.svg"
                            : "/assets/card-overlay-dark.svg"
                        }
                        alt="Card Overlay"
                        style={{
                          backgroundColor: `#${argbToHex(detailSchema.credentialSchemeStyleDetails[0]?.style.cardColor)}`,
                        }}
                        className="h-6 shrink-0 rounded-md shadow-sm"
                      />
                      <span className="truncate">
                        {credentialSchema.credentialIdentifier}
                      </span>
                      <Badge variant="secondary">
                        V {credentialSchema.version}
                      </Badge>
                    </div>
                  );
                })}
              </div>
            </TableCell>
            <DateCell date={proofSchema.createdAt} />
            <DateCell date={proofSchema.updatedAt} />
            <TableCell>
              <div className="flex items-start justify-end gap-2">
                <Button
                  onClick={() => {
                    toast.promise(
                      navigator.clipboard.writeText(proofSchema.uuid),
                      {
                        loading: $t({
                          id: "pages.proofSchemas.copy.loading",
                          defaultMessage: "Copying to Clipboard",
                        }),
                        success: $t({
                          id: "pages.proofSchemas.copy.success",
                          defaultMessage: "Copied to Clipboard",
                        }),
                        error: $t({
                          id: "pages.proofSchemas.copy.error",
                          defaultMessage: "Failed to copy to Clipboard",
                        }),
                      },
                    );
                  }}
                  variant="secondary"
                >
                  <IconCopy />
                  <FormattedMessage
                    id="pages.proofSchemas.copyId"
                    defaultMessage="Copy ID"
                  />
                </Button>
                {isEditor(user) && (
                  <>
                    <Button
                      asChild
                      variant="tertiary"
                      className="justify-self-end"
                    >
                      <Link
                        to="/proof-schemas/$proofSchemaId"
                        params={{
                          proofSchemaId: proofSchema.uuid,
                        }}
                      >
                        <IconPencil className="size-4" />
                        <FormattedMessage
                          id="common.edit"
                          defaultMessage="Edit"
                        />
                      </Link>
                    </Button>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button
                          variant="ghost"
                          className="size-10 shrink-0 p-0"
                        >
                          <IconDots className="size-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuLabel>
                          {proofSchema.title}
                        </DropdownMenuLabel>
                        <DropdownMenuSeparator />
                        <AlertDialog>
                          <AlertDialogTrigger asChild>
                            <DropdownMenuItem
                              onSelect={(e) => e.preventDefault()}
                              variant="destructive"
                            >
                              <IconArchive className="size-4" />
                              <FormattedMessage
                                id="common.archive"
                                defaultMessage="Archive"
                              />
                            </DropdownMenuItem>
                          </AlertDialogTrigger>
                          <AlertDialogContent>
                            <AlertDialogHeader>
                              <AlertDialogTitle>
                                <FormattedMessage
                                  id="pages.proofSchemas.archiveDialog.title"
                                  defaultMessage="Are you absolutely sure?"
                                />
                              </AlertDialogTitle>
                              <AlertDialogDescription>
                                <FormattedMessage
                                  id="pages.proofSchemas.archiveDialog.description"
                                  defaultMessage="This action cannot be undone. This will permanently archive the Proof Schema {schema}"
                                  values={{
                                    schema: (
                                      <span className="inline-flex items-center gap-1 rounded-md border border-neutral-300 bg-neutral-50 p-1 px-1.5 text-xs leading-none shadow-xs">
                                        {proofSchema.title}
                                        <span className="text-[0.625rem] opacity-75">
                                          {proofSchema.purpose}
                                        </span>
                                      </span>
                                    ),
                                  }}
                                />
                              </AlertDialogDescription>
                            </AlertDialogHeader>
                            <AlertDialogFooter>
                              <AlertDialogCancel>
                                <FormattedMessage
                                  id="common.cancel"
                                  defaultMessage="Cancel"
                                />
                              </AlertDialogCancel>
                              <AlertDialogAction
                                variant="destructive"
                                className="gap-2"
                                onClick={() =>
                                  archiveSchema({
                                    proofSchemaId: proofSchema.uuid,
                                  })
                                }
                              >
                                <IconArchive className="size-4" />
                                <FormattedMessage
                                  id="common.archive"
                                  defaultMessage="Archive"
                                />
                              </AlertDialogAction>
                            </AlertDialogFooter>
                          </AlertDialogContent>
                        </AlertDialog>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </>
                )}
              </div>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
