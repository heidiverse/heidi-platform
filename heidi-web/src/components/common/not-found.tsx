// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { Link } from "@tanstack/react-router";
import { FormattedMessage } from "react-intl";

export function NotFoundPage() {
  return (
    <div className="grid h-screen place-items-center">
      <div className="flex flex-col items-center gap-4 sm:gap-8">
        <h1 className="flex items-center gap-3 text-6xl font-extrabold sm:text-8xl">
          <span className="text-5xl sm:text-7xl">✨</span>404
          <span className="text-5xl sm:text-7xl">✨</span>
        </h1>
        <p>
          <FormattedMessage
            id="error.404"
            defaultMessage="This page could not be found."
          />{" "}
          <Link className="text-muted-foreground underline" to="/">
            <FormattedMessage
              id="error.404.link"
              defaultMessage="Go back home?"
            />
          </Link>
        </p>
      </div>
    </div>
  );
}
