// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { FormattedMessage } from "react-intl";
import {
  type CustomProfileIdentity,
  customProfileDisplayName,
  type ProfileOption,
} from "@/types/ecosystem-profile";

export function EcosystemProfileLabel({
  profile,
  identity,
}: {
  profile: ProfileOption<string>;
  identity?: CustomProfileIdentity | null;
}) {
  const customName = profile.family === "custom"
    ? customProfileDisplayName(identity)
    : undefined;
  return customName ?? (
    <FormattedMessage id={profile.labelId} defaultMessage={profile.defaultLabel} />
  );
}
