// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

export async function readApiError(
  response: Response,
  fallback: string,
) {
  const body = await response.text();
  if (!body) return fallback;

  try {
    const parsed = JSON.parse(body) as {
      detail?: unknown;
      message?: unknown;
      title?: unknown;
    };
    for (const value of [parsed.detail, parsed.message, parsed.title]) {
      if (typeof value === "string" && value) return value;
    }
  } catch {
    // Return plain-text responses unchanged.
  }

  return body;
}
