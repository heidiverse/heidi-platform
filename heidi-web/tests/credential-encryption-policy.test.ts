// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { deepEqual, equal } from "node:assert/strict";
import { test } from "node:test";
import {
  customizedGroupCount,
  hasMissingRequestDecryptionKey,
  missingRequestDecryptionTrustSystems,
  policyMode,
  requestEncryptionRequiredTrustSystems,
  setPolicyListMode,
} from "../src/routes/_authenticated/organisation/-components/credential-encryption-policy.ts";

const policy = {
  requestAlgValues: [],
  requestEncValues: [],
  requestZipValues: [],
  responseAlgValues: [],
  responseEncValues: [],
  responseZipValues: [],
  requestEncryptionRequired: null,
  responseEncryptionRequired: null,
};

test("empty encryption lists explicitly mean all supported values", () => {
  equal(policyMode(policy.requestAlgValues), "all");
  equal(customizedGroupCount(policy), 0);
});

test("custom mode starts with every supported value selected", () => {
  const changed = setPolicyListMode(
    policy,
    "requestAlgValues",
    ["ECDH-ES", "ECDH-ES+A256KW"],
    "custom",
  );

  deepEqual(changed.requestAlgValues, ["ECDH-ES", "ECDH-ES+A256KW"]);
  equal(customizedGroupCount(changed), 1);
});

test("all-supported mode clears a custom allowlist", () => {
  const custom = { ...policy, responseEncValues: ["A256GCM"] };
  const changed = setPolicyListMode(
    custom,
    "responseEncValues",
    ["A256GCM"],
    "all",
  );

  deepEqual(changed.responseEncValues, []);
});

test("required request encryption warns when no decryption key is available", () => {
  equal(
    hasMissingRequestDecryptionKey(
      { ...policy, requestEncryptionRequired: true, requestKeys: [] },
    ),
    true,
  );
  equal(
    hasMissingRequestDecryptionKey(
      {
        ...policy,
        requestEncryptionRequired: true,
        requestKeys: [{ keyId: "request-v1", keyUri: "software://request-v1", algorithm: "ECDH-ES" }],
      },
    ),
    false,
  );
  equal(
    hasMissingRequestDecryptionKey(
      { ...policy, requestKeys: [] },
      ["EUDI"],
    ),
    true,
  );
  equal(hasMissingRequestDecryptionKey(policy), false);
});

test("issuance profiles can require encryption independently by trust framework", () => {
  deepEqual(
    requestEncryptionRequiredTrustSystems(policy, ["EUDI", "Switzerland", "Custom", "OIDF"]),
    ["EUDI", "Switzerland"],
  );
  deepEqual(
    missingRequestDecryptionTrustSystems(
      { ...policy, requestKeys: [{ keyId: "request-eudi", keyUri: "software://request-eudi", algorithm: "ECDH-ES", trustSystem: "EUDI" as const }] },
      ["EUDI", "Switzerland", "Custom", "OIDF"],
    ),
    ["Switzerland"],
  );
});

test("scoped request decryption keys only satisfy their trust framework", () => {
  const scoped = {
    ...policy,
    requestKeys: [{
      keyId: "request-eudi",
      keyUri: "software://request-eudi",
      algorithm: "ECDH-ES",
      trustSystem: "EUDI" as const,
    }],
  };

  equal(hasMissingRequestDecryptionKey(scoped, ["EUDI"]), false);
  equal(hasMissingRequestDecryptionKey(scoped, ["Switzerland"]), true);
  equal(
    hasMissingRequestDecryptionKey(scoped, ["EUDI", "Switzerland"]),
    true,
  );
  deepEqual(
    missingRequestDecryptionTrustSystems(scoped, ["EUDI", "Switzerland"]),
    ["Switzerland"],
  );
});

test("Custom and OpenID Federation are independent request-key scopes", () => {
  const scoped = {
    ...policy,
    requestKeys: [
      {
        keyId: "request-custom",
        keyUri: "software://request-custom",
        algorithm: "ECDH-ES",
        trustSystem: "Custom" as const,
      },
      {
        keyId: "request-oidf",
        keyUri: "software://request-oidf",
        algorithm: "ECDH-ES",
        trustSystem: "OIDF" as const,
      },
    ],
  };

  equal(hasMissingRequestDecryptionKey(scoped, ["Custom"]), false);
  equal(hasMissingRequestDecryptionKey(scoped, ["OIDF"]), false);
  equal(
    hasMissingRequestDecryptionKey(scoped, ["Custom", "OIDF", "EUDI"]),
    true,
  );
  deepEqual(
    missingRequestDecryptionTrustSystems(scoped, ["Custom", "OIDF", "EUDI"]),
    ["EUDI"],
  );
});

test("an unscoped request decryption key applies to every trust framework", () => {
  const global = {
    ...policy,
    requestEncryptionRequired: true,
    requestKeys: [{
      keyId: "request-global",
      keyUri: "software://request-global",
      algorithm: "ECDH-ES",
      trustSystem: null,
    }],
  };

  equal(hasMissingRequestDecryptionKey(global), false);
  equal(hasMissingRequestDecryptionKey(global, ["EUDI", "Switzerland"]), false);
});
