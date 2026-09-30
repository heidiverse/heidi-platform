// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

/** Values used by backend routing. `Default` is the global/unscoped fallback. */
export const TrustSystem = {
  Switzerland: "Switzerland",
  EUDI: "EUDI",
  Custom: "Custom",
  OIDF: "OIDF",
  Default: "Default",
} as const;

export type TrustSystem = (typeof TrustSystem)[keyof typeof TrustSystem];
export type ConfiguredTrustSystem = Exclude<TrustSystem, typeof TrustSystem.Default>;

/** Trust frameworks that can scope a request decryption key. */
export const trustFrameworks = [
  TrustSystem.Switzerland,
  TrustSystem.EUDI,
  TrustSystem.Custom,
  TrustSystem.OIDF,
] as const satisfies readonly ConfiguredTrustSystem[];

/** Trust frameworks that can be declared on an identity or profile. */
export const configuredTrustSystems = [
  ...trustFrameworks,
] as const satisfies readonly ConfiguredTrustSystem[];

const backendTrustSystems = [
  ...trustFrameworks,
  TrustSystem.Default,
] as const satisfies readonly TrustSystem[];

export function normalizeConfiguredTrustSystem(
  value: unknown,
): ConfiguredTrustSystem | undefined {
  return trustFrameworks.includes(value as ConfiguredTrustSystem)
    ? (value as ConfiguredTrustSystem)
    : undefined;
}

export function normalizeSigningKeyIds(
  value: unknown,
): Partial<Record<TrustSystem, string>> {
  if (!value || typeof value !== "object") return {};

  return Object.fromEntries(
    Object.entries(value).filter(
      ([trustSystem, keyId]) =>
        backendTrustSystems.includes(trustSystem as TrustSystem) &&
        typeof keyId === "string" &&
        keyId.length > 0,
    ),
  ) as Partial<Record<TrustSystem, string>>;
}
