// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { createFileRoute } from "@tanstack/react-router";

export const Route = createFileRoute("/_authenticated/status-lists")({
  loader: ({ context: { intl } }) => ({
    crumb: intl.$t({
      id: "pages.statusLists",
      defaultMessage: "Status Lists",
    }),
  }),
});
