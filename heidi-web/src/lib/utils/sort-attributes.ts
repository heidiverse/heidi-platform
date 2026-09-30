// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

export function sortAttributes<T extends { name: string }>(
  attributes: readonly T[],
  orderedProperties: readonly string[],
): T[] {
  return [...attributes].sort(
    (a, b) =>
      orderedProperties.indexOf(a.name) - orderedProperties.indexOf(b.name),
  );
}
