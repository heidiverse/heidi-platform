// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useDropzone as useReactDropzone } from "react-dropzone";

export function useDropzone(
  onValueChange: (value: { fileName: string; preview: string } | null) => void,
) {
  return useReactDropzone({
    accept: {
      "image/*": [],
    },
    maxFiles: 1,
    multiple: false,
    noClick: true,
    maxSize: 5242880,
    onDrop: async (acceptedFiles) => {
      if (acceptedFiles.length === 0) {
        onValueChange(null);
      } else {
        const reader = new FileReader();
        reader.onload = () => {
          const base64 = reader.result as string;
          onValueChange({
            fileName: acceptedFiles[0]!.name,
            preview: base64,
          });
        };
        reader.readAsDataURL(acceptedFiles[0]!);
      }
    },
  });
}
