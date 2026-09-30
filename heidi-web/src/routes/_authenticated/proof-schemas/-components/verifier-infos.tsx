// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconEye, IconEyeOff, IconPlus, IconTrash } from "@tabler/icons-react";
import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type {
  SwissVerificationQuery,
  VerifierInfo,
} from "@/types/proof-schema";

type Detail =
  | { kind: "manual"; index: number }
  | { kind: "swiss"; index: number }
  | null;

export function VerifierInfos({
  value,
  swissQueries,
  swissVerificationQueryEnabled,
  alwaysIncludeDcqlQuery,
  onChange,
  onSwissVerificationQueryEnabledChange,
  onAlwaysIncludeDcqlQueryChange,
}: {
  value: VerifierInfo[];
  swissQueries: SwissVerificationQuery[];
  swissVerificationQueryEnabled: boolean;
  alwaysIncludeDcqlQuery: boolean;
  onChange: (value: VerifierInfo[]) => void;
  onSwissVerificationQueryEnabledChange: (value: boolean) => void;
  onAlwaysIncludeDcqlQueryChange: (value: boolean) => void;
}) {
  const [detail, setDetail] = useState<Detail>(null);
  const [draft, setDraft] = useState<VerifierInfo | null>(null);
  const selectedSwiss = detail?.kind === "swiss"
    ? swissQueries[detail.index]
    : undefined;

  function edit(index: number) {
    const info = value[index];
    if (!info) return;
    setDraft({ ...info, credentialIds: info.credentialIds ?? null });
    setDetail({ kind: "manual", index });
  }

  function add() {
    const index = value.length;
    const info = { format: "jwt", data: "", credentialIds: null };
    onChange([...value, info]);
    setDraft(info);
    setDetail({ kind: "manual", index });
  }

  function saveDraft() {
    if (!draft || detail?.kind !== "manual") return;
    onChange(value.map((entry, index) => index === detail.index ? draft : entry));
    setDetail(null);
    setDraft(null);
  }

  const hasSwissQueries = swissQueries.length > 0;

  return (
    <div className="mt-8 space-y-4">
      <div>
        <h4 className="font-semibold">Verifier information</h4>
        <p className="text-muted-foreground text-sm">
          Attestations sent to the wallet. The type is shown here; open an entry
          to inspect its content.
        </p>
      </div>

      {hasSwissQueries && (
        <div className="rounded-3xl border bg-background p-4">
          <div className="flex items-start justify-between gap-3">
            <div>
              <div className="flex items-center gap-2">
                <Badge variant="outline">jwt</Badge>
                <span className="font-medium">Swiss vqPS</span>
                {swissQueries.length > 1 && (
                  <span className="text-muted-foreground text-sm">
                    ({swissQueries.length})
                  </span>
                )}
              </div>
              <p className="mt-1 text-muted-foreground text-sm">
                {swissVerificationQueryEnabled
                  ? "Included as verifier information."
                  : "Disabled; the request uses dcql_query instead."}
              </p>
            </div>
            <Button
              aria-label={swissVerificationQueryEnabled ? "Disable Swiss vqPS" : "Enable Swiss vqPS"}
              size="icon"
              variant="outline"
              onClick={() => {
                const enabled = !swissVerificationQueryEnabled;
                onSwissVerificationQueryEnabledChange(enabled);
                if (!enabled) onAlwaysIncludeDcqlQueryChange(true);
              }}
            >
              {swissVerificationQueryEnabled
                ? <IconEye className="size-4" />
                : <IconEyeOff className="size-4" />}
            </Button>
          </div>
          <div className="mt-3 flex items-center gap-2">
            <Checkbox
              id="always-include-dcql-query"
              checked={alwaysIncludeDcqlQuery || !swissVerificationQueryEnabled}
              disabled={!swissVerificationQueryEnabled}
              onCheckedChange={(checked) => onAlwaysIncludeDcqlQueryChange(checked === true)}
            />
            <Label htmlFor="always-include-dcql-query" className="font-normal">
              Always include <code>dcql_query</code>
            </Label>
          </div>
          <p className="mt-1 pl-7 text-muted-foreground text-sm">
            {swissVerificationQueryEnabled
              ? "Including it together with the Swiss scope is not standards compliant."
              : "Required because the Swiss vqPS is disabled."}
          </p>
          <div className="mt-3 space-y-2">
            {swissQueries.map((query, index) => (
              <div className="flex items-center justify-between rounded-xl border px-3 py-2" key={query.id ?? index}>
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">
                    {query.purposeName || query.scope || `Swiss vqPS ${index + 1}`}
                  </p>
                  <p className="truncate text-muted-foreground text-xs">
                    {query.status || "Published"}
                  </p>
                </div>
                <Button
                  aria-label={`Show Swiss vqPS ${index + 1}`}
                  size="icon"
                  variant="ghost"
                  onClick={() => setDetail({ kind: "swiss", index })}
                >
                  <IconEye className="size-4" />
                </Button>
              </div>
            ))}
          </div>
        </div>
      )}

      {value.map((info, index) => (
        <div
          className="flex items-center justify-between rounded-3xl border bg-background p-4"
          key={`${info.format}-${info.data}-${info.credentialIds?.join(",") ?? ""}`}
        >
          <div className="flex min-w-0 items-center gap-2">
            <Badge variant="outline">{info.format || "custom"}</Badge>
            <span className="truncate text-muted-foreground text-sm">Manual verifier info</span>
          </div>
          <div className="flex gap-2">
            <Button aria-label={`Show verifier info ${index + 1}`} size="icon" variant="ghost" onClick={() => edit(index)}>
              <IconEye className="size-4" />
            </Button>
            <Button
              aria-label={`Remove verifier info ${index + 1}`}
              size="icon"
              variant="outline"
              onClick={() => onChange(value.filter((_, item) => item !== index))}
            >
              <IconTrash className="size-4" />
            </Button>
          </div>
        </div>
      ))}

      <Button type="button" variant="tertiary" onClick={add}>
        <IconPlus />
        Add verifier info
      </Button>

      <Dialog open={detail !== null} onOpenChange={(open) => !open && setDetail(null)}>
        <DialogContent className="max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {detail?.kind === "swiss" ? "Swiss vqPS" : "Verifier information"}
            </DialogTitle>
            <DialogDescription>
              {detail?.kind === "swiss" ? "Read-only signed statement." : "Edit the verifier information sent to the wallet."}
            </DialogDescription>
          </DialogHeader>
          {detail?.kind === "swiss" && selectedSwiss && (
            <pre className="max-h-[55vh] overflow-auto rounded-xl bg-muted p-3 text-xs whitespace-pre-wrap">
              {selectedSwiss.jwt || JSON.stringify(selectedSwiss.query, null, 2) || "No content"}
            </pre>
          )}
          {detail?.kind === "manual" && draft && (
            <div className="space-y-4">
              <div className="space-y-1">
                <Label htmlFor="verifier-info-format">Type</Label>
                <Input
                  id="verifier-info-format"
                  value={draft.format}
                  onChange={(event) => setDraft({ ...draft, format: event.target.value })}
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="verifier-info-data">Content</Label>
                <Textarea
                  id="verifier-info-data"
                  className="font-mono text-xs"
                  rows={10}
                  value={draft.data}
                  onChange={(event) => setDraft({ ...draft, data: event.target.value })}
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="verifier-info-credentials">Credential IDs (optional)</Label>
                <Input
                  id="verifier-info-credentials"
                  value={draft.credentialIds?.join(", ") ?? ""}
                  onChange={(event) => setDraft({
                    ...draft,
                    credentialIds: event.target.value.split(",").map((id) => id.trim()).filter(Boolean),
                  })}
                />
              </div>
            </div>
          )}
          {detail?.kind === "manual" && (
            <DialogFooter>
              <Button onClick={saveDraft}>Save</Button>
            </DialogFooter>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

export function SwissVerificationQueriesOverview({
  queries,
}: {
  queries: SwissVerificationQuery[];
}) {
  const [selected, setSelected] = useState<SwissVerificationQuery | null>(null);

  return (
    <Card>
      <h2 className="text-xl font-semibold">Swiss vqPS</h2>
      <p className="mt-1 text-muted-foreground text-sm">
        Verification query public statements registered for this identity.
      </p>
      {queries.length === 0 ? (
        <p className="mt-4 text-muted-foreground text-sm">No vqPS found.</p>
      ) : (
        <div className="mt-4 space-y-2">
          {queries.map((query, index) => (
            <div className="flex items-center justify-between rounded-xl border p-3" key={query.id ?? index}>
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <Badge variant="outline">jwt</Badge>
                  <span className="truncate font-medium">{query.purposeName || query.scope || `vqPS ${index + 1}`}</span>
                  <span className="text-muted-foreground text-sm">{query.status || "Published"}</span>
                </div>
                <p className="mt-1 truncate text-muted-foreground text-sm">{query.scope || "No scope"}</p>
              </div>
              <Button
                aria-label={`Show Swiss vqPS ${index + 1}`}
                size="icon"
                variant="ghost"
                onClick={() => setSelected(query)}
              >
                <IconEye className="size-4" />
              </Button>
            </div>
          ))}
        </div>
      )}
      <Dialog open={selected !== null} onOpenChange={(open) => !open && setSelected(null)}>
        <DialogContent className="max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Swiss vqPS</DialogTitle>
            <DialogDescription>Read-only signed statement.</DialogDescription>
          </DialogHeader>
          {selected && (
            <pre className="max-h-[55vh] overflow-auto rounded-xl bg-muted p-3 text-xs whitespace-pre-wrap">
              {selected.jwt || JSON.stringify(selected.query, null, 2) || "No content"}
            </pre>
          )}
        </DialogContent>
      </Dialog>
    </Card>
  );
}
