// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconCloudUpload, IconExternalLink } from "@tabler/icons-react";
import { useMutation, useQuery, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { type FormEvent, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  getStatusListEntry,
  publishStatusList,
  publishStatusListToSwitzerland,
  setStatusListEntry,
} from "@/lib/api/status-lists/api";
import { statusListDetailOptions } from "@/lib/api/status-lists/query-options";
import { useUser } from "@/lib/hooks/use-user";
import { isEditor } from "@/lib/utils/user";

export const Route = createFileRoute("/_authenticated/status-lists/$statusListId")({
  loader: async ({ context, params: { statusListId } }) => {
    const statusList = await context.queryClient.ensureQueryData(
      statusListDetailOptions(statusListId),
    );
    return { crumb: statusList.name };
  },
  component: RouteComponent,
});

function RouteComponent() {
  const { statusListId } = Route.useParams();
  const user = useUser();
  const canEdit = isEditor(user);
  const queryClient = useQueryClient();
  const { data: statusList } = useSuspenseQuery(statusListDetailOptions(statusListId));
  const [indexInput, setIndexInput] = useState("0");
  const [index, setIndex] = useState<number>();
  const [status, setStatus] = useState("0");
  const entry = useQuery({
    queryKey: ["status-list-entry", statusListId, index],
    queryFn: () => getStatusListEntry(statusListId, index!),
    enabled: index !== undefined,
  });
  const publish = useMutation({
    mutationFn: () => publishStatusList(statusListId),
    async onSuccess() {
      await queryClient.invalidateQueries({ queryKey: ["status-list", statusListId] });
      toast.success("Status list published");
    },
  });
  const publishSwiss = useMutation({
    mutationFn: () => publishStatusListToSwitzerland(statusListId),
    async onSuccess() {
      await queryClient.invalidateQueries({ queryKey: ["status-list", statusListId] });
      toast.success("Status list uploaded to the Swiss registry");
    },
  });
  const update = useMutation({
    mutationFn: () => setStatusListEntry(statusListId, index!, Number(status)),
    async onSuccess() {
      await queryClient.invalidateQueries({
        queryKey: ["status-list-entry", statusListId, index],
      });
      await queryClient.invalidateQueries({ queryKey: ["status-list", statusListId] });
      toast.success("Status updated");
    },
  });

  function load(event: FormEvent) {
    event.preventDefault();
    const nextIndex = Number(indexInput);
    if (!Number.isInteger(nextIndex) || nextIndex < 0 || nextIndex >= statusList.entryCount) return;
    setIndex(nextIndex);
  }

  return (
    <div className="space-y-4">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-3xl font-semibold">{statusList.name}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {statusList.type} · {statusList.entryCount.toLocaleString()} entries · {statusList.bits} bit
          </p>
        </div>
        {canEdit && (
          <div className="flex flex-wrap gap-2">
            <Button onClick={() => publish.mutate()} disabled={publish.isPending}>
              <IconCloudUpload />
              {statusList.publishedAt ? "Republish" : "Publish"}
            </Button>
            <Button
              variant="outline"
              onClick={() => publishSwiss.mutate()}
              disabled={publishSwiss.isPending}
            >
              <IconCloudUpload />
              {statusList.swissPublishedAt ? "Re-upload to Swiss" : "Upload to Swiss"}
            </Button>
          </div>
        )}
      </header>

      <Card className="grid gap-4 p-5 sm:grid-cols-2">
        <Detail label="Publishing" value={statusList.publishMode === "LOCAL" ? "Heidi Platform" : "Third party"} />
        <Detail label="Status-list key" value={statusList.signingKeyId} />
        <Detail label="Cache TTL" value={statusList.ttl ? `${statusList.ttl} seconds` : "None"} />
        <Detail label="Last published" value={statusList.publishedAt ? new Date(statusList.publishedAt).toLocaleString() : "Not published"} />
        <Detail label="Swiss registry" value={statusList.swissPublishedAt ? new Date(statusList.swissPublishedAt).toLocaleString() : "Not uploaded"} />
        <div className="sm:col-span-2">
          <p className="text-sm font-medium">URI</p>
          <a
            className="mt-1 inline-flex items-center gap-1 break-all text-sm text-primary hover:underline"
            href={statusList.uri}
            target="_blank"
            rel="noreferrer"
          >
            {statusList.uri}<IconExternalLink className="size-4 shrink-0" />
          </a>
        </div>
        {statusList.swissStatusListUrl && (
          <div className="sm:col-span-2">
            <p className="text-sm font-medium">Swiss registry URI</p>
            <a
              className="mt-1 inline-flex items-center gap-1 break-all text-sm text-primary hover:underline"
              href={statusList.swissStatusListUrl}
              target="_blank"
              rel="noreferrer"
            >
              {statusList.swissStatusListUrl}<IconExternalLink className="size-4 shrink-0" />
            </a>
          </div>
        )}
      </Card>

      <Card className="space-y-5 p-5">
        <div>
          <h2 className="text-xl font-semibold">Status entry</h2>
          <p className="text-sm text-muted-foreground">Read or change an entry by index.</p>
        </div>
        <form className="flex max-w-md items-end gap-2" onSubmit={load}>
          <Field>
            <FieldLabel htmlFor="entry-index">Index</FieldLabel>
            <Input
              id="entry-index"
              type="number"
              min={0}
              max={statusList.entryCount - 1}
              value={indexInput}
              onChange={(event) => setIndexInput(event.target.value)}
            />
          </Field>
          <Button type="submit" variant="secondary">Load</Button>
        </form>
        {entry.data && (
          <div className="max-w-md space-y-4 rounded-xl border p-4">
            <p className="text-sm">Current status: <strong>{entry.data.status}</strong> ({statusName(entry.data.status)})</p>
            {canEdit && (
              <Field>
                <FieldLabel htmlFor="entry-status">New status</FieldLabel>
                <Input
                  id="entry-status"
                  type="number"
                  min={0}
                  max={2 ** statusList.bits - 1}
                  value={status}
                  onChange={(event) => setStatus(event.target.value)}
                />
                <FieldDescription>0 valid · 1 invalid · 2 suspended</FieldDescription>
                <Button
                  className="mt-2"
                  onClick={() => update.mutate()}
                  disabled={update.isPending}
                >
                  Update status
                </Button>
              </Field>
            )}
          </div>
        )}
        {(entry.error || update.error || publish.error || publishSwiss.error) && (
          <p className="text-sm text-destructive">
            {(entry.error || update.error || publish.error || publishSwiss.error)?.message}
          </p>
        )}
      </Card>
    </div>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return <div><p className="text-sm font-medium">{label}</p><p className="mt-1 text-sm text-muted-foreground">{value}</p></div>;
}

function statusName(status: number) {
  if (status === 0) return "valid";
  if (status === 1) return "invalid";
  if (status === 2) return "suspended";
  return "application-specific";
}
