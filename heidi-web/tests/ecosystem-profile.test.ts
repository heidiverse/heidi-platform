// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { deepEqual, equal } from "node:assert/strict";
import { test } from "node:test";
import {
  EcosystemProfileId,
  compatibleIssuanceProfiles,
  compatiblePresentationProfiles,
  customProfileDisplayName,
  findIssuanceProfile,
  findPresentationProfile,
  issuanceProfileForTrustSystem,
  issuanceProfileOptions,
  presentationProfileForIssuanceProfile,
  presentationProfileForTrustSystem,
  presentationProfileOptions,
  trustFrameworkDisplayName,
  trustSystemsForIdentity,
} from "../src/types/ecosystem-profile.ts";

test("profile options are role-specific and versioned", () => {
  equal(issuanceProfileOptions.length, 4);
  equal(presentationProfileOptions.length, 4);
  equal(findIssuanceProfile(EcosystemProfileId.EudiPresentation), undefined);
  equal(findPresentationProfile(EcosystemProfileId.EudiIssuance), undefined);
  equal(
    new Set([
      ...issuanceProfileOptions.map(({ id }) => id),
      ...presentationProfileOptions.map(({ id }) => id),
    ]).size,
    8,
  );
});

test("legacy trust systems map to explicit profile selections", () => {
  deepEqual(
    [
      issuanceProfileForTrustSystem("EUDI"),
      issuanceProfileForTrustSystem("Switzerland"),
      issuanceProfileForTrustSystem("OIDF"),
      issuanceProfileForTrustSystem(undefined),
    ],
    [
      EcosystemProfileId.EudiIssuance,
      EcosystemProfileId.SwissIssuance,
      EcosystemProfileId.OidfIssuance,
      EcosystemProfileId.CustomIssuance,
    ],
  );
  deepEqual(
    [
      presentationProfileForTrustSystem("EUDI"),
      presentationProfileForTrustSystem("Switzerland"),
      presentationProfileForTrustSystem("OIDF"),
      presentationProfileForTrustSystem(undefined),
    ],
    [
      EcosystemProfileId.EudiPresentation,
      EcosystemProfileId.SwissPresentation,
      EcosystemProfileId.OidfPresentation,
      EcosystemProfileId.CustomPresentation,
    ],
  );
});

test("credential issuance profiles infer matching presentation profiles", () => {
  deepEqual(
    [
      presentationProfileForIssuanceProfile(EcosystemProfileId.EudiIssuance),
      presentationProfileForIssuanceProfile(EcosystemProfileId.SwissIssuance),
      presentationProfileForIssuanceProfile(EcosystemProfileId.OidfIssuance),
      presentationProfileForIssuanceProfile("unknown"),
    ],
    [
      EcosystemProfileId.EudiPresentation,
      EcosystemProfileId.SwissPresentation,
      EcosystemProfileId.OidfPresentation,
      undefined,
    ],
  );
});

test("schema profile choices include custom alongside identity trust systems", () => {
  deepEqual(
    compatibleIssuanceProfiles(["Switzerland"]).map(({ id }) => id),
    [EcosystemProfileId.SwissIssuance, EcosystemProfileId.CustomIssuance],
  );
  deepEqual(
    compatiblePresentationProfiles(["EUDI"]).map(({ id }) => id),
    [
      EcosystemProfileId.EudiPresentation,
      EcosystemProfileId.CustomPresentation,
    ],
  );
  deepEqual(
    compatiblePresentationProfiles(["OIDF"]).map(({ id }) => id),
    [EcosystemProfileId.OidfPresentation, EcosystemProfileId.CustomPresentation],
  );
  deepEqual(
    compatibleIssuanceProfiles(["Default"]).map(({ id }) => id),
    [EcosystemProfileId.CustomIssuance],
  );
  deepEqual(
    compatibleIssuanceProfiles([]).map(({ id }) => id),
    [EcosystemProfileId.CustomIssuance],
  );
});

test("identity trust systems include its default without duplicates", () => {
  deepEqual(
    trustSystemsForIdentity({
      trustSystems: ["EUDI", "Switzerland"],
      defaultTrustSystem: "EUDI",
    }),
    ["EUDI", "Switzerland"],
  );
});

test("custom profile names are display-only and ignore blank values", () => {
  equal(customProfileDisplayName({ customProfileName: "Operations" }), "Operations");
  equal(customProfileDisplayName({ customProfileName: "  Operations  " }), "Operations");
  equal(customProfileDisplayName({ customProfileName: "   " }), undefined);
  equal(customProfileDisplayName(undefined), undefined);
});

test("trust framework names match the identity trust overview", () => {
  equal(trustFrameworkDisplayName("EUDI"), "EUDI");
  equal(trustFrameworkDisplayName("Switzerland"), "Swiss trust infrastructure");
  equal(trustFrameworkDisplayName("OIDF"), "OpenID Federation");
  equal(
    trustFrameworkDisplayName("Custom", { customProfileName: "Operations" }),
    "Operations",
  );
});
