// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { deepEqual, equal } from "node:assert/strict";
import { test } from "node:test";
import { canAddIntermediate, isTerminalCa, remainingDepth, rootPathOptions } from "../src/components/pki/ca-options.ts";
import type { IssuingSubca } from "../src/lib/api/keys/issuing-pki.ts";

function ca(id: string, limits: (number | null)[], parentCaId?: string): IssuingSubca {
  return { id, parentCaId, certificateChain: limits.map((_, i) => `${id}-${i}`),
    chainDetails: limits.map((pathLength) => ({ pathLength })) } as IssuingSubca;
}

test("root generation requires a choice and accepts zero or explicit unlimited", () => {
  equal(rootPathOptions({ mode: "", limit: "" }), undefined);
  for (const limit of ["", "-1", "1.5", "1e2", "2147483648"]) {
    equal(rootPathOptions({ mode: "LIMITED", limit }), undefined);
  }
  deepEqual(rootPathOptions({ mode: "LIMITED", limit: "0" }), { pathLengthMode: "LIMITED", pathLength: 0 });
  deepEqual(rootPathOptions({ mode: "LIMITED", limit: "2" }), { pathLengthMode: "LIMITED", pathLength: 2 });
  deepEqual(rootPathOptions({ mode: "UNLIMITED", limit: "2" }), { pathLengthMode: "UNLIMITED", pathLength: null });
});

test("ancestor limits constrain every intermediate branch", () => {
  equal(remainingDepth(ca("root", [2])), 2);
  equal(remainingDepth(ca("first", [null, 2])), 1);
  equal(remainingDepth(ca("last", [null, null, 2])), 0);
  equal(canAddIntermediate(ca("last", [null, null, 2])), false);
  equal(remainingDepth(ca("restricted", [1, null, 5])), 1);
  equal(remainingDepth(ca("unlimited", [null, null])), null);
  equal(canAddIntermediate(ca("unlimited", [null, null])), true);
});

test("pending certificates and missing metadata cannot enable intermediate creation", () => {
  equal(canAddIntermediate(ca("pending", [])), false);
  const unknown = ca("unknown", [null]);
  unknown.chainDetails = [];
  equal(canAddIntermediate(unknown), false);
});

test("only terminal CAs offer issuance, including when child certification is pending", () => {
  const root = ca("root", [null]);
  const branch = ca("branch", [null, null], root.id);
  const pending = ca("pending", [], root.id);
  const separate = ca("separate", [0]);
  const cas = [root, branch, pending, separate];
  deepEqual(cas.filter((candidate) => candidate.certificateChain.length && isTerminalCa(candidate, cas)).map(({ id }) => id), ["branch", "separate"]);
});
