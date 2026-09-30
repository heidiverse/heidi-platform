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
import { proofSchemaListOptions } from "@/lib/api/proof-schemas/query-options";
import { useUser } from "@/lib/hooks/use-user";
import { isEditor } from "@/lib/utils/user";
import { CredentialSchemaState } from "@/types/credential-schema";
import { CreateOrUpdateDialog } from "./-components/create-or-update-dialog";
import { ProofSchemaList } from "./-components/proof-schema-list";

export const Route = createFileRoute("/_authenticated/proof-schemas/")({
  loader: ({ context: { queryClient } }) => {
    queryClient.ensureQueryData(
      schemaListOptions({
        statesToExclude: [
          CredentialSchemaState.Archived,
          CredentialSchemaState.Created,
        ],
        includeImages: false,
      }),
    );
    queryClient.ensureQueryData(proofSchemaListOptions());
  },
  validateSearch: (search: Record<string, unknown>): { q?: string } => {
    return {
      q: search.q as string,
    };
  },
  component: RouteComponent,
});

const routeApi = getRouteApi("/_authenticated/proof-schemas");

function RouteComponent() {
  const { crumb } = routeApi.useLoaderData();
  const navigate = Route.useNavigate();
  const { q: search = "" } = Route.useSearch();
  const user = useUser();
  function setSearch(value: string) {
    navigate({ search: { q: value || undefined }, replace: true });
  }
  const { $t } = useIntl();
  return (
    <div>
      <PageHeader heading={crumb}>
        <div className="flex grow flex-wrap items-center gap-2 @lg:grow-0">
          <Input
            icon={IconSearch}
            value={search}
            className="w-full @lg:w-auto"
            onChange={(e) => setSearch(e.target.value)}
            placeholder={`${$t({ id: "common.search", defaultMessage: "Search" })}...`}
          />
          {isEditor(user) && (
            <CreateOrUpdateDialog>
              <Button className="w-full @lg:w-auto">
                <IconPlus />
                <FormattedMessage
                  id="common.new.withValue"
                  defaultMessage="New {value}"
                  values={{
                    pronoun: "neuter",
                    value: $t({
                      id: "common.proofSchema",
                      defaultMessage: "Proof Schema",
                    }),
                  }}
                />
              </Button>
            </CreateOrUpdateDialog>
          )}
        </div>
      </PageHeader>
      <Suspense fallback={<Loading />}>
        <ProofSchemaList />
      </Suspense>
    </div>
  );
}
