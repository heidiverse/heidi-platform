// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconArchive,
  IconCopy,
  IconCornerDownRight,
  IconDots,
  IconEye,
  IconFileExport,
  IconPencil,
} from "@tabler/icons-react";
import { useSuspenseQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { useAtomValue } from "jotai";
import { Fragment } from "react/jsx-runtime";
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
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { getSchema } from "@/lib/api/credential-schemas/api";
import { useArchiveAllSchemasWithIdentifierMutation } from "@/lib/api/credential-schemas/mutations";
import { schemaListOptions } from "@/lib/api/credential-schemas/query-options";
import { localeAtom, selectedTenantAtom } from "@/lib/atoms";
import { UserRole } from "@/lib/auth/identity";
import { useUser } from "@/lib/hooks/use-user";
import { cn, dateFormatter } from "@/lib/utils";
import { argbToHex, hexToArgb } from "@/lib/utils/color";
import { selectNewestAndDraftSchema } from "@/lib/utils/credential-schemas";
import { isEditor } from "@/lib/utils/user";
import { Route } from "@/routes/_authenticated/credential-schemas";
import { CredentialSchemaState, TextColor } from "@/types/credential-schema";
import { CopyToOrganisationDialog } from "./copy-to-organisation-dialog";

export function SchemasList() {
  const { $t } = useIntl();
  const user = useUser();
  const { data: credentialSchemaList } = useSuspenseQuery(
    schemaListOptions({
      statesToExclude: [CredentialSchemaState.Archived],
      includeImages: false,
    }),
  );
  const { q: schemaSearch = "" } = Route.useSearch();
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const canEdit = isEditor(user);

  const data = selectedTenant
    ? credentialSchemaList.filter(
        (schema) => schema.tenantId === selectedTenant,
      )
    : credentialSchemaList;

  const groupedRelevantSchemas = selectNewestAndDraftSchema(data);

  const { mutate: archive } = useArchiveAllSchemasWithIdentifierMutation();

  const searchKeywords = schemaSearch
    .trim()
    .toLowerCase()
    .split(" ")
    .filter(Boolean);
  const filteredSchemas = !searchKeywords.length
    ? groupedRelevantSchemas
    : Object.fromEntries(
        Object.entries(groupedRelevantSchemas).filter(
          ([_, v]) =>
            searchKeywords.every((s) =>
              v.newestPublished?.displayName?.toLowerCase().includes(s),
            ) ||
            searchKeywords.every((s) =>
              v.newestDraft?.displayName?.toLowerCase().includes(s),
            ),
        ),
      );

  const orderedSchemas = Object.fromEntries(
    Object.entries(filteredSchemas).sort(([a], [b]) => a.localeCompare(b)),
  );

  const headers = [
    $t({ id: "common.title", defaultMessage: "Title" }),
    $t({ id: "common.createdOn", defaultMessage: "Created on" }),
    $t({ id: "common.lastEdit", defaultMessage: "Last edit" }),
    $t({ id: "common.version", defaultMessage: "Version" }),
    canEdit
      ? $t({ id: "common.publishedState", defaultMessage: "Published State" })
      : "",
  ];

  if (Object.keys(orderedSchemas).length === 0) {
    return (
      <Card className="mt-3 flex justify-center border-dashed border-muted-foreground/50 py-5 text-muted-foreground">
        {schemaSearch ? (
          <p>
            <FormattedMessage
              id="credentialSchemas.notFound.withValue"
              defaultMessage="Could not find any Credential Schemas with the name {value}"
              values={{
                value: <span className="font-semibold">{schemaSearch}</span>,
              }}
            />
          </p>
        ) : (
          <FormattedMessage
            id="credentialSchemas.notFound"
            defaultMessage="Could not find any Credential Schemas"
          />
        )}
      </Card>
    );
  }

  return (
    <Table
      className="border-separate border-spacing-y-0"
      containerClassName="-mx-4 px-4 sm:-mx-8 sm:px-8"
    >
      <TableHeader>
        <TableRow>
          {headers.map((header) => (
            <TableHead
              className="min-w-0 border-b pr-2 pl-4 whitespace-nowrap"
              key={header}
            >
              {header}
            </TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {Object.entries(orderedSchemas).map(
          ([credentialIdentifier, { newestPublished, newestDraft }]) => {
            const schemas = [newestPublished, newestDraft].filter((s) => !!s);
            return schemas.map((schema, index) => {
              const isPublished =
                schema.state === CredentialSchemaState.Published;

              const {
                style = {
                  textColor: TextColor.Light,
                  cardColor: hexToArgb("#000000"),
                },
              } = schema.credentialSchemeStyleDetails?.[0] ?? {};

              const rowClasses = cn(
                "*:last-border-r rounded-2xl shadow-xs *:w-0 *:bg-card *:py-4! *:pr-2! *:pl-4! *:align-middle *:first:w-auto *:first:border-l *:last:border-r *:last:pr-4!",
                schemas.length > 1
                  ? index === 0
                    ? "*:border-t *:pb-0! *:first:rounded-tl-2xl *:last:rounded-tr-2xl"
                    : "*:border-b *:pt-2! *:first:rounded-bl-2xl *:last:rounded-br-2xl"
                  : "*:border-y *:first:rounded-l-2xl *:last:rounded-r-2xl",
              );

              return (
                <Fragment key={schema.id}>
                  {index !== 1 && <TableRow aria-hidden className="h-3" />}
                  <TableRow className={rowClasses}>
                    <TableCell>
                      {index === 0 ? (
                        <div className="flex items-center gap-4">
                          <img
                            src={
                              style.textColor === TextColor.Dark
                                ? "/assets/card-overlay-light.svg"
                                : "/assets/card-overlay-dark.svg"
                            }
                            alt="Card Overlay"
                            style={{
                              backgroundColor: `#${argbToHex(style.cardColor)}`,
                            }}
                            className="aspect-cc h-11 shrink-0 rounded-lg border object-cover shadow-md"
                          />
                          <div className="overflow-hidden">
                            <h2 className="truncate text-xl font-semibold">
                              {schema.displayName}
                            </h2>
                            <h3 className="text-xs text-muted-foreground">
                              {schema.credentialIdentifier}
                            </h3>
                          </div>
                        </div>
                      ) : (
                        <IconCornerDownRight className="ml-6" />
                      )}
                    </TableCell>
                    <DateCell date={schema.createdOn} />
                    <DateCell date={schema.updatedAt} />
                    <TableCell>
                      <Badge variant="secondary">
                        {isPublished ? (
                          `V ${schema.version}`
                        ) : (
                          <FormattedMessage
                            id="common.draft"
                            defaultMessage="Draft"
                          />
                        )}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {!isPublished && !canEdit ? null : (
                        <div className="flex justify-between gap-2">
                          <Button
                            variant={isPublished ? "secondary" : "tertiary"}
                            className="justify-start"
                            asChild
                          >
                            <Link
                              to="/credential-schemas/$schemaId"
                              params={{
                                schemaId: schema.id,
                              }}
                            >
                              {isPublished ? <IconEye /> : <IconPencil />}
                              {isPublished ? (
                                <FormattedMessage
                                  id="common.published"
                                  defaultMessage="Published"
                                />
                              ) : (
                                <FormattedMessage
                                  id="common.edit.withValue"
                                  defaultMessage="Edit {value}"
                                  values={{
                                    value: $t({
                                      id: "common.draft",
                                      defaultMessage: "Draft",
                                    }),
                                  }}
                                />
                              )}
                            </Link>
                          </Button>
                          {index === 0 && canEdit ? (
                            <DropdownMenu>
                              <DropdownMenuTrigger asChild>
                                <Button
                                  variant="ghost"
                                  className="size-10 shrink-0 p-0"
                                >
                                  <IconDots />
                                </Button>
                              </DropdownMenuTrigger>
                              <DropdownMenuContent align="end">
                                <DropdownMenuLabel>
                                  {schema.displayName ||
                                    schema.credentialIdentifier}
                                </DropdownMenuLabel>
                                <DropdownMenuSeparator />
                                <AlertDialog>
                                  <AlertDialogTrigger asChild>
                                    <DropdownMenuItem
                                      onSelect={(e) => e.preventDefault()}
                                      variant="destructive"
                                    >
                                      <IconArchive />
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
                                          id="pages.crdentialSchemas.archiveDialog.title"
                                          defaultMessage="Are you absolutely sure?"
                                        />
                                      </AlertDialogTitle>
                                      <AlertDialogDescription>
                                        <FormattedMessage
                                          id="pages.crdentialSchemas.archiveDialog.description"
                                          defaultMessage="This action cannot be undone. This will permanently archive the Credential Schema {schema}"
                                          values={{
                                            schema: (
                                              <span className="inline-flex items-center gap-1 rounded-md border border-neutral-300 bg-muted p-1 px-1.5 text-xs leading-none shadow-xs">
                                                {schema.displayName}
                                                <span className="text-[0.625rem] opacity-75">
                                                  {schema.credentialIdentifier}
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
                                          archive({
                                            credentialIdentifier,
                                          })
                                        }
                                      >
                                        <IconArchive />
                                        <FormattedMessage
                                          id="common.archive"
                                          defaultMessage="Archive"
                                        />
                                      </AlertDialogAction>
                                    </AlertDialogFooter>
                                  </AlertDialogContent>
                                </AlertDialog>
                                {user.roles.includes(UserRole.SuperAdmin) && (
                                  <CopyToOrganisationDialog schema={schema}>
                                    <DropdownMenuItem
                                      onSelect={(e) => e.preventDefault()}
                                    >
                                      <IconCopy />
                                      <FormattedMessage
                                        id="pages.crdentialSchemas.copyToOrganisation"
                                        defaultMessage="Copy To Organisation"
                                      />
                                    </DropdownMenuItem>
                                  </CopyToOrganisationDialog>
                                )}
                                <DropdownMenuItem
                                  onSelect={() => {
                                    toast.promise(
                                      async () => {
                                        const detailSchema = await getSchema({
                                          schemaId: schema.id,
                                        });
                                        const file = new Blob(
                                          [JSON.stringify(detailSchema)],
                                          { type: "application/json" },
                                        );
                                        const url = URL.createObjectURL(file);
                                        const link =
                                          document.createElement("a");
                                        link.href = url;
                                        link.download = `${schema.displayName}.json`;
                                        link.click();
                                      },
                                      {
                                        loading: `${$t({
                                          id: "common.exporting",
                                          defaultMessage: "Exporting",
                                        })}...`,
                                        success: $t({
                                          id: "common.exported",
                                          defaultMessage: "Exported",
                                        }),
                                        error: $t({
                                          id: "common.exportError",
                                          defaultMessage: "Export Error",
                                        }),
                                      },
                                    );
                                  }}
                                >
                                  <IconFileExport />
                                  <FormattedMessage
                                    id="common.export"
                                    defaultMessage="Export"
                                  />
                                </DropdownMenuItem>
                              </DropdownMenuContent>
                            </DropdownMenu>
                          ) : (
                            <div />
                          )}
                        </div>
                      )}
                    </TableCell>
                  </TableRow>
                </Fragment>
              );
            });
          },
        )}
      </TableBody>
    </Table>
  );
}

export function DateCell({ date }: { date: number }) {
  const locale = useAtomValue(localeAtom);
  return (
    <TableCell>
      {date && (
        <Popover>
          <PopoverTrigger asChild>
            <Button variant="ghost" className="-m-2 h-auto p-2 font-medium">
              {dateFormatter.format(new Date(date))}
            </Button>
          </PopoverTrigger>
          <PopoverContent
            collisionPadding={16}
            className="w-auto rounded-full px-3 py-2 text-sm font-medium"
          >
            {Intl.DateTimeFormat(`${locale}-CH`, {
              dateStyle: "full",
              timeStyle: "medium",
            }).format(new Date(date))}
          </PopoverContent>
        </Popover>
      )}
    </TableCell>
  );
}
