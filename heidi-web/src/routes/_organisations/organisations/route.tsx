// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_organisations/organisations")({
  loader: ({ context: { intl } }) => {
    return {
      crumb: intl.$t({
        id: "pages.organisations",
        defaultMessage: "Organisations",
      }),
    };
  },
});
