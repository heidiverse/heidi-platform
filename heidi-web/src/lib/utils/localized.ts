// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

export function getLocalizedValue(
  values: Record<string, string> | null | undefined,
  fallbackLanguage: string,
): string | undefined {
  if (!values) return undefined;

  const fallback = fallbackLanguage.trim();
  const baseLanguage = fallback.split("-").at(0) ?? fallback;
  const candidates = [fallback, baseLanguage, ...Object.keys(values)];

  for (const language of candidates) {
    const value = values[language]?.trim();
    if (value) return value;
  }

  return undefined;
}

export function isLanguageTag(value: string): boolean {
  try {
    return Boolean(value.trim()) && !value.includes("_") && Boolean(new Intl.Locale(value).language);
  } catch {
    return false;
  }
}
