// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

const faceEmojis = Array.from({ length: 80 }, (_, i) =>
  String.fromCodePoint(0x1f600 + i),
);

export async function transactionIdToEmojis(transactionId: string) {
  const hashedTransactionId = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(transactionId),
  );
  const hashBytes = new Uint8Array(hashedTransactionId);

  const firstEmoji = faceEmojis[hashBytes[0]! % faceEmojis.length]!;
  const secondEmoji = faceEmojis[hashBytes[1]! % faceEmojis.length]!;

  return [firstEmoji, secondEmoji];
}
