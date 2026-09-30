// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export async function fetchWithRedirect(
  url: string | URL | globalThis.Request,
  { redirect = "manual", ...options }: RequestInit = {},
) {
  const res = await fetch(url, { redirect, ...options });
  if (
    res.redirected ||
    res.status === 401 ||
    res.status === 403 ||
    (res.status >= 300 && res.status < 400) ||
    res.type === "opaqueredirect"
  ) {
    const location = res.headers.get("location") || "/";
    if (
      window.location.pathname !==
      new URL(location, window.location.origin).pathname
    ) {
      window.location.href = location;
    }
  }

  return res;
}

export const dateFormatter = Intl.DateTimeFormat("de-CH", {
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

export async function fileToDataURL(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = () => reject(new Error("Failed to read file"));
    reader.readAsDataURL(file);
  });
}

export function omit<T, K extends keyof T>(obj: T, keys: K[]): Omit<T, K> {
  const clone = { ...obj };
  for (const key of keys) {
    delete clone[key];
  }
  return clone;
}
