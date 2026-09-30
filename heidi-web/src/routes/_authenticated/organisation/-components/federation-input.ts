// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

/** Authority hints entered one per line, trimmed and without duplicates. */
export function authorityHintsFromText(value: string) {
  const hints = value
    .split(/\r?\n/)
    .map((hint) => hint.trim())
    .filter((hint) => hint.length > 0);
  return [...new Set(hints)];
}

/** Adds or removes one entry, keeping the order of the others. */
export function toggled<T>(values: T[], value: T, include: boolean) {
  const without = values.filter((entry) => entry !== value);
  return include ? [...without, value] : without;
}
