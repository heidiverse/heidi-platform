// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

/** Versioned profile identifiers understood by the platform profile catalogue. */
export const EcosystemProfileId = {
  EudiIssuance: "EUDI_ISSUANCE_2026_1",
  SwissIssuance: "SWISS_ISSUANCE_2026_1",
  OidfIssuance: "OIDF_ISSUANCE_2026_1",
  CustomIssuance: "CUSTOM_ISSUANCE_2026_1",
  EudiPresentation: "EUDI_PRESENTATION_2026_1",
  SwissPresentation: "SWISS_PRESENTATION_2026_1",
  OidfPresentation: "OIDF_PRESENTATION_2026_1",
  CustomPresentation: "CUSTOM_PRESENTATION_2026_1",
} as const;

export type IssuanceProfileId =
  | typeof EcosystemProfileId.EudiIssuance
  | typeof EcosystemProfileId.SwissIssuance
  | typeof EcosystemProfileId.OidfIssuance
  | typeof EcosystemProfileId.CustomIssuance;

export type PresentationProfileId =
  | typeof EcosystemProfileId.EudiPresentation
  | typeof EcosystemProfileId.SwissPresentation
  | typeof EcosystemProfileId.OidfPresentation
  | typeof EcosystemProfileId.CustomPresentation;

export type ProfileTrustSystem =
  | "EUDI"
  | "Switzerland"
  | "Custom"
  | "OIDF"
  | "Default";

export type CustomProfileIdentity = {
  customProfileName?: string | null;
};

export function customProfileDisplayName(
  identity: CustomProfileIdentity | null | undefined,
) {
  const name = identity?.customProfileName?.trim();
  return name || undefined;
}

export function trustFrameworkDisplayName(
  value: string | null | undefined,
  identity?: CustomProfileIdentity | null,
) {
  switch (value) {
    case "EUDI":
      return "EUDI";
    case "Switzerland":
      return "Swiss trust infrastructure";
    case "OIDF":
      return "OpenID Federation";
    case "Custom":
    case "Default":
      return customProfileDisplayName(identity) ?? "Custom trust framework";
    default:
      return value ?? "Not selected";
  }
}

export type ProfileOption<T extends string> = {
  id: T;
  family: "eudi" | "swiss" | "oidf" | "custom";
  labelId: string;
  defaultLabel: string;
  descriptionId: string;
  defaultDescription: string;
  derivedValues: string[];
  clientIdScheme?: string;
  trustSystem?: ProfileTrustSystem;
  /** Whether the issuance profile requires encrypted Credential Requests. */
  requestEncryptionRequired?: boolean;
};

export const issuanceProfileOptions: readonly ProfileOption<IssuanceProfileId>[] =
  [
    {
      id: EcosystemProfileId.EudiIssuance,
      family: "eudi",
      labelId: "ecosystemProfile.eudiIssuance",
      defaultLabel: "EUDI Profile 1.0",
      descriptionId: "ecosystemProfile.eudiIssuance.description",
      defaultDescription: "EUDI Profile 1.0 credential issuance",
      derivedValues: ["EUDI access certificates", "HAIP credential policy"],
      trustSystem: "EUDI",
      requestEncryptionRequired: true,
    },
    {
      id: EcosystemProfileId.SwissIssuance,
      family: "swiss",
      labelId: "ecosystemProfile.swissIssuance",
      defaultLabel: "Swiss Profile 1.0",
      descriptionId: "ecosystemProfile.swissIssuance.description",
      defaultDescription: "Swiss Profile 1.0 credential issuance",
      derivedValues: ["Swiss trust registry", "ES256 and encrypted transport"],
      trustSystem: "Switzerland",
      requestEncryptionRequired: true,
    },
    {
      id: EcosystemProfileId.CustomIssuance,
      family: "custom",
      labelId: "ecosystemProfile.customIssuance",
      defaultLabel: "Custom / manual",
      descriptionId: "ecosystemProfile.customIssuance.description",
      defaultDescription: "Operator-managed custom credential issuance",
      derivedValues: ["Operator-managed PKI", "X.509 SAN DNS"],
      trustSystem: "Custom",
    },
    {
      id: EcosystemProfileId.OidfIssuance,
      family: "oidf",
      labelId: "ecosystemProfile.oidfIssuance",
      defaultLabel: "OpenID Federation",
      descriptionId: "ecosystemProfile.oidfIssuance.description",
      defaultDescription: "OpenID Federation credential issuance",
      derivedValues: ["Federation entity statements", "Operator-managed signing"],
      trustSystem: "OIDF",
    },
  ] as const;

export const presentationProfileOptions: readonly ProfileOption<PresentationProfileId>[] =
  [
    {
      id: EcosystemProfileId.EudiPresentation,
      family: "eudi",
      labelId: "ecosystemProfile.eudiPresentation",
      defaultLabel: "EUDI Profile 1.0",
      descriptionId: "ecosystemProfile.eudiPresentation.description",
      defaultDescription: "EUDI Profile 1.0 presentation requests",
      derivedValues: ["EUDI access certificates", "HAIP request policy"],
      clientIdScheme: "x509_hash",
      trustSystem: "EUDI",
    },
    {
      id: EcosystemProfileId.SwissPresentation,
      family: "swiss",
      labelId: "ecosystemProfile.swissPresentation",
      defaultLabel: "Swiss Profile 1.0",
      descriptionId: "ecosystemProfile.swissPresentation.description",
      defaultDescription: "Swiss Profile 1.0 presentation requests",
      derivedValues: ["Swiss trust registry", "Encrypted direct_post.jwt"],
      clientIdScheme: "decentralized_identifier",
      trustSystem: "Switzerland",
    },
    {
      id: EcosystemProfileId.CustomPresentation,
      family: "custom",
      labelId: "ecosystemProfile.customPresentation",
      defaultLabel: "Custom / manual",
      descriptionId: "ecosystemProfile.customPresentation.description",
      defaultDescription: "Operator-managed custom presentation requests",
      derivedValues: ["Operator-managed PKI", "X.509 SAN DNS"],
      clientIdScheme: "x509_san_dns",
      trustSystem: "Custom",
    },
    {
      id: EcosystemProfileId.OidfPresentation,
      family: "oidf",
      labelId: "ecosystemProfile.oidfPresentation",
      defaultLabel: "OpenID Federation",
      descriptionId: "ecosystemProfile.oidfPresentation.description",
      defaultDescription: "OpenID Federation presentation requests",
      derivedValues: ["Federation entity identifier", "Encrypted direct_post.jwt"],
      clientIdScheme: "openid_federation",
      trustSystem: "OIDF",
    },
  ] as const;

export const issuanceProfileIds = issuanceProfileOptions.map(
  (option) => option.id,
) as [IssuanceProfileId, ...IssuanceProfileId[]];

export const presentationProfileIds = presentationProfileOptions.map(
  (option) => option.id,
) as [PresentationProfileId, ...PresentationProfileId[]];

export function findIssuanceProfile(id: string | null | undefined) {
  return issuanceProfileOptions.find((option) => option.id === id);
}

export function findPresentationProfile(id: string | null | undefined) {
  return presentationProfileOptions.find((option) => option.id === id);
}

type IdentityTrustConfiguration = {
  trustSystems?: readonly string[] | null;
  defaultTrustSystem?: string | null;
};

export function trustSystemsForIdentity(
  identity: IdentityTrustConfiguration | null | undefined,
) {
  const supported: ProfileTrustSystem[] = [
    "EUDI",
    "Switzerland",
    "Custom",
    "OIDF",
    "Default",
  ];
  const trustSystems = new Set<ProfileTrustSystem>();
  for (const trustSystem of identity?.trustSystems ?? []) {
    if (supported.includes(trustSystem as ProfileTrustSystem)) {
      trustSystems.add(trustSystem as ProfileTrustSystem);
    }
  }
  if (
    identity?.defaultTrustSystem &&
    supported.includes(identity.defaultTrustSystem as ProfileTrustSystem)
  ) {
    trustSystems.add(identity.defaultTrustSystem as ProfileTrustSystem);
  }
  return [...trustSystems];
}

export function compatibleIssuanceProfiles(trustSystems: readonly string[]) {
  const profiles = issuanceProfileOptions.filter((option) =>
    trustSystems.includes(option.trustSystem ?? "Default"),
  );
  const customProfile = issuanceProfileOptions.find(
    (option) => option.family === "custom",
  );

  // Custom is an independent framework and remains available alongside
  // framework-specific profiles.
  if (customProfile && !profiles.includes(customProfile)) {
    profiles.push(customProfile);
  }
  return profiles;
}

export function compatiblePresentationProfiles(
  trustSystems: readonly string[],
) {
  const profiles = presentationProfileOptions.filter((option) =>
    trustSystems.includes(option.trustSystem ?? "Default"),
  );
  const customProfile = presentationProfileOptions.find(
    (option) => option.family === "custom",
  );

  // Custom is an independent framework and remains available alongside
  // framework-specific profiles.
  if (customProfile && !profiles.includes(customProfile)) {
    profiles.push(customProfile);
  }
  return profiles;
}

export function trustSystemForIssuanceProfile(
  value: string | null | undefined,
) {
  return findIssuanceProfile(value)?.trustSystem ?? "Default";
}

export function trustSystemForPresentationProfile(
  value: string | null | undefined,
) {
  return findPresentationProfile(value)?.trustSystem ?? "Default";
}

export function issuanceProfileForTrustSystem(
  value: string | null | undefined,
) {
  if (value === "EUDI") return EcosystemProfileId.EudiIssuance;
  if (value === "Switzerland") return EcosystemProfileId.SwissIssuance;
  if (value === "OIDF") return EcosystemProfileId.OidfIssuance;
  if (value === "Custom") return EcosystemProfileId.CustomIssuance;
  return EcosystemProfileId.CustomIssuance;
}

export function presentationProfileForIssuanceProfile(
  value: string | null | undefined,
) {
  if (value === EcosystemProfileId.EudiIssuance) {
    return EcosystemProfileId.EudiPresentation;
  }
  if (value === EcosystemProfileId.SwissIssuance) {
    return EcosystemProfileId.SwissPresentation;
  }
  if (value === EcosystemProfileId.OidfIssuance) {
    return EcosystemProfileId.OidfPresentation;
  }
  if (value === EcosystemProfileId.CustomIssuance) {
    return EcosystemProfileId.CustomPresentation;
  }
  return undefined;
}

export function presentationProfileForTrustSystem(
  value: string | null | undefined,
) {
  if (value === "EUDI") return EcosystemProfileId.EudiPresentation;
  if (value === "Switzerland") return EcosystemProfileId.SwissPresentation;
  if (value === "OIDF") return EcosystemProfileId.OidfPresentation;
  if (value === "Custom") return EcosystemProfileId.CustomPresentation;
  return EcosystemProfileId.CustomPresentation;
}
