// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { CredentialEncryptionPolicy } from "@/lib/api/issuer-definitions/api";
import { issuanceProfileOptions } from "../../../../types/ecosystem-profile.ts";
import {
  type ConfiguredTrustSystem,
  type TrustSystem,
  trustFrameworks,
} from "../../../../types/trust-system.ts";

export type PolicyList = keyof Pick<
  CredentialEncryptionPolicy,
  | "requestAlgValues"
  | "requestEncValues"
  | "requestZipValues"
  | "responseAlgValues"
  | "responseEncValues"
  | "responseZipValues"
>;

export type PolicyMode = "all" | "custom";

const policyLists: PolicyList[] = [
  "requestAlgValues",
  "requestEncValues",
  "requestZipValues",
  "responseAlgValues",
  "responseEncValues",
  "responseZipValues",
];

export function policyMode(values: readonly string[]): PolicyMode {
  return values.length === 0 ? "all" : "custom";
}

export function customizedGroupCount(policy: CredentialEncryptionPolicy | undefined) {
  if (!policy) return 0;

  return policyLists.filter((list) => policy[list].length > 0).length;
}

export function missingRequestDecryptionTrustSystems(
  policy: CredentialEncryptionPolicy | undefined,
  trustSystems: readonly string[] = [],
) {
  if (!policy) return [] as TrustSystem[];

  const configuredFrameworks = [
    ...new Set(
      trustSystems.filter(
        (trustSystem): trustSystem is ConfiguredTrustSystem =>
          trustFrameworks.includes(trustSystem),
      ),
    ),
  ];
  const requiredTrustSystems = requestEncryptionRequiredTrustSystems(
    policy,
    configuredFrameworks,
  );

  return requiredTrustSystems.filter(
    (requiredTrustSystem) =>
      !(policy.requestKeys ?? []).some(
        (key) =>
          key.trustSystem == null || key.trustSystem === requiredTrustSystem,
      ),
  );
}

export function requestEncryptionRequiredTrustSystems(
  policy: CredentialEncryptionPolicy | undefined,
  trustSystems: readonly string[] = [],
): TrustSystem[] {
  if (!policy) return [];

  const configuredFrameworks = [
    ...new Set(
      trustSystems.filter(
        (trustSystem): trustSystem is ConfiguredTrustSystem =>
          trustFrameworks.includes(trustSystem),
      ),
    ),
  ];
  if (configuredFrameworks.length === 0) {
    return policy.requestEncryptionRequired === true ? ["Default"] : [];
  }

  const profileRequired = configuredFrameworks.filter((trustSystem) =>
    issuanceProfileOptions.some(
      (profile) =>
        profile.trustSystem === trustSystem &&
        profile.requestEncryptionRequired === true,
    ),
  );
  return policy.requestEncryptionRequired === true
    ? configuredFrameworks
    : profileRequired;
}

export function hasMissingRequestDecryptionKey(
  policy: CredentialEncryptionPolicy | undefined,
  trustSystems: readonly string[] = [],
) {
  return missingRequestDecryptionTrustSystems(policy, trustSystems).length > 0;
}

export function setPolicyListMode(
  policy: CredentialEncryptionPolicy,
  list: PolicyList,
  supported: string[],
  mode: PolicyMode,
) {
  return {
    ...policy,
    [list]: mode === "all" ? [] : [...supported],
  };
}
