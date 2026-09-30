// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { useStore } from "@tanstack/react-form";
import { useAtomValue } from "jotai";
import { Fragment } from "react/jsx-runtime";
import { contentLanguageAtom } from "@/lib/atoms";
import { cn } from "@/lib/utils";
import { formatHandlebars } from "@/lib/utils/credential-schemas";
import { getLocalizedValue } from "@/lib/utils/localized";
import { withForm } from "@/routes/_authenticated/credential-schemas/-form/form-context";
import { TextColor } from "@/types/credential-schema";
import type { CredentialSchemaForm } from "./schemas";

export const CardPreview = withForm({
  defaultValues: {} as CredentialSchemaForm,
  render: ({ form }) => {
    const locale = useAtomValue(contentLanguageAtom);

    const attributes = useStore(form.store, (state) => state.values.attributes);
    const metaAttributes = useStore(
      form.store,
      (state) => state.values.metaAttributes,
    );
    const style = useStore(form.store, (state) => state.values.style);

    return (
      <div className="space-y-4">
        <div
          className="relative isolate aspect-cc w-full max-w-80 rounded-3xl bg-cover bg-center p-5 shadow-lg sm:min-w-80"
          style={{
            backgroundImage: `url("${style.backgroundImage}")`,
            color: style.textColor === TextColor.Dark ? "#000000" : "#ffffff",
            backgroundColor: `#${style.cardColor}`,
          }}
        >
          <p className="truncate text-xl font-semibold">
            {formatHandlebars(
              attributes ?? [],
              metaAttributes ?? [],
              style.cardTitle,
            )}
          </p>
          <p className="truncate text-sm font-medium">
            {formatHandlebars(
              attributes ?? [],
              metaAttributes ?? [],
              style.cardSubtitle,
            )}
          </p>
          <div className="absolute inset-0 z-10">
            {style.frontOverlays.map(
              ({ content, position, contentType, showLabel }) => (
                <Fragment key={content}>
                  {contentType === "Image" && (
                    <div
                      style={{
                        backgroundColor:
                          style.textColor === TextColor.Light
                            ? "#ffffff40"
                            : "#00000040",
                        color:
                          style.textColor === TextColor.Light
                            ? "#ffffff"
                            : "#000000",
                      }}
                      className={cn(
                        "absolute bottom-0 mb-[7%] h-[37%] w-[17.5%] rounded-[0.625rem]",
                        position === "BottomRight" && "right-0 mr-[7%]",
                        position === "BottomLeft" && "left-0 ml-[7%]",
                      )}
                    />
                  )}
                  {contentType === "ImageLogo" && (
                    <img
                      alt={contentType}
                      src={content}
                      className={cn(
                        "absolute max-h-1/5 max-w-[46%] object-contain",
                        position === "BottomRight" &&
                          "right-0 bottom-0 mr-[7%] mb-[7%] object-right",
                        position === "BottomLeft" &&
                          "bottom-0 left-0 mb-[7%] ml-[7%] object-left",
                        position === "TopRight" &&
                          "top-0 right-0 mt-[7%] mr-[7%] object-right",
                      )}
                    />
                  )}
                  {contentType === "ImageIcon" && (
                    <img
                      alt={contentType}
                      src={content}
                      className={cn(
                        "absolute aspect-square w-[7%] object-contain",
                        position === "BottomRight" &&
                          "right-0 bottom-0 mr-[7%] mb-[7%]",
                        position === "BottomLeft" &&
                          "bottom-0 left-0 mb-[7%] ml-[7%]",
                        position === "TopRight" &&
                          "top-0 right-0 mt-[7%] mr-[7%]",
                      )}
                    />
                  )}
                  {contentType === "Text" && (
                    <div
                      className={cn(
                        "absolute inset-x-0 bottom-0 mx-[7%] mb-[7%] text-xs",
                        position === "BottomRight" && "text-right",
                      )}
                    >
                      {showLabel && (
                        <p className="font-bold">
                          {getLocalizedValue(
                            attributes.find((a) => a.name === content)
                              ?.displayName,
                            locale,
                          ) ||
                            content}
                        </p>
                      )}
                      <p>{content}</p>
                    </div>
                  )}
                </Fragment>
              ),
            )}
          </div>
        </div>
        <img
          src={
            style.textColor === TextColor.Dark
              ? "/assets/card-overlay-light.svg"
              : "/assets/card-overlay-dark.svg"
          }
          alt="Card Overlay"
          style={{
            backgroundColor: `#${style.cardColor}`,
          }}
          className="h-8 rounded-lg shadow-md"
        />
      </div>
    );
  },
});
