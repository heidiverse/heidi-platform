// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconPlus } from "@tabler/icons-react";
import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute, getRouteApi, Link } from "@tanstack/react-router";
import { useAtomValue } from "jotai";
import { type FormEvent, useState } from "react";
import { FormattedMessage } from "react-intl";
import { PageHeader } from "@/components/common/page-header";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { getKeys } from "@/lib/api/keys/api";
import {
  type CreateStatusList,
  createStatusList,
  DEFAULT_STATUS_LIST_TTL_SECONDS,
} from "@/lib/api/status-lists/api";
import { statusListOptions } from "@/lib/api/status-lists/query-options";
import { jotaiStore, selectedTenantAtom } from "@/lib/atoms";
import { useUser } from "@/lib/hooks/use-user";
import { isEditor } from "@/lib/utils/user";

export const Route = createFileRoute("/_authenticated/status-lists/")({
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(
      statusListOptions(jotaiStore.get(selectedTenantAtom) ?? undefined),
    ),
  component: RouteComponent,
});

const routeApi = getRouteApi("/_authenticated/status-lists");

function RouteComponent() {
  const { crumb } = routeApi.useLoaderData();
  const user = useUser();
  const canEdit = isEditor(user);
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const tenantId = selectedTenant || user.tenantId;
  const { data: statusLists } = useSuspenseQuery(statusListOptions(tenantId));
  const [open, setOpen] = useState(false);

  return (
    <>
      <PageHeader heading={crumb}>
        {canEdit && (
          <Button onClick={() => setOpen(true)}>
            <IconPlus />
            <FormattedMessage
              id="common.new.withValue"
              defaultMessage="New {value}"
              values={{ value: "Status List" }}
            />
          </Button>
        )}
      </PageHeader>
      <div className="mt-4 grid gap-3">
        {statusLists.map((statusList) => (
          <Link
            key={statusList.id}
            to="/status-lists/$statusListId"
            params={{ statusListId: statusList.id }}
          >
            <Card className="grid gap-2 p-4 transition-colors hover:bg-muted/50 sm:grid-cols-[1fr_auto]">
              <div>
                <h2 className="font-semibold">{statusList.name}</h2>
                <p className="text-sm text-muted-foreground">
                  {statusList.type} · {statusList.entryCount.toLocaleString()} entries ·{" "}
                  {statusList.bits} bit
                </p>
              </div>
              <div className="text-sm text-muted-foreground">
                {statusList.publishedAt ? "Published" : "Draft"}
              </div>
            </Card>
          </Link>
        ))}
        {statusLists.length === 0 && (
          <Card className="p-6 text-sm text-muted-foreground">
            No status lists yet.
          </Card>
        )}
      </div>
      {canEdit && <CreateDialog open={open} onOpenChange={setOpen} />}
    </>
  );
}

function CreateDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const user = useUser();
  const selectedTenant = useAtomValue(selectedTenantAtom);
  const tenantId = selectedTenant || user.tenantId;
  const queryClient = useQueryClient();
  const { data: platformKeys = [] } = useSuspenseQuery({
    queryKey: ["platform-keys", tenantId],
    queryFn: () => getKeys(tenantId),
  });
  const keys = platformKeys.flatMap((key) =>
    key.versions
      .filter((version) => version.id === key.activeVersionId)
      .map((version) => ({
        keyId: key.id,
        logicalKeyId: key.keyId,
        algorithm: version.algorithm,
      })),
  );
  const [value, setValue] = useState<CreateStatusList>({
    name: "",
    type: "SD_JWT_VC",
    bits: 1,
    entryCount: 131072,
    publishMode: "LOCAL",
    signingKeyId: keys[0]?.keyId ?? "",
    ttl: DEFAULT_STATUS_LIST_TTL_SECONDS,
  });
  const mutation = useMutation({
    mutationFn: createStatusList,
    async onSuccess() {
      await queryClient.invalidateQueries({ queryKey: ["status-lists"] });
      onOpenChange(false);
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate({
      ...value,
      endpoint: value.publishMode === "REMOTE" ? value.endpoint : undefined,
    });
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={submit}>
          <DialogHeader>
            <DialogTitle>New Status List</DialogTitle>
            <DialogDescription>
              Creates an IETF Token Status List for SD-JWT VC credentials.
            </DialogDescription>
          </DialogHeader>
          <FieldGroup className="py-5">
            <Field>
              <FieldLabel htmlFor="status-list-name">Name</FieldLabel>
              <Input
                id="status-list-name"
                required
                value={value.name}
                onChange={(event) => setValue({ ...value, name: event.target.value })}
              />
            </Field>
            <Field>
              <FieldLabel>Type</FieldLabel>
              <Select value={value.type} disabled>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent><SelectItem value="SD_JWT_VC">SD-JWT VC</SelectItem></SelectContent>
              </Select>
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field>
                <FieldLabel>Bits per entry</FieldLabel>
                <Select
                  value={value.bits.toString()}
                  onValueChange={(bits) =>
                    setValue({ ...value, bits: Number(bits) as 1 | 2 | 4 | 8 })
                  }
                >
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {[1, 2, 4, 8].map((bits) => <SelectItem key={bits} value={bits.toString()}>{bits}</SelectItem>)}
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="status-list-size">Entries</FieldLabel>
                <Input
                  id="status-list-size"
                  type="number"
                  min={1}
                  required
                  value={value.entryCount}
                  onChange={(event) => setValue({ ...value, entryCount: event.target.valueAsNumber })}
                />
              </Field>
            </div>
            <Field>
              <FieldLabel>Status-list key</FieldLabel>
              <Select
                required
                value={value.signingKeyId}
                onValueChange={(signingKeyId) => setValue({ ...value, signingKeyId })}
              >
                <SelectTrigger><SelectValue placeholder="Select key" /></SelectTrigger>
                <SelectContent>
                  {keys.map((key) => (
                    <SelectItem key={key.keyId} value={key.keyId}>
                      {key.logicalKeyId} · {key.algorithm}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            <Field>
              <FieldLabel>Publishing</FieldLabel>
              <Select
                value={value.publishMode}
                onValueChange={(publishMode: "LOCAL" | "REMOTE") => setValue({ ...value, publishMode })}
              >
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="LOCAL">Heidi Platform</SelectItem>
                  <SelectItem value="REMOTE">Third-party endpoint</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            {value.publishMode === "REMOTE" && (
              <Field>
                <FieldLabel htmlFor="status-list-endpoint">Endpoint</FieldLabel>
                <Input
                  id="status-list-endpoint"
                  type="url"
                  required
                  value={value.endpoint ?? ""}
                  onChange={(event) => setValue({ ...value, endpoint: event.target.value })}
                />
              </Field>
            )}
            {mutation.error && <p className="text-sm text-destructive">{mutation.error.message}</p>}
          </FieldGroup>
          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending || !value.signingKeyId}>
              Create
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
