// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconPencil } from "@tabler/icons-react";
import { useSuspenseQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { useAtomValue } from "jotai";
import { FormattedMessage } from "react-intl";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { integrationsListOptions } from "@/lib/api/integrations/query-options";
import { selectedTenantAtom } from "@/lib/atoms";
import { IntegrationDialog } from "@/routes/_authenticated/integrations/-components/integrations-dialog";

export function IntegrationsList({ integrationsSearch }: { integrationsSearch: string }) {
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const { data: integrations } = useSuspenseQuery(integrationsListOptions());

  const filteredIntegrations = integrations.filter(
    (s) =>
      (selectedTenant ? s.tenantId === selectedTenant : true) &&
      integrationsSearch
        .trim()
        .toLowerCase()
        .split(" ")
        .every((search) => `${s.displayName}`.toLowerCase().includes(search)),
  );

  if (filteredIntegrations.length === 0) {
    return (
      <Card className="mt-4 flex justify-center border-dashed border-muted-foreground/50 py-5 text-muted-foreground">
        {integrationsSearch ? (
          <p>
            <FormattedMessage
              id="integrations.notFound.withValue"
              defaultMessage="Could not find any Integrations with the name {value}"
              values={{
                value: (
                  <span className="font-semibold">{integrationsSearch}</span>
                ),
              }}
            />
          </p>
        ) : (
          <FormattedMessage
            id="integrations.notFound"
            defaultMessage="Could not find any Integrations"
          />
        )}
      </Card>
    );
  }

  return (
    <Table
      className="border-separate border-spacing-y-3"
      containerClassName="-mx-4 px-4 sm:-mx-8 sm:px-8 -mt-4 -mb-3"
    >
      <TableHeader>
        <TableRow className="*:border-b">
          <TableHead className="min-w-0 whitespace-nowrap">
            <FormattedMessage
              id="common.displayName"
              defaultMessage="Display Name"
            />
          </TableHead>
          <TableHead className="min-w-0 whitespace-nowrap">
            <FormattedMessage
              id="pages.credentialSchemas"
              defaultMessage="Credential Schemas"
            />
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {filteredIntegrations.map((integration) => (
          <TableRow
            key={integration.id}
            className="*:last-border-r rounded-2xl shadow-xs *:border-y *:bg-card *:p-4! *:align-middle *:first:rounded-l-2xl *:first:border-l *:last:rounded-r-2xl *:last:border-r"
          >
            <TableCell>
              <h2 className="truncate text-xl font-semibold">
                {integration.displayName}
              </h2>
            </TableCell>
            <TableCell>
              <div className="flex items-center justify-between gap-3">
                <p className="truncate text-sm font-medium">
                  {integration.credentialIdentifiers.length}{" "}
                  <FormattedMessage
                    id="pages.credentialSchemas"
                    defaultMessage="Credential Schemas"
                  />
                </p>
                <Button asChild className="ml-auto" variant="tertiary">
                  <Link
                    to="/integrations/$integrationId"
                    params={{ integrationId: integration.id }}
                  >
                    <FormattedMessage
                      id="common.viewDetails"
                      defaultMessage="View Details"
                    />
                  </Link>
                </Button>
                <IntegrationDialog defaultValues={integration}>
                  <Button variant="outline" size="icon">
                    <IconPencil />
                  </Button>
                </IntegrationDialog>
              </div>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
