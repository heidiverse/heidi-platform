// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

export function getLocalizedValue(
  values: Record<string, string> | null | undefined,
  fallbackLanguage: string,
  preferredLanguages: readonly string[] = [],
): string | undefined {
  if (!values) return undefined;

  for (const language of [...preferredLanguages, fallbackLanguage]) {
    const value = valueForLanguage(values, language);
    if (value) return value;
  }

  return Object.values(values).find((value) => value?.trim())?.trim();
}

/** Browser language preferences, ordered from most to least preferred. */
export function getBrowserLanguages(): string[] {
  if (typeof navigator === "undefined") return [];

  const languages = navigator.languages?.filter(Boolean) ?? [];
  if (languages.length > 0) return [...languages];
  return navigator.language ? [navigator.language] : [];
}

function valueForLanguage(
  values: Record<string, string>,
  language: string,
): string | undefined {
  const requested = localeTag(language);
  if (!requested) return undefined;

  const available = Object.entries(values)
    .filter(([, value]) => typeof value === "string" && value.trim())
    .map(([key, value]) => ({
      value: value.trim(),
      locale: localeTag(key),
    }))
    .filter(
      (entry): entry is { value: string; locale: LocaleTag } =>
        entry.locale !== undefined,
    );

  const sameLanguage = available.filter(
    (entry) => entry.locale.language === requested.language,
  );
  const match =
    sameLanguage.find((entry) => entry.locale.tag === requested.tag) ??
    sameLanguage.find((entry) => entry.locale.tag === requested.language) ??
    sameLanguage.sort((left, right) =>
      left.locale.tag < right.locale.tag
        ? -1
        : left.locale.tag > right.locale.tag
          ? 1
          : 0,
    )[0];

  return match?.value;
}

function localeTag(value: string): LocaleTag | undefined {
  const languageTag = value.trim();
  if (!languageTag || languageTag.includes("_")) return undefined;

  try {
    const locale = new Intl.Locale(languageTag);
    return { tag: locale.baseName, language: locale.language };
  } catch {
    return undefined;
  }
}

type LocaleTag = { tag: string; language: string };

export function isLanguageTag(value: string): boolean {
  return localeTag(value) !== undefined;
}
