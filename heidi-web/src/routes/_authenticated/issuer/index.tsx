// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconRubberStamp, IconSearch } from "@tabler/icons-react";
import { useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute, getRouteApi, Link, redirect } from "@tanstack/react-router";
import { useAtomValue } from "jotai";
import { Suspense } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { compare, valid } from "semver";
import { Loading } from "@/components/common/loading";
import { PageHeader } from "@/components/common/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  SegmentedControl,
  SegmentedControlItem,
} from "@/components/ui/segmented-control";
import { schemaListOptions } from "@/lib/api/credential-schemas/query-options";
import { selectedTenantAtom } from "@/lib/atoms";
import { cn } from "@/lib/utils";
import { argbToHex } from "@/lib/utils/color";
import { selectNewestSchemas } from "@/lib/utils/credential-schemas";
import { isOperator } from "@/lib/utils/user";
import { CredentialSchemaState, TextColor } from "@/types/credential-schema";

export const Route = createFileRoute("/_authenticated/issuer/")({
  beforeLoad: ({ context }) => {
    if (!isOperator(context.user)) {
      throw redirect({ to: "/" });
    }
  },
  loader: ({ context }) => {
    context.queryClient.ensureQueryData(
      schemaListOptions({
        statesToExclude: [
          CredentialSchemaState.Archived,
          CredentialSchemaState.Created,
        ],
      }),
    );
  },
  validateSearch: (
    search: Record<string, unknown>,
  ): { q?: string; filter?: "all" | "latest" } => {
    return {
      q: search.q as string,
      filter: (search.filter as "all" | "latest") || "latest",
    };
  },
  component: RouteComponent,
});

const routeApi = getRouteApi("/_authenticated/issuer");

function RouteComponent() {
  const navigate = Route.useNavigate();
  const { q: search = "", filter } = Route.useSearch();
  const { crumb } = routeApi.useLoaderData();
  const { $t } = useIntl();
  function setSearch(value: string) {
    navigate({ search: { q: value || undefined, filter } });
  }

  return (
    <>
      <PageHeader heading={crumb}>
        <div className="flex grow flex-wrap items-center gap-2 @lg:grow-0">
          <Input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={`${$t({ id: "common.search", defaultMessage: "Search" })}...`}
            icon={IconSearch}
            className="w-full min-w-52 @lg:w-auto"
          />
          <SegmentedControl
            className="w-full @lg:w-auto"
            onValueChange={(value) =>
              navigate({
                search: {
                  q: search || undefined,
                  filter: value as "all" | "latest",
                },
              })
            }
            value={filter}
          >
            <SegmentedControlItem value="latest">
              <FormattedMessage
                id="common.versions.latest"
                defaultMessage="Latest versions"
              />
            </SegmentedControlItem>
            <SegmentedControlItem value="all">
              <FormattedMessage
                id="common.versions.all"
                defaultMessage="Show all"
              />
            </SegmentedControlItem>
          </SegmentedControl>
        </div>
      </PageHeader>
      <Suspense fallback={<Loading />}>
        <PublishedSchemaList />
      </Suspense>
    </>
  );
}
function PublishedSchemaList() {
  const { q: search = "", filter } = Route.useSearch();
  const { data: publishedSchemas } = useSuspenseQuery(
    schemaListOptions({
      statesToExclude: [
        CredentialSchemaState.Archived,
        CredentialSchemaState.Created,
      ],
    }),
  );

  const selectedTenant = useAtomValue(selectedTenantAtom);

  const data = selectedTenant
    ? publishedSchemas.filter((s) => s.tenantId === selectedTenant)
    : publishedSchemas;

  const schemas =
    filter === "latest"
      ? selectNewestSchemas(data)
      : data.sort((a, b) =>
          valid(a.version) && valid(b.version)
            ? compare(a.version, b.version)
            : -1,
        );

  const filteredSchemas = schemas
    .sort((a, b) =>
      a.credentialIdentifier.localeCompare(b.credentialIdentifier),
    )
    .filter((s) =>
      search
        .trim()
        .toLowerCase()
        .split(" ")
        .every((search) =>
          `${s.credentialIdentifier} v${s.version}`
            .toLowerCase()
            .includes(search),
        ),
    );

  if (filteredSchemas.length === 0) {
    return (
      <Card className="mt-3 flex justify-center border-dashed border-muted-foreground/50 py-5 text-muted-foreground">
        {search ? (
          <p>
            <FormattedMessage
              id="credentialSchemas.notFound.withValue"
              defaultMessage="Could not find any Credential Schemas with the name {value}"
              values={{
                value: <span className="font-semibold">{search}</span>,
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
    <div className="mt-1 flex flex-col gap-4">
      {filteredSchemas.map((schema) => {
        const backgroundCard =
          schema.credentialSchemeStyleDetails[0]?.style.backgroundCard ?? "";
        return (
          <Card className="flex flex-wrap items-center gap-5" key={schema.id}>
            <div
              className="relative aspect-cc h-20 shrink-0 overflow-hidden rounded-lg border shadow-md"
              style={{
                backgroundColor: `#${argbToHex(
                  schema.credentialSchemeStyleDetails[0]?.style.cardColor,
                )}`,
              }}
            >
              {backgroundCard ? (
                <img
                  className="size-full object-cover"
                  src={backgroundCard}
                  alt={schema.displayName}
                />
              ) : (
                <img
                  src={
                    schema.credentialSchemeStyleDetails[0]?.style.textColor ===
                    TextColor.Dark
                      ? "/assets/card-overlay-light.svg"
                      : "/assets/card-overlay-dark.svg"
                  }
                  alt="Card Overlay"
                  className={cn("size-full object-cover")}
                />
              )}
            </div>
            <div className="flex flex-1 flex-col items-start gap-1">
              <h2 className="text-xl leading-none font-semibold">
                {schema.displayName || schema.credentialIdentifier}
              </h2>
              <p className="text-xs text-muted-foreground">
                {schema.credentialIdentifier}
              </p>
              <Badge variant="secondary">V {schema.version}</Badge>
            </div>
            <Button className="w-full sm:w-auto" asChild>
              <Link
                to={"/issuer/$schemaId/issue"}
                params={{
                  schemaId: schema.id,
                }}
              >
                <IconRubberStamp className="size-4" />
                <FormattedMessage id="common.issue" defaultMessage="Issue" />
              </Link>
            </Button>
          </Card>
        );
      })}
    </div>
  );
}
