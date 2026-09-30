// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { Disclosures } from "@heidiverse/heidi-web-components";
import { disclosuresToJsonPointer } from "@heidiverse/heidi-web-components/utils";
import { useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute, useLocation } from "@tanstack/react-router";
import { useAtomValue } from "jotai";
import { FormattedMessage, useIntl } from "react-intl";
import { toast } from "sonner";
import { Card } from "@/components/ui/card";
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
import { localeAtom } from "@/lib/atoms";
import { runtimeConfig } from "@/lib/runtime-config";
import { cn } from "@/lib/utils";
import { argbToHex } from "@/lib/utils/color";
import { transactionIdToEmojis } from "@/lib/utils/ticket";
import { type CredentialSchema, TextColor } from "@/types/credential-schema";

type TicketConnectionStatus = { disclosures: Disclosures | null };
export const Route = createFileRoute("/public/ticket")({
  component: RouteComponent,
});

function RouteComponent() {
  const { $t } = useIntl();
  const { hash } = useLocation();

  const { data: emojis } = useSuspenseQuery({
    queryKey: ["transaction-id-to-emojis", hash],
    queryFn: () => transactionIdToEmojis(hash),
  });

  const { data: disclosures } = useSuspenseQuery({
    queryKey: ["connection-info", hash],
    queryFn: async () => {
      const res = await fetch(
        new URL(
          `public/v1/transactions/${hash}/status`,
          runtimeConfig.heidiApiBaseUrl,
        ),
      );
      if (!res.ok) {
        toast.error(
          $t({
            id: "ticketingQueue.connectionInfo.error",
            defaultMessage: "Loading connection info failed",
          }),
        );
        return null;
      }
      const { disclosures } = (await res.json()) as TicketConnectionStatus;
      return disclosures;
    },
  });

  let content: React.ReactNode = null;

  if (!disclosures) {
    toast.error(
      $t({
        id: "ticketingQueue.noDisclosures",
        defaultMessage: "No disclosures available",
      }),
    );
  } else {
    const disclosureToJsonPointer = disclosuresToJsonPointer({ disclosures });
    const topLevelKey = Object.keys(disclosures)[0];
    const disclosure = topLevelKey ? disclosures[topLevelKey] : undefined;

    let credentialIdentifier: string | undefined;
    let version: string | undefined;

    if (disclosure?.schema_identifier) {
      const { credentialIdentifier: ci, version: v } =
        disclosure.schema_identifier as {
          credentialIdentifier: string;
          version: string;
        };
      credentialIdentifier = ci;
      version = v;
    } else {
      credentialIdentifier = topLevelKey
        ? topLevelKey.split("_")[0]
        : undefined;
      version = undefined;
    }

    if (!credentialIdentifier) {
      toast.error(
        $t({
          id: "ticketingQueue.invalidCredentialIdentifier",
          defaultMessage: "Invalid credential identifier",
        }),
      );
    } else if (version) {
      content = (
        <SchemaWithVersion
          disclosureToJsonPointer={disclosureToJsonPointer}
          credentialIdentifier={credentialIdentifier}
          version={version}
        />
      );
    } else {
      content = (
        <SchemaWithoutVersion
          credentialIdentifier={credentialIdentifier}
          disclosureToJsonPointer={disclosureToJsonPointer}
        />
      );
    }
  }

  return (
    <div className="m-auto flex h-dvh max-w-6xl flex-col items-center justify-center gap-12">
      <EmojiDisplay emojis={emojis} />
      {content}
    </div>
  );
}

function SchemaWithVersion({
  disclosureToJsonPointer,
  credentialIdentifier,
  version,
}: {
  disclosureToJsonPointer: Record<string, string>;
  credentialIdentifier: string;
  version: string;
}) {
  const { data: schema } = useSuspenseQuery(
    schemaOptions({ credentialIdentifier, version }, { isPublic: true }),
  );

  return (
    <SchemaRenderer
      schema={schema}
      disclosureToJsonPointer={disclosureToJsonPointer}
    />
  );
}

function SchemaWithoutVersion({
  credentialIdentifier,
  disclosureToJsonPointer,
}: {
  credentialIdentifier: string;
  disclosureToJsonPointer: Record<string, string>;
}) {
  const { $t } = useIntl();
  const { data: list } = useSuspenseQuery(
    schemaListOptions({ credentialIdentifier, isPublic: true }),
  );

  function compareVersions(a: string, b: string): number {
    return a.localeCompare(b, undefined, {
      numeric: true,
      sensitivity: "base",
    });
  }

  function getNewestSchema(list: { version: string }[] | undefined) {
    if (!list || list.length === 0) return null;
    return list
      .slice()
      .sort((a, b) => compareVersions(b.version, a.version))[0];
  }

  const newestSchema = getNewestSchema(list) as CredentialSchema | null;

  const { data: schema } = useSuspenseQuery(
    schemaOptions(
      {
        credentialIdentifier: newestSchema?.credentialIdentifier ?? "",
        version: newestSchema?.version ?? "",
      },
      { isPublic: true },
    ),
  );

  if (!list || list.length === 0) {
    return toast.error(
      $t({
        id: "credentialSchemas.notFound",
        defaultMessage: "Could not find any Credential Schemas",
      }),
    );
  }
  return (
    <SchemaRenderer
      schema={schema}
      disclosureToJsonPointer={disclosureToJsonPointer}
    />
  );
}

function SchemaRenderer({
  schema,
  disclosureToJsonPointer,
}: {
  schema: CredentialSchema;
  disclosureToJsonPointer?: Record<string, string>;
}) {
  const locale = useAtomValue(localeAtom);

  const style = schema?.credentialSchemeStyleDetails?.[0]?.style;
  const jsonPointerToFormatted = Object.entries(disclosureToJsonPointer ?? {})
    .map(([key, value]) => {
      const name = key.slice(1).split("/").join(".");
      const attribute = schema?.attributes?.find((a) => a.name === name);

      if (!attribute) {
        return null;
      }

      const displayName =
        attribute.displayName[locale] ??
        Object.values(attribute.displayName)[0] ??
        name;

      const type = attribute?.type ?? "STRING";

      return {
        name,
        value,
        type,
        displayName,
      };
    })
    .filter(Boolean);

  const attrMap = jsonPointerToFormatted.reduce<Record<string, string>>(
    (acc, attr) => {
      acc[attr.name] = attr.value;
      return acc;
    },
    {},
  );

  const subtitle = style?.subtitle.replace(/{{(.*?)}}/g, (_, key) => {
    return attrMap[key.trim()] || "";
  });

  const title = style?.title.replace(/{{(.*?)}}/g, (_, key) => {
    return attrMap[key.trim()] || "";
  });

  return (
    <div className="max-w-dvw p-3">
      <div
        className={cn(
          "group -m-px grid aspect-cc overflow-hidden rounded-2xl border",
          style?.textColor === TextColor.Light ? "text-white" : "text-black",
        )}
      >
        {style?.backgroundCard ? (
          <img
            src={style.backgroundCard}
            alt="Verifiable Credential Background"
            className="size-full object-cover [grid-area:1/1]"
          />
        ) : (
          <div
            className="size-full [grid-area:1/1]"
            style={{
              backgroundColor: `#${argbToHex(style?.cardColor)}`,
            }}
          />
        )}
        <div className="p-3 pt-2 [grid-area:1/1]">
          <h3 className="text-lg font-semibold">{title}</h3>
          <h4>{subtitle}</h4>
        </div>
      </div>
      <Card className="mt-6 min-w-64 divide-y p-6">
        <Table safariFix={false}>
          <TableHeader>
            <TableRow>
              <TableHead className="px-0">
                <FormattedMessage
                  id="common.displayName"
                  defaultMessage="Display Name"
                />
              </TableHead>
              <TableHead className="px-0">
                <FormattedMessage id="common.value" defaultMessage="Value" />
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {jsonPointerToFormatted.map((disclosure) => {
              if (!disclosure) {
                return null;
              }
              return (
                <TableRow key={disclosure.name}>
                  <TableCell>{disclosure.displayName}</TableCell>
                  <TableCell className="max-w-3xl">
                    {disclosure.type !== "IMAGE" ? (
                      disclosure.value
                    ) : (
                      <img className="size-8" src={disclosure.value} alt="" />
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </Card>
    </div>
  );
}

function EmojiDisplay({ emojis }: { emojis?: string[] }) {
  if (!emojis) {
    return null;
  }
  return (
    <div className="grid place-items-center text-8xl">
      <div className="flex gap-4">
        {emojis.map((emoji) => (
          <div key={emoji}>{emoji}</div>
        ))}
      </div>
    </div>
  );
}
