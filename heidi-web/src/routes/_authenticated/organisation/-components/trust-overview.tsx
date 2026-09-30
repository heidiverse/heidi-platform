// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  type Icon,
  IconBuildingBank,
  IconCertificate,
  IconHierarchy2,
  IconWorld,
} from "@tabler/icons-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import {
  type TrustMechanism,
  type TrustOverviewInput,
  type TrustStatus,
  trustMechanisms,
  trustRoles,
  trustStatus,
} from "./trust-overview-status";

const mechanismDetails: Record<
  TrustMechanism,
  { title: string; description: string; icon: Icon }
> = {
  Custom: {
    title: "Custom / manual",
    description:
      "Signing and verification for operator-managed custom profiles.",
    icon: IconWorld,
  },
  EUDI: {
    title: "EUDI Profile 1.0",
    description:
      "Access and credential-signing certificates for EUDI Wallet ecosystem roles.",
    icon: IconCertificate,
  },
  Switzerland: {
    title: "Swiss Profile 1.0",
    description:
      "Swiss trust registry identity, DID and signing material for issuer and verifier roles.",
    icon: IconBuildingBank,
  },
  OIDF: {
    title: "OpenID Federation",
    description:
      "Federation signing, superiors and authority relationships for this identity.",
    icon: IconHierarchy2,
  },
};

const statusDetails: Record<
  TrustStatus,
  { label: string; variant: "secondary" | "warning" | "success" | "destructive" }
> = {
  NOT_IN_USE: { label: "Not in use", variant: "secondary" },
  SETUP_INCOMPLETE: { label: "Setup incomplete", variant: "warning" },
  CONFIGURED: { label: "Configured", variant: "success" },
  NEEDS_ATTENTION: { label: "Needs attention", variant: "destructive" },
};

export type TrustOverviewProps = TrustOverviewInput & {
  selected?: TrustMechanism;
  onSelect?: (mechanism: TrustMechanism) => void;
};

export function TrustOverview({
  declaredTrustSystems,
  slots,
  federation,
  issues,
  selected,
  onSelect,
}: TrustOverviewProps) {
  const input = { declaredTrustSystems, slots, federation, issues };

  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      {trustMechanisms.map((mechanism) => (
        <TrustCard
          key={mechanism}
          mechanism={mechanism}
          status={trustStatus(mechanism, input)}
          roles={trustRoles(mechanism, input)}
          selected={selected === mechanism}
          onSelect={onSelect}
        />
      ))}
    </div>
  );
}

function TrustCard({
  mechanism,
  status,
  roles,
  selected,
  onSelect,
}: {
  mechanism: TrustMechanism;
  status: TrustStatus;
  roles: ReturnType<typeof trustRoles>;
  selected: boolean;
  onSelect?: (mechanism: TrustMechanism) => void;
}) {
  const detail = mechanismDetails[mechanism];
  const state = statusDetails[status];
  const MechanismIcon = detail.icon;

  return (
    <Card className={cn("flex min-h-56 flex-col", selected && "border-primary")}>
      <div className="flex items-start justify-between gap-3">
        <MechanismIcon className="size-6 shrink-0" aria-hidden="true" />
        <Badge variant={state.variant}>{state.label}</Badge>
      </div>
      <h2 className="mt-4 text-lg font-semibold">{detail.title}</h2>
      <p className="mt-2 flex-1 text-sm text-muted-foreground">
        {detail.description}
      </p>
      {roles.length > 0 && (
        <div className="mt-4 space-y-1.5 text-sm">
          {roles.map((role) => (
            <div key={role.label} className="flex items-center justify-between gap-3">
              <span>{role.label}</span>
              <span className={role.configured ? "text-success" : "text-warning"}>
                {role.configured ? "Configured" : "Incomplete"}
              </span>
            </div>
          ))}
        </div>
      )}
      {onSelect && (
        <Button
          className="mt-4 self-start"
          variant="outline"
          onClick={() => onSelect(mechanism)}
        >
          {status === "NOT_IN_USE" ? "Set up" : "View configuration"}
        </Button>
      )}
    </Card>
  );
}
