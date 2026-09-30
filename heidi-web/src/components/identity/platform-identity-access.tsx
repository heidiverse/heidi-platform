// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQueries, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Field, FieldGroup, FieldLabel, FieldSet } from "@/components/ui/field";
import type { IssuerDefinition } from "@/lib/api/issuer-definitions/api";
import type { Organisation } from "@/lib/api/organisations/api";
import { organisationListOptions } from "@/lib/api/organisations/query-options";
import { updateSettings } from "@/lib/api/settings/api";
import { settingsForOrganisationOptions } from "@/lib/api/settings/query-options";

export function PlatformIdentityAccess({ identity }: { identity: IssuerDefinition }) {
  const queryClient = useQueryClient();
  const { data: allOrganisations } = useSuspenseQuery(organisationListOptions());
  const organisations = allOrganisations.filter(({ revoked }) => !revoked);
  const settingsQueries = useQueries({
    queries: organisations.map(({ tenantId }) => settingsForOrganisationOptions(tenantId)),
  });
  const settings = settingsQueries.flatMap(({ data }) => data ? [data] : []);
  const [selected, setSelected] = useState<string[]>();
  const loading = settingsQueries.some(({ isPending }) => isPending);

  useEffect(() => {
    if (loading || selected) return;
    setSelected(settings.filter(({ issuerIds }) => issuerIds.includes(identity.id))
      .map(({ tenantId }) => tenantId));
  }, [identity.id, loading, selected, settings]);

  const save = useMutation({
    mutationFn: async () => {
      if (!selected) return;
      await Promise.all(settings.map((item) => {
        const assigned = item.issuerIds.includes(identity.id);
        const shouldAssign = selected.includes(item.tenantId);
        if (assigned === shouldAssign) return Promise.resolve();
        return updateSettings({
          tenantId: item.tenantId,
          thumbnail: null,
          issuerIds: shouldAssign
            ? [...item.issuerIds, identity.id]
            : item.issuerIds.filter((id) => id !== identity.id),
        });
      }));
    },
    onSuccess: async () => {
      await Promise.all(organisations.map(({ tenantId }) =>
        queryClient.invalidateQueries(settingsForOrganisationOptions(tenantId))));
      toast.success("Organisation access saved.");
    },
    onError: (error) => toast.error("Could not save organisation access.", { description: error.message }),
  });

  function toggle(organisation: Organisation, checked: boolean) {
    setSelected((current = []) => checked
      ? [...current, organisation.tenantId]
      : current.filter((tenantId) => tenantId !== organisation.tenantId));
  }

  return <Card className="mt-4">
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h2 className="text-xl font-semibold">Organisation access</h2>
        <p className="mt-1 text-sm text-muted-foreground">Selected organisations inherit this identity as read-only.</p>
      </div>
      <Button disabled={loading || save.isPending} onClick={() => save.mutate()}>Save access</Button>
    </div>
    <FieldSet className="mt-4">
      <FieldGroup className="grid gap-2 sm:grid-cols-2">
        {organisations.map((organisation) => {
          const id = `platform-identity-${identity.id}-${organisation.tenantId}`;
          return <Field key={organisation.tenantId} orientation="inline">
            <Checkbox id={id} checked={selected?.includes(organisation.tenantId) ?? false}
              disabled={loading} onCheckedChange={(checked) => toggle(organisation, checked === true)} />
            <FieldLabel htmlFor={id} className="font-normal">{organisation.displayName}</FieldLabel>
          </Field>;
        })}
      </FieldGroup>
    </FieldSet>
  </Card>;
}
