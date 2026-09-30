// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { HWCLightbox } from "@heidiverse/heidi-web-components/react";
import { useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute, useLocation } from "@tanstack/react-router";
import { useAtomValue } from "jotai";
import {
  type ComponentProps,
  type ComponentType,
  useEffect,
} from "react";
import { PageHeader } from "@/components/common/page-header";
import { Badge } from "@/components/ui/badge";
import { schemaOptions } from "@/lib/api/credential-schemas/query-options";
import { localeAtom } from "@/lib/atoms";
import { transferForSchema } from "@/lib/issuer-transfer";
import { CredentialSchemaState } from "@/types/credential-schema";

type IssuerLightboxProps = Omit<ComponentProps<typeof HWCLightbox>, "token"> & {
  clientInteractionToken?: string;
};

const IssuerLightbox =
  HWCLightbox as unknown as ComponentType<IssuerLightboxProps>;

export const Route = createFileRoute("/_authenticated/issuer/$schemaId/transfer")({
  loader: async ({ params: { schemaId }, context }) => {
    const { displayName, credentialIdentifier } =
      await context.queryClient.ensureQueryData(schemaOptions({ schemaId }));
    return {
      crumb: context.intl.$t(
        { id: "pages.issuer.transfer", defaultMessage: "Transfer {value}" },
        { value: displayName ?? credentialIdentifier },
      ),
    };
  },
  component: RouteComponent,
});

function RouteComponent() {
  const locale = useAtomValue(localeAtom);
  const { schemaId } = Route.useParams();
  const navigate = Route.useNavigate();
  const { state } = useLocation();
  const { data: schema } = useSuspenseQuery(schemaOptions({ schemaId }));
  const transfer = transferForSchema(state, schemaId);

  useEffect(() => {
    if (!transfer) {
      void navigate({ to: "/issuer", replace: true });
    }
  }, [navigate, transfer]);

  if (schema.state !== CredentialSchemaState.Published) {
    throw new Error("Schema is not published");
  }

  if (!transfer) {
    return null;
  }

  return (
    <>
      <PageHeader
        heading={schema.displayName ?? schema.credentialIdentifier}
        className="flex-col items-start"
      >
        <Badge variant="outline" className="bg-card">
          V {schema.version}
        </Badge>
      </PageHeader>
      <IssuerLightbox
        locale={locale}
        clientInteractionToken={transfer.clientInteractionToken}
        open
        onClose={() => {
          void navigate({ to: "/issuer", replace: true });
        }}
      />
    </>
  );
}
