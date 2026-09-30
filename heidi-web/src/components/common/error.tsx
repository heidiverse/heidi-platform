// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconRefresh, IconRotate } from "@tabler/icons-react";
import { useQueryErrorResetBoundary } from "@tanstack/react-query";
import { type ErrorComponentProps, useRouter } from "@tanstack/react-router";
import { useEffect } from "react";
import { FormattedMessage } from "react-intl";
import { PageHeader } from "@/components/common/page-header";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

export function DefaultErrorComponent({ error }: ErrorComponentProps) {
  const router = useRouter();
  const queryErrorResetBoundary = useQueryErrorResetBoundary();

  useEffect(() => {
    queryErrorResetBoundary.reset();
  }, [queryErrorResetBoundary]);

  return (
    <>
      <PageHeader heading="Oh no, something went wrong!" />
      <Card className="flex flex-col gap-3">
        <p>{error.message}</p>
        {error.stack && (
          <small className="text-muted-foreground">{error.stack}</small>
        )}
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            onClick={() => {
              router.invalidate();
            }}
          >
            <IconRefresh className="size-5" />
            <FormattedMessage id="error.retry" defaultMessage="Retry" />
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              window.location.reload();
            }}
          >
            <IconRotate className="size-5" />
            <FormattedMessage id="error.reload" defaultMessage="Reload" />
          </Button>
        </div>
      </Card>
    </>
  );
}
