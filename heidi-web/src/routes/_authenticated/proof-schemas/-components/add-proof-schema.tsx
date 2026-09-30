// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconFileTypography,
  IconHash,
  IconPlus,
  IconTrash,
  IconVersions,
} from "@tabler/icons-react";
import {
  useQueries,
  useQueryClient,
  useSuspenseQuery,
} from "@tanstack/react-query";
import { useAtomValue } from "jotai";
import { useState } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { compare, valid } from "semver";
import { toast } from "sonner";
import { CredentialSchemaCombobox } from "@/components/common/credential-schema-combobox";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  schemaListOptions,
  schemaOptions,
} from "@/lib/api/credential-schemas/query-options";
import { selectedTenantAtom } from "@/lib/atoms";
import { omit } from "@/lib/utils";
import { argbToHex } from "@/lib/utils/color";
import type { SelectedCredentialSchema } from "@/routes/_authenticated/proof-schemas/$proofSchemaId";
import {
  CredentialOfferType,
  type CredentialSchema,
  CredentialSchemaState,
  IssuerKeyTypes,
  SupportedCredentialTypes,
  TextColor,
} from "@/types/credential-schema";
import {
  EcosystemProfileId,
  presentationProfileForIssuanceProfile,
} from "@/types/ecosystem-profile";

export function AddProofSchema({
  setIsDirty,
  selectedCredentialSchemas,
  setSelectedCredentialSchemas,
  presentationProfileId,
}: {
  setIsDirty: (d: boolean) => void;
  selectedCredentialSchemas: SelectedCredentialSchema[];
  setSelectedCredentialSchemas: React.Dispatch<
    React.SetStateAction<SelectedCredentialSchema[]>
  >;
  presentationProfileId?: string;
}) {
  const { $t } = useIntl();
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const [isCredentialSchemasComboboxOpen, setIsCredentialSchemasComboboxOpen] =
    useState(false);
  const queryClient = useQueryClient();

  const { data: credentialSchemasLite } = useSuspenseQuery({
    ...schemaListOptions({
      statesToExclude: [
        CredentialSchemaState.Archived,
        CredentialSchemaState.Created,
      ],
      includeImages: false,
    }),
    select(data) {
      const tenantSchemas = selectedTenant
        ? data.filter((schema) => schema.tenantId === selectedTenant)
        : data;
      if (!presentationProfileId) return tenantSchemas;
      return tenantSchemas.filter(
        (schema) =>
          presentationProfileForIssuanceProfile(
            schema.issuerSettings.issuanceProfileId,
          ) === presentationProfileId,
      );
    },
  });

  const data = useQueries({
    queries: selectedCredentialSchemas.map((s) =>
      schemaOptions({ schemaId: s.id }),
    ),
  });

  const detailSchemas = data
    .map((d) => d.data)
    .filter(Boolean) as CredentialSchema[];

  return (
    <>
      {selectedCredentialSchemas.map((credentialSchema) => {
        const detailSchema = detailSchemas.find(
          (d) => d.id === credentialSchema.id,
        );
        if (!detailSchema) return null;
        const schemasWithSwitchableVersions = credentialSchemasLite.filter(
          (c) =>
            c.credentialIdentifier === credentialSchema.credentialIdentifier &&
            c.version !== credentialSchema.version,
        );
        return (
          <div
            key={credentialSchema.id}
            className="mb-4 rounded-3xl border bg-background p-4"
          >
            <Card className="flex items-center gap-3">
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
                className="h-11 shrink-0 rounded"
              />
              <div className="mr-auto">
                <h3 className="text-xl font-semibold">
                  {credentialSchema.displayName ||
                    credentialSchema.credentialIdentifier}
                </h3>
                <Badge variant="outline">V {credentialSchema.version}</Badge>
              </div>
              {schemasWithSwitchableVersions.length > 0 && (
                <CredentialSchemaCombobox
                  schemas={schemasWithSwitchableVersions}
                  commandItemProps={(selectedSchema) => ({
                    onSelect: async () => {
                      const schemaPromise = queryClient.ensureQueryData(
                        schemaOptions({ schemaId: selectedSchema.id }),
                      );
                      toast.promise(schemaPromise, {
                        loading: $t({
                          id: "pages.proofSchemas.credentialSchema.loading",
                          defaultMessage: "Loading Credential Schema...",
                        }),
                        error: $t({
                          id: "pages.proofSchemas.credentialSchema.loadingError",
                          defaultMessage: "Error loading Credential Schema",
                        }),
                      });
                      const selectedDetailSchema = await schemaPromise;
                      setSelectedCredentialSchemas((prev) => {
                        return prev.map((schema) => {
                          if (
                            schema.credentialIdentifier ===
                            credentialSchema.credentialIdentifier
                          ) {
                            return {
                              id: selectedDetailSchema.id,
                              credentialIdentifier:
                                selectedDetailSchema.credentialIdentifier,
                              version: selectedDetailSchema.version,
                              displayName: selectedDetailSchema.displayName,
                              issuerSettings: selectedDetailSchema.issuerSettings,
                              attributes:
                                selectedDetailSchema.attributes.filter((a) =>
                                  schema.attributes.some(
                                    (b) => b.name === a.name,
                                  ),
                                ),
                            };
                          }
                          return schema;
                        });
                      });
                      setIsDirty(true);
                    },
                  })}
                  buttonProps={{ variant: "outline" }}
                  initialShowLatest={false}
                >
                  <IconVersions />
                  <FormattedMessage
                    id="pages.proofSchemas.switchVersion"
                    defaultMessage="Switch Version"
                  />
                </CredentialSchemaCombobox>
              )}
              <Button
                className="size-10 p-0"
                variant="outline"
                onClick={() => {
                  setSelectedCredentialSchemas((prev) =>
                    prev.filter((s) => s.id !== credentialSchema.id),
                  );
                  setIsDirty(true);
                }}
              >
                <IconTrash className="size-4" />
              </Button>
            </Card>
            <Table safariFix={false} containerClassName="-m-4 p-4">
              <TableHeader>
                <TableRow>
                  <TableHead>
                    <div className="flex items-center gap-1">
                      <IconHash className="size-4" />
                      <FormattedMessage
                        id="attributes.key"
                        defaultMessage="Key"
                      />
                      *
                    </div>
                  </TableHead>
                  <TableHead>
                    <div className="flex items-center gap-1">
                      <IconFileTypography className="size-4" />
                      <FormattedMessage
                        id="attributes.type"
                        defaultMessage="Type"
                      />
                    </div>
                  </TableHead>
                  <TableHead className="w-10 min-w-0" />
                </TableRow>
              </TableHeader>
              <TableBody className="divide-y">
                {credentialSchema.attributes.map((attribute) => (
                  <TableRow
                    key={attribute.name}
                    className="*:p-0 *:align-middle"
                  >
                    <TableCell className="pl-4!">{attribute.name}</TableCell>
                    <TableCell className="pl-4!">{attribute.type}</TableCell>
                    <TableCell>
                      <Button
                        variant="outline"
                        size="icon"
                        className="m-1 size-8"
                        onClick={() => {
                          setSelectedCredentialSchemas((prev) => {
                            return prev.map((schema) => {
                              if (schema.id === credentialSchema.id) {
                                return {
                                  ...schema,
                                  attributes:
                                    credentialSchema.attributes.filter(
                                      (a) => a.id !== attribute.id,
                                    ),
                                };
                              }
                              return schema;
                            });
                          });
                          setIsDirty(true);
                        }}
                      >
                        <IconTrash className="size-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
                {credentialSchema.attributes.length !==
                  detailSchema.attributes.length && (
                  <TableRow>
                    <TableCell>
                      <Select
                        value=""
                        onValueChange={(id) => {
                          setSelectedCredentialSchemas((prev) => {
                            return prev.map((schema) => {
                              if (schema.id === credentialSchema.id) {
                                const attribute = detailSchema.attributes.find(
                                  (s) => s.id === Number(id),
                                );
                                if (attribute) {
                                  return {
                                    ...schema,
                                    attributes: [
                                      ...schema.attributes,
                                      attribute,
                                    ],
                                  };
                                }
                              }
                              return schema;
                            });
                          });
                          setIsDirty(true);
                        }}
                      >
                        <SelectTrigger>
                          {$t(
                            {
                              id: "common.select.withValue",
                              defaultMessage: "Select {value}",
                            },
                            {
                              value: $t({
                                id: "common.attribute",
                                defaultMessage: "Attribute",
                              }),
                            },
                          )}
                        </SelectTrigger>
                        <SelectContent>
                          {detailSchema.attributes.map(
                            (attribute) =>
                              !credentialSchema.attributes.some(
                                (a) => a.name === attribute.name,
                              ) && (
                                <SelectItem
                                  withIndicator={false}
                                  value={attribute.id.toString()}
                                  key={attribute.id}
                                >
                                  {attribute.name} – {attribute.type}
                                </SelectItem>
                              ),
                          )}
                        </SelectContent>
                      </Select>
                    </TableCell>
                    <TableCell />
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>
        );
      })}
      <CredentialSchemaCombobox
        buttonProps={{ variant: "tertiary" }}
        schemas={credentialSchemasLite.sort((a, b) =>
          valid(a.version) && valid(b.version)
            ? compare(a.version, b.version)
            : -1,
        )}
        popoverProps={{
          open: isCredentialSchemasComboboxOpen,
          onOpenChange: setIsCredentialSchemasComboboxOpen,
        }}
        commandItemProps={(schema) => {
          const safeSchema = omit(schema, ["createdOn", "updatedAt"]);
          return {
            onSelect: async () => {
              setSelectedCredentialSchemas((prev) => [
                ...prev,
                {
                  ...safeSchema,
                  attributes: [],
                  issuerSettings: {
                    issuerKeyType: IssuerKeyTypes.SOFTWARE_NO_AUTH,
                    issuanceProfileId:
                      EcosystemProfileId.CustomIssuance,
                    credentialOfferType: CredentialOfferType.Value,
                    doctype: null,
                    namespace: null,
                    vct: null,
                    id: 1,
                    supportedCredentialTypes: Object.values(
                      SupportedCredentialTypes,
                    ),
                  },
                  schemeMetadata: "",
                },
              ]);
              setIsDirty(true);
              setIsCredentialSchemasComboboxOpen(false);
            },
          };
        }}
      >
        <IconPlus />
        <FormattedMessage
          id="common.add.withValue"
          defaultMessage="Add {value}"
          values={{
            value: $t({
              id: "common.credentialSchema",
              defaultMessage: "Credential Schema",
            }),
          }}
        />
      </CredentialSchemaCombobox>
    </>
  );
}
