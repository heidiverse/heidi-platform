// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

export type PrivateKeyFormat = "JWK" | "JWKS" | "PEM" | "PKCS12";

export const privateKeyFormats: ReadonlyArray<{
  value: PrivateKeyFormat;
  label: string;
}> = [
  { value: "PKCS12", label: "PKCS#12 file" },
  { value: "PEM", label: "Private PEM text" },
  { value: "JWK", label: "Private JWK text" },
  { value: "JWKS", label: "Private JWK Set text" },
];

export function fileAsBase64(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error("Could not read PKCS#12 file"));
    reader.onload = () => {
      const value = String(reader.result);
      resolve(value.slice(value.indexOf(",") + 1));
    };
    reader.readAsDataURL(file);
  });
}
