// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { atom, createStore } from "jotai";
import { atomWithStorage } from "jotai/utils";
import { DEFAULT_LOCALE } from "@/lib/constants";

export const jotaiStore = createStore();

export const selectedTenantAtom = atomWithStorage<string | null>(
  "selected-tenant",
  null,
  undefined,
  { getOnInit: true },
);

export const localeAtom = atom(DEFAULT_LOCALE);

export const contentLanguageAtom = atom<string>(DEFAULT_LOCALE);
