// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { type KeyRotationPolicy, type PlatformKey, updateRotationPolicy } from "@/lib/api/keys/api";

const secondsPerDay = 86400;
const defaultRotationDays = 30;

export function KeyRotationDialog({ tenantId, signingKey, onClose }: {
  tenantId?: string; signingKey: PlatformKey; onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<KeyRotationPolicy["mode"]>(signingKey.rotationPolicy.mode);
  const [interval, setInterval] = useState(String(
    (signingKey.rotationPolicy.intervalSeconds ?? defaultRotationDays * secondsPerDay) / secondsPerDay,
  ));
  const [grace, setGrace] = useState(String(signingKey.rotationPolicy.gracePeriodSeconds / secondsPerDay));
  const intervalSeconds = Math.round(Number(interval) * secondsPerDay);
  const gracePeriodSeconds = Math.round(Number(grace) * secondsPerDay);
  const intervalValid = mode !== "AUTOMATIC"
    || (interval.trim() !== "" && Number.isSafeInteger(intervalSeconds) && intervalSeconds > 0);
  const valid = grace.trim() !== "" && intervalValid
    && Number.isSafeInteger(gracePeriodSeconds) && gracePeriodSeconds >= 0;
  const save = useMutation({
    mutationFn: () => updateRotationPolicy(tenantId, signingKey.keyId, {
      mode,
      intervalSeconds: mode === "AUTOMATIC" ? intervalSeconds : undefined,
      gracePeriodSeconds,
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["platform-keys", tenantId ?? "platform"] });
      toast.success("Rotation policy saved.");
      onClose();
    },
    onError: (error) => toast.error("Could not save rotation policy.", { description: error.message }),
  });
  return <Dialog open onOpenChange={(open) => !open && onClose()}><DialogContent>
    <DialogHeader><DialogTitle>Rotation policy · {signingKey.keyId}</DialogTitle><DialogDescription>Choose who controls this key's lifecycle. Automatic rotation remains limited to eligible request-decryption keys.</DialogDescription></DialogHeader>
    <Field><FieldLabel htmlFor="rotation-mode">Rotation mode</FieldLabel><select id="rotation-mode" className="h-9 w-full rounded-md border bg-transparent px-3 text-sm" value={mode} onChange={(event) => setMode(event.target.value as KeyRotationPolicy["mode"])}><option value="MANUAL">Manual</option><option value="AUTOMATIC">Automatic</option><option value="EXTERNAL">Managed by provider</option></select></Field>
    {mode === "AUTOMATIC" && <Field><FieldLabel htmlFor="rotation-interval">Rotate after (days)</FieldLabel><Input id="rotation-interval" type="number" min="0" step="any" value={interval} onChange={(event) => setInterval(event.target.value)} /><p className="text-sm text-muted-foreground">Automatic rotation currently applies only to keys assigned exclusively to request decryption.</p></Field>}
    <Field><FieldLabel htmlFor="rotation-grace">Keep previous versions usable for (days)</FieldLabel><Input id="rotation-grace" type="number" min="0" step="any" value={grace} onChange={(event) => setGrace(event.target.value)} /><p className="text-sm text-muted-foreground">Allow for cached wallet metadata. Active requests retain their keys until they end; public verification material is retained.</p></Field>
    <DialogFooter><Button variant="outline" onClick={onClose}>Cancel</Button><Button disabled={!valid || save.isPending} onClick={() => save.mutate()}>Save policy</Button></DialogFooter>
  </DialogContent></Dialog>;
}
