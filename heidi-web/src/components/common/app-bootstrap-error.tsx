// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconRotate } from "@tabler/icons-react";
import type { FallbackProps } from "react-error-boundary";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

export function AppBootstrapError({ error }: FallbackProps) {
  const message = error instanceof Error ? error.message : String(error);

  return (
    <main className="grid min-h-screen place-items-center bg-background px-6 py-16 text-foreground">
      <Card className="w-full max-w-xl">
        <h1 className="text-2xl leading-none font-semibold">
          Heidi could not start
        </h1>
        <p className="mt-4 text-muted-foreground">
          The Cockpit loaded, but it could not reach the service needed to
          initialize the application. Check that the backend is running and
          try again.
        </p>
        <pre className="mt-4 overflow-auto rounded-xl bg-muted p-3 text-sm whitespace-pre-wrap">
          {message}
        </pre>
        <Button
          className="mt-6"
          type="button"
          onClick={() => window.location.reload()}
        >
          <IconRotate className="size-5" />
          Reload
        </Button>
      </Card>
    </main>
  );
}
