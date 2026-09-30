// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconLoader2,
  IconPhoto,
  IconPhotoOff,
  IconTrash,
} from "@tabler/icons-react";
import imageCompression, { type Options } from "browser-image-compression";
import {
  type ChangeEvent,
  forwardRef,
  type HTMLAttributes,
  useState,
} from "react";
import { FormattedMessage } from "react-intl";
import { Button } from "@/components/ui/button";

export const BgImageInput = forwardRef<
  HTMLInputElement,
  {
    value: string | null;
    onValueChange: (base64: string | null) => void;
  } & HTMLAttributes<HTMLInputElement>
>(({ value, onValueChange, ...inputProps }, ref) => {
  const [loading, setLoading] = useState(false);

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    try {
      setLoading(true);
      const file = event.target.files?.[0];
      if (file) {
        const options: Options = {
          maxSizeMB: 0.2,
          maxWidthOrHeight: 720,
        };
        const compressedFile = await imageCompression(file, options);
        const reader = new FileReader();
        reader.onload = () => {
          const base64 = reader.result as string;
          onValueChange(base64);
        };
        reader.readAsDataURL(compressedFile);
      }
    } finally {
      setLoading(false);
      event.target.value = "";
    }
  }

  return (
    <div className="flex min-w-0 items-center gap-2">
      <input
        ref={ref}
        type="file"
        accept=".jpg, .jpeg, .png"
        onChange={handleFileChange}
        className="peer sr-only"
        {...inputProps}
      />
      <label
        htmlFor={inputProps.id}
        className="flex h-10 w-full min-w-0 cursor-pointer items-center gap-2 rounded-full border bg-input px-3 py-2 text-sm outline-2 outline-offset-2 outline-transparent peer-focus-visible:outline-ring peer-disabled:cursor-not-allowed peer-disabled:opacity-50 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground"
      >
        {loading ? (
          <IconLoader2 className="size-5 shrink-0 animate-spin opacity-40" />
        ) : value ? (
          <IconPhoto className="size-5 shrink-0" />
        ) : (
          <IconPhotoOff className="size-5 shrink-0" />
        )}
        <span className="truncate">
          {loading ? (
            <FormattedMessage
              id="bgImageInput.compressing"
              defaultMessage="Compressing..."
            />
          ) : value ? (
            <FormattedMessage
              id="bgImageInput.change"
              defaultMessage="Change image"
            />
          ) : (
            <FormattedMessage
              id="bgImageInput.upload"
              defaultMessage="Upload image"
            />
          )}
        </span>
      </label>
      {value && (
        <Button
          type="button"
          variant="ghost"
          className="px-2"
          onClick={() => onValueChange(null)}
        >
          <span className="flex items-center gap-2 text-destructive">
            <IconTrash />
            <FormattedMessage id="common.remove" defaultMessage="Remove" />
          </span>
        </Button>
      )}
    </div>
  );
});
