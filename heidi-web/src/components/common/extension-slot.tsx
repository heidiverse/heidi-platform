// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  getSlotEntries,
  type SlotName,
  type SlotProps,
} from "@/lib/extensions";

/**
 * Renders whatever extensions have registered for a slot, in registration order.
 * Renders nothing when none have, which is the open-source case.
 */
export function ExtensionSlot<Name extends SlotName>({
  slot,
  ...props
}: { slot: Name } & SlotProps[Name]) {
  return (
    <>
      {getSlotEntries(slot).map(({ id, Component }) => (
        <Component key={id} {...(props as SlotProps[Name])} />
      ))}
    </>
  );
}
