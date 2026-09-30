// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { endKeyVersion, type PlatformKey, type PlatformKeyVersion } from "@/lib/api/keys/api";

export function KeyVersionsDialog({ tenantId, signingKey, onClose }: { tenantId?: string; signingKey: PlatformKey; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [selection, setSelection] = useState<{ version: PlatformKeyVersion; action: "retire" | "revoke" } | null>(null);
  const update = useMutation({
    mutationFn: ({ version, action }: NonNullable<typeof selection>) => endKeyVersion(tenantId, signingKey.keyId, version.id, action),
    onSuccess: async () => {
      setSelection(null);
      await queryClient.invalidateQueries({ queryKey: ["platform-keys", tenantId ?? "platform"] });
      await queryClient.invalidateQueries({ queryKey: ["identity-key-slots", tenantId ?? "platform"] });
    },
    onError: (error) => toast.error("Could not update key version.", { description: error.message }),
  });

  return <Dialog open onOpenChange={(open) => { if (!open && !update.isPending) onClose(); }}><DialogContent>
    <DialogHeader><DialogTitle>Versions · {signingKey.keyId}</DialogTitle><DialogDescription>Public keys and certificates remain available after retirement or revocation.</DialogDescription></DialogHeader>
    {signingKey.versions.map((version) => <div key={version.id} className="rounded-md border p-3">
      <p>Version {version.version} · {version.status}</p>
      {version.status === "PREVIOUS" && version.previousUntil && <p className="text-sm text-muted-foreground">Grace ends {new Date(version.previousUntil).toLocaleString()}. Existing requests retain access until they end.</p>}
      <div className="mt-2 flex gap-2">
        {version.status === "ACTIVE" && <Button variant="outline" disabled={update.isPending} onClick={() => setSelection({ version, action: "retire" })}>Retire</Button>}
        {version.status !== "REVOKED" && <Button variant="outline" disabled={update.isPending} onClick={() => setSelection({ version, action: "revoke" })}>Revoke</Button>}
      </div>
    </div>)}
    {selection && <div className="space-y-3 rounded-md border p-3">
      <p className="font-medium">{selection.action === "revoke" ? "Revoke" : "Retire"} version {selection.version.version}?</p>
      <p className="text-sm">{selection.action === "revoke" ? "Permanently stops signing and decryption, including existing requests. This cannot be undone." : "Stops new requests using this version. Existing requests and the configured grace period keep access. Activate a replacement to resume new requests."}</p>
      <div className="flex justify-end gap-2"><Button variant="outline" disabled={update.isPending} onClick={() => setSelection(null)}>Cancel</Button><Button disabled={update.isPending} onClick={() => update.mutate(selection)}>{update.isPending ? "Updating…" : "Confirm"}</Button></div>
    </div>}
  </DialogContent></Dialog>;
}
