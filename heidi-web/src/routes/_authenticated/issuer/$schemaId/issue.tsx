// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconRubberStamp } from "@tabler/icons-react";
import { useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import {
  type Dispatch,
  type SetStateAction,
  Suspense,
  useEffect,
  useState,
} from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { toast } from "sonner";
import { AttributeArrayField } from "@/components/common/credential-attributes/array-attribute-field";
import { AttributeField } from "@/components/common/credential-attributes/attribute-field";
import { Loading } from "@/components/common/loading";
import { PageHeader } from "@/components/common/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { schemaOptions } from "@/lib/api/credential-schemas/query-options";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";
import { createTestingProcess } from "@/lib/api/testing/api";
import { useUser } from "@/lib/hooks/use-user";
import { getLocalizedValue } from "@/lib/utils/localized";
import { sortAttributes } from "@/lib/utils/sort-attributes";
import {
  AttributeType,
  CredentialSchemaState,
} from "@/types/credential-schema";

export const Route = createFileRoute("/_authenticated/issuer/$schemaId/issue")({
  loader: async ({ params: { schemaId }, context }) => {
    const { displayName, credentialIdentifier } =
      await context.queryClient.ensureQueryData(schemaOptions({ schemaId }));
    return {
      crumb: context.intl.$t(
        { id: "pages.issuer.issue", defaultMessage: "Issue {value}" },
        { value: displayName ?? credentialIdentifier },
      ),
    };
  },
  component: RouteComponent,
});

type Values = Record<
  string,
  {
    type: AttributeType;
    value: string | string[];
    attributeNameOverrides: Record<string, string>;
  }
>;

function RouteComponent() {
  const { schemaId } = Route.useParams();
  const [values, setValues] = useState<Values>({});
  const navigate = Route.useNavigate();
  const { $t } = useIntl();

  const { data: schema } = useSuspenseQuery(schemaOptions({ schemaId }));

  if (schema.state !== CredentialSchemaState.Published) {
    throw new Error("Schema is not published");
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const issuanceValues = Object.fromEntries(
      Object.entries(values).map(([key, { value }]) => [key, value]),
    );

    try {
      const { clientInteractionToken } = await createTestingProcess({
        action: "pre_auth_issuance",
        preAuthIssuanceData: {
          schemaIdentifier: {
            credentialIdentifier: schema.credentialIdentifier,
            version: schema.version,
          },
          issuanceProfileId: schema.issuerSettings.issuanceProfileId,
          values: issuanceValues,
        },
      });
      navigate({
        to: "/issuer/$schemaId/transfer",
        params: { schemaId },
        state: {
          issuerTransfer: {
            schemaId,
            clientInteractionToken,
          },
        },
      });
    } catch (error) {
      toast(
        $t({
          id: "pages.issuer.issue.error",
          defaultMessage: "There was an error...",
        }),
        { description: (error as Error).message },
      );
      console.error(error);
    }
  }

  return (
    <div>
      <PageHeader
        heading={schema.displayName ?? schema.credentialIdentifier}
        className="flex-col items-start"
      >
        <Badge variant="outline" className="bg-card">
          V {schema.version}
        </Badge>
      </PageHeader>
      <Suspense fallback={<Loading />}>
        <Card className="mx-auto max-w-xl sm:mt-6">
          <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
            <AttributeFields values={values} setValues={setValues} />
            <Button type="submit" className="mt-4">
              <IconRubberStamp />
              <FormattedMessage id="common.issue" defaultMessage="Issue" />
            </Button>
          </form>
        </Card>
      </Suspense>
    </div>
  );
}

const defaultAttributeValues: Partial<Record<AttributeType, string>> = {
  [AttributeType.Boolean]: "false",
};

function AttributeFields({
  values,
  setValues,
}: {
  values: Values;
  setValues: Dispatch<SetStateAction<Values>>;
}) {
  const user = useUser();
  const { schemaId } = Route.useParams();
  const { data: schema, isSuccess } = useSuspenseQuery({
    ...schemaOptions({ schemaId }),
    refetchIntervalInBackground: false,
    refetchOnMount: false,
  });
  const { data: settings } = useSuspenseQuery(
    settingsForOrganisationOptions(schema.tenantId ?? user.tenantId),
  );
  useEffect(() => {
    if (isSuccess) {
      setValues(
        Object.fromEntries(
          schema.attributes.map((attr) => [
            attr.name,
            {
              type: attr.type,
              value: attr.isArray
                ? [""]
                : (defaultAttributeValues[attr.type] ?? ""),
              attributeNameOverrides: attr.attributeNameOverrides,
            },
          ]),
        ) as Values,
      );
    }
  }, [isSuccess, schema]);

  const orderedProperties =
    schema.credentialSchemeStyleDetails[0]?.style.orderedProperties ?? [];

  return sortAttributes(schema.attributes, orderedProperties)
    .map((attribute) => {
      const val = values[attribute.name]?.value;

      if (attribute.isArray) {
        return (
          <AttributeArrayField
            key={attribute.name}
            fieldName={attribute.name}
            label={
              getLocalizedValue(
                attribute.displayName,
                settings.defaultLanguage,
              ) ?? ""
            }
            type={attribute.type}
            values={Array.isArray(val) ? val : [""]}
            onChange={(newVals: string[]) =>
              setValues((prev) => ({
                ...prev,
                [attribute.name]: {
                  ...prev[attribute.name]!,
                  value: newVals,
                },
              }))
            }
          />
        );
      }

      return (
        <AttributeField
          key={attribute.name}
          fieldName={attribute.name}
          label={
            getLocalizedValue(
              attribute.displayName,
              settings.defaultLanguage,
            ) ?? ""
          }
          type={attribute.type}
          value={typeof val === "string" ? val : ""}
          onValueChange={(value) =>
            setValues((prev) => ({
              ...prev,
              [attribute.name]: {
                ...prev[attribute.name]!,
                value: value,
              },
            }))
          }
        />
      );
    });
}
