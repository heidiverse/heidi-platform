// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useCallback, useEffect, useRef, useState } from "react";
import type Webcam from "react-webcam";

export type WebcamImage = {
  fileName: string;
  preview: string;
};

export function useWebcamSelector(initialImage: WebcamImage | null = null) {
  const [openWebcamModal, setOpenWebcamModal] = useState(false);
  const [deviceId, setDeviceId] = useState<string>("");
  const [devices, setDevices] = useState<MediaDeviceInfo[]>([]);
  const webcamRef = useRef<Webcam>(null);
  const [image, setImage] = useState<WebcamImage | null>(initialImage);

  const capture = useCallback(() => {
    if (webcamRef.current) {
      const imageSrc = webcamRef.current.getScreenshot();
      if (imageSrc) {
        setImage({ fileName: "webcam", preview: imageSrc });
      }
    }
    setOpenWebcamModal(false);
  }, [webcamRef]);

  const handleDevices = useCallback(
    async (mediaDevices: MediaDeviceInfo[]) => {
      if (!openWebcamModal) return;

      await navigator.mediaDevices.getUserMedia({ video: true });
      const newDevices = mediaDevices.filter(
        ({ kind }) => kind === "videoinput",
      );
      if (newDevices.length > 0) {
        setDevices(newDevices);
        setDeviceId(newDevices[0]!.deviceId);
      }
    },
    [setDevices, openWebcamModal],
  );

  function getDevices() {
    return navigator.mediaDevices.enumerateDevices();
  }

  useEffect(() => {
    if (!openWebcamModal) return;
    navigator.mediaDevices
      .getUserMedia({ video: true })
      .then(getDevices)
      .then(handleDevices);
  }, [handleDevices, openWebcamModal]);

  return {
    openWebcamModal,
    setOpenWebcamModal,
    deviceId,
    setDeviceId,
    devices,
    webcamRef,
    capture,
    image,
    setImage,
  };
}
