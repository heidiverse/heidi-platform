// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useAtomValue } from "jotai";
import type { ReactNode } from "react";
import { IntlProvider } from "react-intl";
import { localeAtom } from "@/lib/atoms";
import type { Locale } from "@/lib/constants";
import { getExtensionMessageLoaders } from "@/lib/extensions";
import englishMessages from "../../translations/en/translations.json";

const messagesCache: Partial<Record<Locale, Record<string, string>>> = {};

function getMessages(locale: Locale) {
  if (messagesCache[locale]) {
    return messagesCache[locale];
  }
  // throwing a promise suspends react app
  throw loadMessages(locale);
}

async function loadMessages(locale: Locale) {
  const contributed = await Promise.all(
    getExtensionMessageLoaders(locale).map((load) => load()),
  );
  // Extensions are merged last so they can also reword built-in messages.
  messagesCache[locale] = Object.assign({}, englishMessages, ...contributed);
}

export function AsyncIntlProvider({ children }: { children: ReactNode }) {
  const locale = useAtomValue(localeAtom);
  const messages = getMessages(locale);

  return (
    <IntlProvider onError={console.warn} locale={locale} messages={messages}>
      {children}
    </IntlProvider>
  );
}
