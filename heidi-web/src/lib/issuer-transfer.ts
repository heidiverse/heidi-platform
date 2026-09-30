// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

export type IssuerTransfer = {
  schemaId: string;
  clientInteractionToken: string;
};

export type IssuerTransferHistoryState = {
  issuerTransfer?: IssuerTransfer;
};

declare module "@tanstack/react-router" {
  interface HistoryState extends IssuerTransferHistoryState {}
}

export function transferForSchema(
  state: unknown,
  schemaId: string,
): IssuerTransfer | null {
  if (!isRecord(state) || !isRecord(state.issuerTransfer)) {
    return null;
  }

  const transfer = state.issuerTransfer;
  if (
    transfer.schemaId !== schemaId ||
    typeof transfer.clientInteractionToken !== "string" ||
    transfer.clientInteractionToken.length === 0
  ) {
    return null;
  }

  return {
    schemaId: transfer.schemaId,
    clientInteractionToken: transfer.clientInteractionToken,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
