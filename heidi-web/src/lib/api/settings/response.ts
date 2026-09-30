// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

export async function parseSettingsResponse<T>(
  response: Response,
  errorMessage: string,
): Promise<T> {
  if (!response.ok) throw new Error(errorMessage);
  return (await response.json()) as T;
}
