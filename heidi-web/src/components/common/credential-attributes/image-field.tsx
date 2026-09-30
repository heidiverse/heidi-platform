// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconCamera,
  IconUpload,
  IconUserSquare,
  IconX,
} from "@tabler/icons-react";
import { useRef, useState } from "react";
import AvatarEditor, { type AvatarEditorRef } from "react-avatar-editor";
import { WebcamModal } from "@/components/common/credential-attributes/webcam-modal";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Slider } from "@/components/ui/slider";
import { compressImage } from "@/lib/compress";
import { useDropzone } from "@/lib/hooks";
import {
  useWebcamSelector,
  type WebcamImage,
} from "@/lib/hooks/use-webcam-selector";

export function ImageField({
  defaultValue,
  onValueChange,
}: {
  defaultValue?: string;
  onValueChange: (value: string) => void;
}) {
  const editorRef = useRef<AvatarEditorRef>(null);
  const [imageScale, setImageScale] = useState(1);
  const skipInitialImageChangeRef = useRef(Boolean(defaultValue));
  const initialImage = useRef<WebcamImage | null>(
    defaultValue
      ? {
          fileName: "default-value",
          preview: defaultValue,
        }
      : null,
  ).current;

  const {
    openWebcamModal,
    setOpenWebcamModal,
    deviceId,
    setDeviceId,
    devices,
    webcamRef,
    capture,
    image,
    setImage: setImageInternal,
  } = useWebcamSelector(initialImage);

  function setImage(image: WebcamImage | null) {
    setImageInternal(image);
    onValueChange(image?.preview ?? "");
  }

  const {
    getRootProps,
    getInputProps,
    open: openDropzone,
  } = useDropzone(setImage);

  function clearImage() {
    setImage(null);
    setImageScale(1);
    skipInitialImageChangeRef.current = false;
  }

  async function emitEditedImage() {
    const croppedImage = editorRef.current?.getImage();
    if (!croppedImage) {
      return;
    }

    if (skipInitialImageChangeRef.current) {
      skipInitialImageChangeRef.current = false;
      return;
    }

    const croppedImageDataUrl = croppedImage.toDataURL("image/png");
    const nextValue = await compressImage({
      fileName: image?.fileName ?? "image-field-value.png",
      preview: croppedImageDataUrl,
      value: croppedImageDataUrl,
    });
    onValueChange(nextValue);
  }

  return (
    <Card>
      <div className="mx-auto w-max space-y-4 pt-2 pb-6">
        <div
          {...getRootProps({
            className:
              "border border-border rounded-2xl shadow-xs cursor-pointer",
            onClick: image ? undefined : openDropzone,
          })}
        >
          <input {...getInputProps()} />
          <div className="relative aspect-3/4 w-40">
            {image ? (
              <>
                <div className="overflow-clip rounded-2xl">
                  <AvatarEditor
                    ref={editorRef}
                    image={image.preview}
                    width={150}
                    height={203.333}
                    border={5}
                    color={[255, 255, 255, 0.6]}
                    scale={imageScale}
                    rotate={0}
                    onImageChange={emitEditedImage}
                  />
                </div>
                <Button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    clearImage();
                  }}
                  className="absolute -top-2 -right-2 rounded-lg bg-black/20 p-1.5 text-white/80 backdrop-blur-xs hover:text-white"
                >
                  <IconX />
                </Button>
              </>
            ) : (
              <div className="flex h-full flex-col">
                <div className="flex grow items-center justify-center">
                  <IconUserSquare className="size-12 opacity-20" />
                </div>
                <div className="grid grid-cols-2 gap-2 p-2 pt-0">
                  <Button variant="secondary" color="gray" type="button">
                    <IconUpload />
                  </Button>
                  <Button
                    variant="secondary"
                    color="gray"
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      setOpenWebcamModal(true);
                    }}
                  >
                    <IconCamera />
                  </Button>
                </div>
              </div>
            )}
          </div>
        </div>
        <div className="px-2.5">
          <Slider
            min={1}
            max={3}
            step={0.001}
            value={[imageScale]}
            onValueChange={(e) => setImageScale(e[0] ?? 1)}
            disabled={!image}
          />
        </div>
      </div>
      <WebcamModal
        openWebcamModal={openWebcamModal}
        setOpenWebcamModal={setOpenWebcamModal}
        webcamRef={webcamRef}
        deviceId={deviceId}
        setDeviceId={setDeviceId}
        devices={devices}
        capture={capture}
      />
    </Card>
  );
}
