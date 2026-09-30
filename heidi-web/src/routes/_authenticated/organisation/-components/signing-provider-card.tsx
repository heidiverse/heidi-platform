// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconPlus, IconRefresh, IconServer, IconTrash } from "@tabler/icons-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
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
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  checkSigningProviderConnection,
  createSigningProvider,
  deleteSigningProvider,
  getSigningProviderConnection,
  getSigningProviders,
  refreshSigningProvider,
  registerProviderClient,
  registerSigningProviderClient,
  type SigningProvider,
} from "@/lib/api/signing-providers/api";
import type { SigningProviderClient } from "@/lib/api/signing-providers/client-controls";

function backendClient(name: string): SigningProviderClient {
  if (name === "issuer" || name === "verifier") return name;

  throw new Error(`Unsupported signing client: ${name}`);
}

const CLIENTS = ["issuer", "verifier"] as const;

function clientLabel(client: typeof CLIENTS[number]) {
  return client.charAt(0).toUpperCase() + client.slice(1);
}

function canManageClients(scope: "global" | "tenant", tenantId?: string) {
  return scope === "global" ? !tenantId : Boolean(tenantId);
}

function ClientAuthorization({ provider, tenantId }: { provider: SigningProvider; tenantId?: string }) {
  const queryKey = ["signing-provider-connection", tenantId ?? "platform", provider.scope, provider.id];
  const query = useQuery({
    queryKey,
    queryFn: () => getSigningProviderConnection({
      tenantId,
      providerId: provider.id,
      scope: provider.scope,
    }),
  });
  const [failedClient, setFailedClient] = useState<SigningProviderClient | null>(null);
  const register = useMutation({
    mutationFn: registerProviderClient,
    onSuccess: async () => {
      setFailedClient(null);
      await query.refetch();
      toast.success("Signing client accepted.");
    },
    onError: (error, variables) => {
      setFailedClient(variables.client);
      toast.error(`Could not accept ${variables.client} client.`, { description: error.message });
    },
  });

  if (query.isPending) {
    return <p className="mt-3 text-xs text-muted-foreground">Checking client authorization…</p>;
  }

  if (query.isError) {
    return (
      <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-destructive">
        <span>Could not check client authorization.</span>
        <Button className="h-8 text-xs" variant="outline" onClick={() => query.refetch()}>Retry</Button>
      </div>
    );
  }

  const connection = query.data;
  const clients = (connection.clients ?? []).filter((client) => client.name === "issuer" || client.name === "verifier");
  const platformAssisted = connection.clientAcceptance === "platform-assisted";
  if (!clients.length) {
    return (
      <p className="mt-3 text-xs text-muted-foreground">
        Client authorization status is unavailable for this signing service.
      </p>
    );
  }

  return (
    <div className="mt-3 space-y-2">
      <p className="text-sm font-medium">Client authorization</p>
      {clients.map((client) => {
        const name = backendClient(client.name);
        const label = clientLabel(name);
        if (client.known) {
          return (
            <p key={client.name} className="text-xs text-muted-foreground">
              ✓ {label} accepted{client.registered ? " · used" : " · awaiting first use"}
            </p>
          );
        }

        if (!platformAssisted) {
          return (
            <div key={client.name} className="space-y-1 text-xs">
              <p>✗ {label} is not accepted. Add this public key to the signing service:</p>
              {client.publicKey && <code className="block overflow-x-auto rounded bg-background/60 p-2">{client.publicKey}</code>}
            </div>
          );
        }

        const failed = failedClient === name;
        return (
          <div key={client.name} className="flex flex-wrap items-center gap-2 text-xs">
            <span>✗ {label} is not accepted.</span>
            <Button
              className="h-8 text-xs"
              variant="outline"
              disabled={register.isPending}
              onClick={() => register.mutate({
                tenantId,
                providerId: provider.id,
                client: name,
                scope: provider.scope,
              })}
            >{failed ? `Retry ${label}` : `Accept ${label}`}</Button>
          </div>
        );
      })}
      {!platformAssisted && (
        <p className="text-xs text-muted-foreground">
          Platform-assisted registration is disabled. Configure these public keys in the signing service.
        </p>
      )}
    </div>
  );
}

export function SigningProviderCard({ tenantId }: { tenantId?: string }) {
  const queryClient = useQueryClient();
  const queryKey = ["signing-providers", tenantId ?? "platform"];
  const { data: providers = [], isPending } = useQuery({
    queryKey,
    queryFn: () => getSigningProviders(tenantId),
  });
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [endpoint, setEndpoint] = useState("");
  const [bearerToken, setBearerToken] = useState("");
  const [authenticationMode, setAuthenticationMode] = useState("none");
  const [connection, setConnection] = useState<Awaited<ReturnType<typeof checkSigningProviderConnection>> | null>(null);
  const clearConnection = () => setConnection(null);

  const invalidate = () => queryClient.invalidateQueries({ queryKey });
  const create = useMutation({
    mutationFn: createSigningProvider,
    onSuccess: async () => {
      await invalidate();
      setOpen(false);
      setName("");
      setEndpoint("");
      setBearerToken("");
      setAuthenticationMode("none");
      setConnection(null);
      toast.success("Signing provider created.");
    },
    onError: (error) => toast.error("Could not create signing provider.", { description: error.message }),
  });
  const check = useMutation({
    mutationFn: checkSigningProviderConnection,
    onSuccess: (result) => {
      setConnection(result);
      toast.success(`Connected to ${result.scheme} signing provider.`);
    },
    onError: (error) => {
      setConnection(null);
      toast.error("Could not connect to signing provider.", { description: error.message });
    },
  });
  const registerClient = useMutation({
    mutationFn: registerSigningProviderClient,
    onSuccess: (result) => {
      setConnection(result);
      toast.success("Signing client accepted.");
    },
    onError: (error) => toast.error("Could not register signing client.", { description: error.message }),
  });
  const remove = useMutation({
    mutationFn: deleteSigningProvider,
    onSuccess: invalidate,
    onError: (error) => toast.error("Could not delete signing provider.", { description: error.message }),
  });
  const refresh = useMutation({
    mutationFn: refreshSigningProvider,
    onSuccess: async () => {
      await invalidate();
      toast.success("Signing provider capabilities refreshed.");
    },
    onError: (error) => toast.error("Could not refresh signing provider capabilities.", { description: error.message }),
  });

  return (
    <Card className="mt-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-2xl font-semibold">Signing providers</h2>
          <p className="mt-1.5 text-muted-foreground">
            {tenantId
              ? "Configure the services that hold this organisation's keys. Platform providers are inherited and read-only."
              : "Configure signing services available to every organisation."}
          </p>
        </div>
        <Button onClick={() => setOpen(true)}><IconPlus /> Add provider</Button>
      </div>
      <div className="mt-6 space-y-3">
        {isPending && <p className="text-sm text-muted-foreground">Loading providers…</p>}
        {!isPending && providers.length === 0 && (
          <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">
            No signing provider has been configured. Create one before adding a
            key or issuing credentials.
          </p>
        )}
        {providers.map((provider) => (
          <div key={provider.id} className="flex flex-wrap items-center gap-3 rounded-xl border p-4">
            <IconServer className="size-5" />
            <div className="min-w-48 flex-1">
              <p className="font-semibold">{provider.name}</p>
              <p className="text-sm text-muted-foreground">
                URI scheme: {provider.scheme ?? "unavailable"}{provider.endpoint ? ` · ${provider.endpoint}` : ""}
                {provider.scope === "global" && tenantId ? " · inherited from platform · read-only" : provider.scope === "global" ? " · platform-wide" : " · organisation-owned"}
                {provider.defaultProvider ? " · default" : ""}
              </p>
              {canManageClients(provider.scope, tenantId) && (
                <ClientAuthorization provider={provider} tenantId={tenantId} />
              )}
            </div>
            <Button
              variant="ghost"
              size="icon"
              disabled={refresh.isPending || (Boolean(tenantId) && provider.scope === "global")}
              aria-label={`Refresh capabilities for ${provider.name}`}
              onClick={() => refresh.mutate({ tenantId, providerId: provider.id })}
            ><IconRefresh className={refresh.isPending ? "animate-spin" : undefined} /></Button>
            <Button
              variant="ghost"
              size="icon"
              disabled={remove.isPending || (Boolean(tenantId) && provider.scope === "global")}
              onClick={() => remove.mutate({ tenantId, providerId: provider.id })}
            ><IconTrash /></Button>
          </div>
        ))}
      </div>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add signing provider</DialogTitle>
            <DialogDescription>
              These are the platform's own credentials for the signing service; they are encrypted before they are stored and never leave the platform. The URI scheme is discovered from the signing service.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <Field><FieldLabel>Name</FieldLabel><Input value={name} onChange={(event) => setName(event.target.value)} /></Field>
            <Field><FieldLabel>Endpoint</FieldLabel><Input placeholder="http://localhost:8086" value={endpoint} onChange={(event) => { setEndpoint(event.target.value); clearConnection(); }} /></Field>
            <Field>
              <FieldLabel>Authentication</FieldLabel>
              <select className="h-9 w-full rounded-md border bg-transparent px-3 text-sm" value={authenticationMode} onChange={(event) => { setAuthenticationMode(event.target.value); clearConnection(); }}>
                <option value="none">None (local development only)</option>
                <option value="bearer">Bearer token</option>
                <option value="mtls">Mutual TLS</option>
                <option value="registered">Registered key</option>
              </select>
            </Field>
            {authenticationMode === "bearer" && <Field><FieldLabel>Bearer token</FieldLabel><Input type="password" value={bearerToken} onChange={(event) => { setBearerToken(event.target.value); clearConnection(); }} /></Field>}
            {connection && (
              <div className="space-y-3 rounded-md border border-green-600/30 bg-green-50 p-3 text-sm text-green-800 dark:bg-green-950/30 dark:text-green-200">
                <p>
                  Connected. Scheme: <strong>{connection.scheme}</strong>. Algorithms: {connection.supportedAlgorithms.join(", ") || "none reported"}.
                </p>
                {connection.clients && connection.clients.length > 0 && (
                  <div className="space-y-2">
                    <p className="font-medium">Clients</p>
                    {connection.clients.map((client) => (
                      <div key={client.name} className="space-y-1">
                        <p>
                          {client.known ? "✓" : "✗"} {client.name}
                          {client.known && client.registered === false ? " · not registered" : ""}
                          {client.lastUse ? ` · last used ${new Date(client.lastUse).toLocaleString()}` : ""}
                        </p>
                        {/* The signing service does not know this backend yet. Its public key is
                            what an operator adds there; no secret is shown or sent. */}
                        {!client.known && client.publicKey && (client.name === "issuer" || client.name === "verifier") && (
                          connection.clientAcceptance === "platform-assisted" ? (
                            <Button variant="outline" disabled={registerClient.isPending} onClick={() => registerClient.mutate({
                              tenantId,
                              client: backendClient(client.name),
                              provider: { name: name || "Connection check", endpoint, bearerToken, authenticationMode },
                            })}>Accept {client.name}</Button>
                          ) : <code className="block overflow-x-auto rounded bg-background/60 p-2 text-xs">
                              {client.publicKey}
                            </code>
                        )}
                      </div>
                    ))}
                    {connection.clientAcceptance !== "platform-assisted" && connection.clients.some((client) => !client.known) && (
                      <p className="text-xs">
                        Add the keys above to the signing service as accepted clients, then check again.
                      </p>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
            <Button
              variant="secondary"
              disabled={!endpoint || check.isPending || create.isPending}
              onClick={() => check.mutate({ tenantId, provider: { name: name || "Connection check", endpoint, bearerToken, authenticationMode } })}
            >{check.isPending ? "Checking…" : "Check connection"}</Button>
            <Button
              disabled={!name || !endpoint || create.isPending || check.isPending}
              onClick={() => create.mutate({ tenantId, provider: { name, endpoint, bearerToken, authenticationMode, defaultProvider: true } })}
            >Create provider</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
