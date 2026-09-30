// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

export function argbToHex(argb: number | undefined = 4278190080) {
  const hexa = argb.toString(16);
  const hex = hexa.length === 8 ? hexa.substring(2) : hexa;
  const alpha = hexa.length === 8 ? hexa.substring(0, 2) : "ff";
  return `${hex}${alpha}`.replace(/ff$/, "");
}

function fullLengthHex(hex: string) {
  switch (hex.length) {
    case 3:
      return `ff${hex
        .split("")
        .map((x) => x + x)
        .join("")}`;
    case 4:
      return (
        hex[3]! +
        hex[3]! +
        hex
          .substring(0, 3)
          .split("")
          .map((x) => x + x)
          .join("")
      );
    case 6:
      return `ff${hex}`;
    case 8:
      return hex.substring(6) + hex.substring(0, 6);
    default:
      throw new Error("Invalid color code.");
  }
}

export function hexToArgb(hex: string) {
  const sliced = hex.replace(/^#/, "");
  const hexa = fullLengthHex(sliced);
  if (/[0-9A-Fa-f]{8}/g.test(hexa)) {
    return Number.parseInt(hexa, 16);
  }
  throw new Error("Invalid color code.");
}
