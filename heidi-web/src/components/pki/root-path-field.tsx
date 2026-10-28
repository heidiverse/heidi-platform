// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { MAX_PATH_LENGTH, type RootPathChoice } from "./ca-options";

export function RootPathField({ value, onChange, disabled }: {
  value: RootPathChoice; onChange: (value: RootPathChoice) => void; disabled?: boolean;
}) {
  return <div className="space-y-4 rounded-xl border bg-muted/30 p-4">
    <Field>
      <FieldLabel htmlFor="root-path-mode">Root CA path length</FieldLabel>
      <select id="root-path-mode" className="h-10 rounded-md border bg-surface px-3" value={value.mode} disabled={disabled}
        onChange={(event) => onChange({ ...value, mode: event.target.value as RootPathChoice["mode"] })}>
        <option value="" disabled>Choose an intermediate limit</option>
        <option value="LIMITED">Set a maximum depth</option>
        <option value="UNLIMITED">Allow unlimited intermediates</option>
      </select>
      <FieldDescription>This constraint is embedded in the root certificate and applies to every branch.</FieldDescription>
    </Field>
    {value.mode === "LIMITED" && <Field>
      <FieldLabel htmlFor="root-path-limit">Maximum intermediate levels</FieldLabel>
      <Input id="root-path-limit" type="number" min={0} max={MAX_PATH_LENGTH} step={1} value={value.limit} disabled={disabled}
        onChange={(event) => onChange({ ...value, limit: event.target.value })} placeholder="e.g. 2" />
      <FieldDescription>0: the root issues leaf certificates directly. 2: root → intermediate → intermediate → leaf.</FieldDescription>
    </Field>}
    {value.mode === "UNLIMITED" && <p className="text-sm text-muted-foreground">The root places no depth limit on its intermediate CAs.</p>}
  </div>;
}
