// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { IconImageInPicture, IconTrash } from "@tabler/icons-react";
import { useField, useStore } from "@tanstack/react-form";
import { getColorSync } from "colorthief";
import { useAtomValue } from "jotai";
import { FormattedMessage, useIntl } from "react-intl";
import { Button } from "@/components/ui/button";
import {
  Field,
  FieldDescription,
  FieldError,
  FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { contentLanguageAtom } from "@/lib/atoms";
import { getLocalizedValue } from "@/lib/utils/localized";
import { withForm } from "@/routes/_authenticated/credential-schemas/-form/form-context";
import {
  AllowedOverlayContentTypesForPosition,
  AttributeType,
  CredentialSchemaState,
  type FrontOverlayContentTypes,
  FrontOverlayPositions,
  OcaVersion,
  type TextColor,
} from "@/types/credential-schema";
import { BgImageInput } from "./bg-image-input";
import { CardColorInput } from "./card-color-input";
import type { CredentialSchemaForm } from "./schemas";

export const CardStyleCard = withForm({
  defaultValues: {} as CredentialSchemaForm,
  render: ({ form }) => {
    const { formatMessage } = useIntl();
    const published = useStore(
      form.store,
      (state) => state.values.state === CredentialSchemaState.Published,
    );
    const { pushValue } = useField({
      form,
      name: "style.frontOverlays",
    });
    const frontOverlays = useStore(
      form.store,
      (state) => state.values.style.frontOverlays,
    );
    const attributes = useStore(form.store, (state) => state.values.attributes);

    const locale = useAtomValue(contentLanguageAtom);

    const activeFrontOverlayPositions = frontOverlays.map((o) => o.position);

    return (
      <>
        <div className="flex flex-col gap-y-3 rounded-2xl border bg-background p-4 sm:p-6">
          <form.AppField name="style.cardTitle">
            {(field) => {
              const isInvalid =
                field.state.meta.isTouched && !field.state.meta.isValid;
              return (
                <Field data-invalid={isInvalid}>
                  <FieldLabel htmlFor={field.name}>
                    <FormattedMessage
                      id="credentialSchema.style.cardTitle"
                      defaultMessage="Card Title"
                    />
                  </FieldLabel>
                  {!published && (
                    <FieldDescription>
                      <FormattedMessage
                        id="credentialSchema.style.attributeReference.description"
                        defaultMessage="You can reference an attribute using {syntax} syntax."
                        values={{ syntax: <code>{"{{attributeName}}"}</code> }}
                      />
                    </FieldDescription>
                  )}
                  {published ? (
                    <div className="-mt-2 text-sm">{field.state.value}</div>
                  ) : (
                    <Input
                      id={field.name}
                      name={field.name}
                      value={field.state.value}
                      onChange={(e) => field.handleChange(e.target.value)}
                      onBlur={field.handleBlur}
                      aria-invalid={isInvalid}
                    />
                  )}
                  {isInvalid && <FieldError errors={field.state.meta.errors} />}
                </Field>
              );
            }}
          </form.AppField>

          <form.AppField name="style.cardSubtitle">
            {(field) => {
              const isInvalid =
                field.state.meta.isTouched && !field.state.meta.isValid;
              return (
                <Field data-invalid={isInvalid}>
                  <FieldLabel htmlFor={field.name}>
                    <FormattedMessage
                      id="credentialSchema.style.cardSubtitle"
                      defaultMessage="Card Subtitle"
                    />
                  </FieldLabel>
                  {!published && (
                    <FieldDescription>
                      <FormattedMessage
                        id="credentialSchema.style.attributeReference.description"
                        defaultMessage="You can reference an attribute using {syntax} syntax."
                        values={{ syntax: <code>{"{{attributeName}}"}</code> }}
                      />
                    </FieldDescription>
                  )}
                  {published ? (
                    <div className="-mt-2 text-sm">{field.state.value}</div>
                  ) : (
                    <Input
                      id={field.name}
                      name={field.name}
                      value={field.state.value}
                      onChange={(e) => field.handleChange(e.target.value)}
                      onBlur={field.handleBlur}
                      aria-invalid={isInvalid}
                    />
                  )}
                  {isInvalid && <FieldError errors={field.state.meta.errors} />}
                </Field>
              );
            }}
          </form.AppField>

          <form.AppField name="style.textColor">
            {(field) => {
              const isInvalid =
                field.state.meta.isTouched && !field.state.meta.isValid;
              return (
                <Field data-invalid={isInvalid}>
                  <FieldLabel htmlFor={field.name}>
                    <FormattedMessage
                      id="credentialSchema.style.textColor"
                      defaultMessage="Text Color"
                    />
                  </FieldLabel>
                  {published ? (
                    <div className="-mt-2 text-sm">{field.state.value}</div>
                  ) : (
                    <Select
                      value={field.state.value}
                      onValueChange={(v) => field.handleChange(v as TextColor)}
                    >
                      <SelectTrigger id={field.name} aria-invalid={isInvalid}>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="light">
                          <FormattedMessage
                            id="common.light"
                            defaultMessage="Light"
                          />
                        </SelectItem>
                        <SelectItem value="dark">
                          <FormattedMessage
                            id="common.dark"
                            defaultMessage="Dark"
                          />
                        </SelectItem>
                      </SelectContent>
                    </Select>
                  )}
                  {isInvalid && <FieldError errors={field.state.meta.errors} />}
                </Field>
              );
            }}
          </form.AppField>

          <form.AppField name="style.ocaVersion">
            {(field) => (
              <Field>
                <FieldLabel htmlFor={field.name}>
                  <FormattedMessage
                    id="credentialSchema.style.ocaVersion"
                    defaultMessage="OCA Version"
                  />
                </FieldLabel>
                {published ? (
                  <div className="-mt-2 text-sm">{field.state.value}</div>
                ) : (
                  <Select
                    value={field.state.value}
                    onValueChange={(value) =>
                      field.handleChange(value as OcaVersion)
                    }
                  >
                    <SelectTrigger id={field.name}>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={OcaVersion.Legacy}>
                        <FormattedMessage
                          id="credentialSchema.style.ocaVersion.legacy"
                          defaultMessage="Legacy"
                        />
                      </SelectItem>
                      <SelectItem value={OcaVersion.Swiyu}>
                        <FormattedMessage
                          id="credentialSchema.style.ocaVersion.swiyu"
                          defaultMessage="swiyu"
                        />
                      </SelectItem>
                    </SelectContent>
                  </Select>
                )}
              </Field>
            )}
          </form.AppField>

          <form.AppField name="style.cardColor">
            {(field) => {
              const isInvalid =
                field.state.meta.isTouched && !field.state.meta.isValid;
              return (
                <Field data-invalid={isInvalid}>
                  <FieldLabel htmlFor={field.name}>
                    <FormattedMessage
                      id="credentialSchema.style.cardColor"
                      defaultMessage="Card Color"
                    />
                  </FieldLabel>
                  {published ? (
                    <div className="-mt-2 flex items-center gap-2 text-sm">
                      <div
                        className="size-6 rounded-lg border"
                        style={{ backgroundColor: `#${field.state.value}` }}
                      />
                      #{field.state.value.toUpperCase()}
                    </div>
                  ) : (
                    <CardColorInput
                      id={field.name}
                      value={field.state.value}
                      onValueChange={field.handleChange}
                      onBlur={field.handleBlur}
                      aria-invalid={isInvalid}
                    />
                  )}
                  {isInvalid && <FieldError errors={field.state.meta.errors} />}
                </Field>
              );
            }}
          </form.AppField>
          <hr className="my-2 max-sm:mt-4" />
          <form.AppField name="style.backgroundImage">
            {(field) => {
              const isInvalid =
                field.state.meta.isTouched && !field.state.meta.isValid;
              return (
                <Field data-invalid={isInvalid}>
                  <FieldLabel htmlFor={field.name}>
                    <FormattedMessage
                      id="credentialSchema.style.backgroundImage"
                      defaultMessage="Background Image"
                    />
                  </FieldLabel>
                  {published ? (
                    <div className="-mt-2">
                      {field.state.value ? (
                        <img
                          src={field.state.value}
                          alt="VC Background"
                          className="aspect-cc h-full max-h-36 rounded-2xl object-cover shadow"
                        />
                      ) : (
                        <div className="truncate text-sm">
                          <FormattedMessage
                            id="credentialSchema.style.noBackgroundImage"
                            defaultMessage="No Background Image set"
                          />
                        </div>
                      )}
                    </div>
                  ) : (
                    <BgImageInput
                      id={field.name}
                      value={field.state.value ?? ""}
                      onValueChange={(image) => {
                        field.handleChange(image);
                        if (image) {
                          const img = new Image();
                          img.src = image;
                          img.onload = () => {
                            const color = getColorSync(img);
                            if (!color) {
                              return;
                            }
                            form.setFieldValue(
                              "style.textColor",
                              color.isDark ? "light" : "dark",
                            );
                            form.setFieldValue(
                              "style.cardColor",
                              color.hex().slice(1),
                            );
                          };
                        }
                      }}
                      onBlur={field.handleBlur}
                      aria-invalid={isInvalid}
                    />
                  )}
                  {isInvalid && <FieldError errors={field.state.meta.errors} />}
                </Field>
              );
            }}
          </form.AppField>
        </div>

        <div className="mt-6 flex items-center justify-between gap-4">
          <h3 className="text-xl">
            <FormattedMessage
              id="credentialSchema.style.frontOverlays"
              defaultMessage="Front Overlays"
            />
          </h3>
          {!published && (
            <Button
              type="button"
              onClick={() => {
                pushValue({
                  content: "",
                  contentType: "" as FrontOverlayContentTypes,
                  position: "" as FrontOverlayPositions,
                });
              }}
              variant="outline"
              className="self-end"
            >
              <IconImageInPicture />
              <FormattedMessage
                id="credentialSchema.style.frontOverlays.addNew"
                defaultMessage="Add New"
              />
            </Button>
          )}
        </div>
        <form.AppField name="style.frontOverlays" mode="array">
          {(field) => {
            if (field.state.value.length === 0) {
              return null;
            }
            return (
              <div className="flex flex-col gap-2">
                {field.state.value.map((frontOverlay, i) => {
                  return (
                    <div
                      className="relative flex flex-col gap-y-3 rounded-2xl border bg-background p-4"
                      key={`${frontOverlay.content}-${i.toString()}`}
                    >
                      {!published && (
                        <Button
                          variant="tertiary"
                          size="icon"
                          className="absolute top-0 right-0 size-6 translate-x-1/3 -translate-y-1/3"
                          onClick={() => {
                            field.removeValue(i);
                          }}
                        >
                          <IconTrash className="size-4!" />
                        </Button>
                      )}

                      <form.AppField
                        name={`style.frontOverlays[${i}].position`}
                      >
                        {(subField) => {
                          const isInvalid =
                            subField.state.meta.isTouched &&
                            !subField.state.meta.isValid;
                          return (
                            <Field data-invalid={isInvalid}>
                              <FieldLabel htmlFor={subField.name}>
                                <FormattedMessage
                                  id="credentialSchema.style.position"
                                  defaultMessage="Position"
                                />
                              </FieldLabel>
                              {published ? (
                                <div className="-mt-2 text-sm">
                                  {subField.state.value}
                                </div>
                              ) : (
                                <Select
                                  value={subField.state.value}
                                  onValueChange={(
                                    value: FrontOverlayPositions,
                                  ) => {
                                    subField.handleChange(value);
                                    if (
                                      !AllowedOverlayContentTypesForPosition[
                                        value
                                      ].includes(frontOverlay.contentType)
                                    ) {
                                      form.setFieldValue(
                                        `style.frontOverlays[${i}].contentType`,
                                        "" as FrontOverlayContentTypes,
                                      );
                                    }
                                  }}
                                >
                                  <SelectTrigger
                                    id={subField.name}
                                    aria-invalid={isInvalid}
                                  >
                                    <SelectValue
                                      placeholder={formatMessage({
                                        id: "credentialSchema.style.position.placeholder",
                                        defaultMessage: "Select Position",
                                      })}
                                    />
                                  </SelectTrigger>
                                  <SelectContent>
                                    {Object.entries(FrontOverlayPositions).map(
                                      ([key, value]) => (
                                        <SelectItem
                                          value={value}
                                          key={key}
                                          disabled={activeFrontOverlayPositions.includes(
                                            value,
                                          )}
                                        >
                                          {value}
                                        </SelectItem>
                                      ),
                                    )}
                                  </SelectContent>
                                </Select>
                              )}
                              {!subField.state.meta.isValid && (
                                <FieldError
                                  errors={subField.state.meta.errors}
                                />
                              )}
                            </Field>
                          );
                        }}
                      </form.AppField>

                      <form.AppField
                        name={`style.frontOverlays[${i}].contentType`}
                      >
                        {(subField) => {
                          const isInvalid =
                            subField.state.meta.isTouched &&
                            !subField.state.meta.isValid;
                          return (
                            <Field data-invalid={isInvalid}>
                              <FieldLabel htmlFor={subField.name}>
                                <FormattedMessage
                                  id="credentialSchema.style.contentType"
                                  defaultMessage="Content Type"
                                />
                              </FieldLabel>
                              {published ? (
                                <div className="-mt-2 text-sm">
                                  {subField.state.value}
                                </div>
                              ) : (
                                <Select
                                  value={subField.state.value}
                                  onValueChange={(
                                    value: FrontOverlayContentTypes,
                                  ) => {
                                    subField.handleChange(value);
                                    form.setFieldValue(
                                      `style.frontOverlays[${i}].content`,
                                      "",
                                    );
                                  }}
                                  disabled={!frontOverlay.position}
                                >
                                  <SelectTrigger
                                    id={subField.name}
                                    aria-invalid={isInvalid}
                                  >
                                    <SelectValue
                                      placeholder={
                                        frontOverlay.position
                                          ? formatMessage({
                                              id: "credentialSchema.style.contentType.placeholder",
                                              defaultMessage:
                                                "Select Content Type",
                                            })
                                          : formatMessage({
                                              id: "credentialSchema.style.contentType.placeholder.positionFirst",
                                              defaultMessage:
                                                "Select Position First",
                                            })
                                      }
                                    />
                                  </SelectTrigger>
                                  <SelectContent>
                                    {AllowedOverlayContentTypesForPosition[
                                      frontOverlay.position
                                    ]?.map((value) => (
                                      <SelectItem value={value} key={value}>
                                        {value}
                                      </SelectItem>
                                    ))}
                                  </SelectContent>
                                </Select>
                              )}
                              {!subField.state.meta.isValid && (
                                <FieldError
                                  errors={subField.state.meta.errors}
                                />
                              )}
                            </Field>
                          );
                        }}
                      </form.AppField>

                      <form.AppField name={`style.frontOverlays[${i}].content`}>
                        {(subField) => {
                          const isInvalid =
                            subField.state.meta.isTouched &&
                            !subField.state.meta.isValid;
                          return (
                            <Field data-invalid={isInvalid}>
                              <FieldLabel htmlFor={subField.name}>
                                <FormattedMessage
                                  id="credentialSchema.style.content"
                                  defaultMessage="Content"
                                />
                              </FieldLabel>
                              {published ? (
                                <div className="-mt-2 text-sm">
                                  {frontOverlay.contentType === "ImageLogo" ||
                                  frontOverlay.contentType === "ImageIcon" ? (
                                    <img
                                      src={subField.state.value}
                                      alt=""
                                      className="max-h-36"
                                    />
                                  ) : (
                                    subField.state.value
                                  )}
                                </div>
                              ) : frontOverlay.contentType === "ImageLogo" ||
                                frontOverlay.contentType === "ImageIcon" ? (
                                <BgImageInput
                                  id={subField.name}
                                  onValueChange={(value) => {
                                    subField.handleChange(value ?? "");
                                  }}
                                  value={subField.state.value}
                                />
                              ) : (
                                <Select
                                  value={subField.state.value}
                                  onValueChange={subField.handleChange}
                                  disabled={
                                    !frontOverlay.position &&
                                    !frontOverlay.contentType
                                  }
                                >
                                  <SelectTrigger
                                    id={subField.name}
                                    aria-invalid={isInvalid}
                                  >
                                    <SelectValue
                                      placeholder={
                                        !frontOverlay.position ||
                                        !frontOverlay.contentType
                                          ? formatMessage({
                                              id: "credentialSchema.style.content.placeholder.selectFirst",
                                              defaultMessage:
                                                "Select Position and Content Type First",
                                            })
                                          : formatMessage({
                                              id: "credentialSchema.style.content.placeholder.referenceAttribute",
                                              defaultMessage:
                                                "Reference an attribute here",
                                            })
                                      }
                                    />
                                  </SelectTrigger>
                                  <SelectContent>
                                    {attributes.map((attribute) => (
                                      <SelectItem
                                        key={attribute.id}
                                        value={attribute.name}
                                        disabled={
                                          (frontOverlay.contentType ===
                                            "Image" &&
                                            attribute.type !==
                                              AttributeType.Image) ||
                                          (frontOverlay.contentType ===
                                            "Text" &&
                                            [
                                              AttributeType.FileDownload,
                                              AttributeType.Image,
                                              AttributeType,
                                            ].includes(attribute.type))
                                        }
                                      >
                                        <div className="flex items-center gap-1">
                                          {attribute.name}
                                          <span className="text-xs font-normal text-muted-foreground">
                                            {getLocalizedValue(attribute.displayName, locale)}
                                          </span>
                                        </div>
                                      </SelectItem>
                                    ))}
                                  </SelectContent>
                                </Select>
                              )}
                              {!subField.state.meta.isValid && (
                                <FieldError
                                  errors={subField.state.meta.errors}
                                />
                              )}
                            </Field>
                          );
                        }}
                      </form.AppField>
                      {frontOverlay.contentType === "Text" && (
                        <form.AppField
                          name={`style.frontOverlays[${i}].showLabel`}
                        >
                          {(subField) => {
                            const isInvalid =
                              subField.state.meta.isTouched &&
                              !subField.state.meta.isValid;
                            return (
                              <Field
                                data-invalid={isInvalid}
                                orientation="horizontal"
                              >
                                <Switch
                                  disabled={published}
                                  size="sm"
                                  id={subField.name}
                                  name={subField.name}
                                  checked={subField.state.value}
                                  onCheckedChange={subField.handleChange}
                                  onBlur={field.handleBlur}
                                  aria-invalid={isInvalid}
                                  className="flex-none!"
                                />
                                <FieldLabel
                                  htmlFor={subField.name}
                                  className="font-normal"
                                >
                                  <FormattedMessage
                                    id="credentialSchema.style.showLabel"
                                    defaultMessage="Show label"
                                  />
                                </FieldLabel>
                                {!subField.state.meta.isValid && (
                                  <FieldError
                                    errors={subField.state.meta.errors}
                                  />
                                )}
                              </Field>
                            );
                          }}
                        </form.AppField>
                      )}
                    </div>
                  );
                })}
              </div>
            );
          }}
        </form.AppField>
      </>
    );
  },
});
