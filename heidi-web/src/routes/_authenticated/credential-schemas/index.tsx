// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconPlus, IconSearch } from "@tabler/icons-react";
import { createFileRoute, getRouteApi } from "@tanstack/react-router";
import { Suspense } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { Loading } from "@/components/common/loading";
import { PageHeader } from "@/components/common/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { schemaListOptions } from "@/lib/api/credential-schemas/query-options";
import { useUser } from "@/lib/hooks/use-user";
import { isEditor } from "@/lib/utils/user";
import { CredentialSchemaState } from "@/types/credential-schema";
import { AddCredentialSchemaDialog } from "./-components/add-credential-schema-dialog";
import { SchemasList } from "./-components/schema-list";

export const Route = createFileRoute("/_authenticated/credential-schemas/")({
  loader: ({ context }) => {
    context.queryClient.ensureQueryData(
      schemaListOptions({
        statesToExclude: [CredentialSchemaState.Archived],
        includeImages: false,
      }),
    );
  },
  validateSearch: (search: Record<string, unknown>): { q?: string } => {
    return {
      q: search.q as string,
    };
  },
  component: RouteComponent,
});

const routeApi = getRouteApi("/_authenticated/credential-schemas");

function RouteComponent() {
  const navigate = Route.useNavigate();
  const { q: search = "" } = Route.useSearch();
  const { crumb } = routeApi.useLoaderData();
  const user = useUser();
  const { $t } = useIntl();
  function setSearch(value: string) {
    navigate({ search: { q: value || undefined }, replace: true });
  }
  const canEdit = isEditor(user);

  return (
    <>
      <PageHeader heading={crumb}>
        <div className="flex grow flex-wrap items-center gap-2 @lg:grow-0">
          <Input
            icon={IconSearch}
            value={search}
            className="w-full min-w-32 @lg:w-auto"
            onChange={(e) => setSearch(e.target.value)}
            placeholder={`${$t({ id: "common.search", defaultMessage: "Search" })}...`}
          />
          {canEdit && (
            <AddCredentialSchemaDialog>
              <Button className="w-full @lg:w-auto">
                <IconPlus />
                <FormattedMessage
                  id="common.new.withValue"
                  defaultMessage="New {value}"
                  values={{
                    pronoun: "neuter",
                    value: $t({
                      id: "common.credentialSchema",
                      defaultMessage: "Credential Schema",
                    }),
                  }}
                />
              </Button>
            </AddCredentialSchemaDialog>
          )}
        </div>
      </PageHeader>
      <Suspense fallback={<Loading />}>
        <SchemasList />
      </Suspense>
    </>
  );
}
