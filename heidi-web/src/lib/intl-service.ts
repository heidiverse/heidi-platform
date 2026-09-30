// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

// This file is needed, to use translated strings outside of the react context (eg. api files)
import type { IntlShape } from "react-intl";

let intl: IntlShape;

export function setIntl(intlInstance: IntlShape) {
  intl = intlInstance;
}

export function getIntl() {
  return intl;
}

export function translate(id: string, values = {}) {
  const intl = getIntl();
  return intl ? intl.formatMessage({ id }, values) : id;
}
