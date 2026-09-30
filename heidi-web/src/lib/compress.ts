// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import imageCompression from "browser-image-compression";

function dataURLtoFile(dataurl: string, filename: string) {
  const arr = dataurl.split(",");
  const mime = arr[0]?.match(/:(.*?);/)?.[1];
  const bstr = atob(arr[arr.length - 1]!);
  let n = bstr.length;
  const u8arr = new Uint8Array(n);
  while (n--) {
    u8arr[n] = bstr.charCodeAt(n);
  }
  return new File([u8arr], filename, { type: mime ?? "image/png" });
}

export async function compressImage(image: {
  value: string;
  preview: string;
  fileName: string;
}) {
  const inputFile = dataURLtoFile(image.value, image.fileName);
  const compressedFile = await imageCompression(inputFile, {
    maxSizeMB: 0.35,
    maxWidthOrHeight: 1024,
    useWebWorker: true,
  });
  const reader = new FileReader();
  return new Promise<string>((resolve) => {
    reader.onload = () => {
      resolve(reader.result as string);
    };
    reader.readAsDataURL(compressedFile);
  });
}
