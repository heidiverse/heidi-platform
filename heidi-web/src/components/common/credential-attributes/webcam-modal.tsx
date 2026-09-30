// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconCamera } from "@tabler/icons-react";
import { useIntl } from "react-intl";
import Webcam from "react-webcam";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

type Props = {
  openWebcamModal: boolean;
  setOpenWebcamModal: (open: boolean) => void;
  webcamRef: React.MutableRefObject<Webcam | null>;
  deviceId: string;
  setDeviceId: (deviceId: string) => void;
  devices: MediaDeviceInfo[];
  capture: () => void;
};

export function WebcamModal({
  openWebcamModal,
  setOpenWebcamModal,
  webcamRef,
  deviceId,
  setDeviceId,
  devices,
  capture,
}: Props) {
  const { $t } = useIntl();
  return (
    <Dialog open={openWebcamModal} onOpenChange={setOpenWebcamModal}>
      <DialogContent>
        <div className="space-y-4">
          <div className="relative">
            <Webcam
              audio={false}
              ref={webcamRef}
              screenshotFormat="image/jpeg"
              videoConstraints={{
                deviceId,
              }}
              className="aspect-4/3 w-full rounded-lg border shadow-xs"
            />
            <div className="absolute top-1/2 left-1/2 aspect-3/4 h-4/5 -translate-x-1/2 -translate-y-1/2 rounded border-2 border-dashed" />
          </div>
          <div className="flex gap-4">
            <Select value={deviceId} onValueChange={setDeviceId}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {devices.map((device, index) => (
                  <SelectItem key={device.deviceId} value={device.deviceId}>
                    {device.label ||
                      $t(
                        {
                          id: "common.camera.withIndex",
                          defaultMessage: "Camera {index}",
                        },
                        { index: index + 1 },
                      )}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button onClick={capture}>
              <IconCamera />
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
