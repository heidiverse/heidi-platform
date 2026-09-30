// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconArrowDown, IconArrowUp } from "@tabler/icons-react";
import { useStore } from "@tanstack/react-form";
import { useAtomValue } from "jotai";
import { FormattedMessage } from "react-intl";
import { Button } from "@/components/ui/button";
import { contentLanguageAtom } from "@/lib/atoms";
import { cn } from "@/lib/utils";
import { getLocalizedValue } from "@/lib/utils/localized";
import { withForm } from "@/routes/_authenticated/credential-schemas/-form/form-context";
import { CredentialSchemaState } from "@/types/credential-schema";
import type { CredentialSchemaForm } from "./schemas";

function Attribute({
  attribute,
  onMove,
  isPublished,
  disabledUp = false,
  disabledDown = false,
}: {
  attribute: NonNullable<CredentialSchemaForm["attributes"]>[number];
  onMove: (direction: "up" | "down") => void;
  isPublished: boolean;
  disabledUp?: boolean;
  disabledDown?: boolean;
}) {
  const contentLanguage = useAtomValue(contentLanguageAtom);
  return (
    <div
      className={cn(
        "grid grid-cols-[3fr_2fr_auto_auto] items-center gap-x-2 rounded-2xl border bg-background px-3 sm:gap-x-3",
        isPublished ? "py-2" : "py-3 sm:px-6",
      )}
    >
      <p className="truncate text-sm font-semibold"># {attribute.name}</p>
      <p className="truncate text-sm font-medium">
        {getLocalizedValue(attribute.displayName, contentLanguage)}
      </p>
      {!isPublished && (
        <>
          <Button
            type="button"
            variant="ghost"
            className="gap-1 px-2 text-blue-500"
            disabled={disabledUp}
            onClick={() => onMove("up")}
          >
            <IconArrowUp className="size-5" />{" "}
            <FormattedMessage id="common.up" defaultMessage="Up" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            className="gap-1 px-2 text-blue-500"
            disabled={disabledDown}
            onClick={() => onMove("down")}
          >
            <IconArrowDown className="size-5" />{" "}
            <FormattedMessage id="common.down" defaultMessage="Down" />
          </Button>
        </>
      )}
    </div>
  );
}

export const OrderedProperties = withForm({
  defaultValues: {} as CredentialSchemaForm,
  render: ({ form }) => {
    const isPublished = useStore(
      form.store,
      (state) => state.values.state === CredentialSchemaState.Published,
    );

    return (
      <form.AppField name="attributes" mode="array">
        {(field) => (
          <div className="space-y-2">
            {field.state.value?.map((attribute, index) => (
              <Attribute
                isPublished={isPublished}
                key={attribute.id}
                attribute={attribute}
                onMove={(direction) => {
                  const dir = direction === "up" ? -1 : 1;
                  field.swapValues(index, index + dir);
                }}
                disabledUp={index === 0}
                disabledDown={index === field.state.value.length - 1}
              />
            ))}
          </div>
        )}
      </form.AppField>
    );
  },
});
