// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import type { MessageDescriptor } from "react-intl";
import {
  AttributeType,
  type AttributeType as AttributeTypeValue,
} from "@/types/credential-schema";

export const credentialSchemaValidationMessages = {
  validationIdRequired: {
    id: "credentialSchema.validation.idRequired",
    defaultMessage: "Id is required",
  },
  validationKeyRequired: {
    id: "credentialSchema.validation.keyRequired",
    defaultMessage: "You have to enter a key",
  },
  validationDisplayNameRequired: {
    id: "credentialSchema.validation.displayNameRequired",
    defaultMessage: "Please enter at least 1 Display Name",
  },
  validationAttributeTypeInvalid: {
    id: "credentialSchema.validation.attributeTypeInvalid",
    defaultMessage: "Please select a valid attribute type",
  },
  validationFrontOverlayContentRequired: {
    id: "credentialSchema.validation.frontOverlayContentRequired",
    defaultMessage: "Please select something to display",
  },
  validationKeyTypeInvalid: {
    id: "credentialSchema.validation.keyTypeInvalid",
    defaultMessage: "Please select a valid key type",
  },
  validationSchemaDisplayNameRequired: {
    id: "credentialSchema.validation.schemaDisplayNameRequired",
    defaultMessage: "Please enter a display name",
  },
} as const satisfies Record<string, MessageDescriptor>;

const attributeTypeMessages: Record<AttributeTypeValue, MessageDescriptor> = {
  [AttributeType.String]: {
    id: "credentialSchema.attributeType.string",
    defaultMessage: "String",
  },
  [AttributeType.Number]: {
    id: "credentialSchema.attributeType.number",
    defaultMessage: "Number",
  },
  [AttributeType.Date]: {
    id: "credentialSchema.attributeType.date",
    defaultMessage: "Date",
  },
  [AttributeType.DateOfBirth]: {
    id: "credentialSchema.attributeType.dateOfBirth",
    defaultMessage: "Date of Birth",
  },
  [AttributeType.DateTime]: {
    id: "credentialSchema.attributeType.dateTime",
    defaultMessage: "Datetime",
  },
  [AttributeType.Time]: {
    id: "credentialSchema.attributeType.time",
    defaultMessage: "Time",
  },
  [AttributeType.Boolean]: {
    id: "credentialSchema.attributeType.boolean",
    defaultMessage: "Boolean",
  },
  [AttributeType.Image]: {
    id: "credentialSchema.attributeType.image",
    defaultMessage: "Image",
  },
  [AttributeType.Location]: {
    id: "credentialSchema.attributeType.location",
    defaultMessage: "Location",
  },
  [AttributeType.Other]: {
    id: "credentialSchema.attributeType.other",
    defaultMessage: "Other",
  },
  [AttributeType.Link]: {
    id: "credentialSchema.attributeType.link",
    defaultMessage: "Link",
  },
  [AttributeType.FileDownload]: {
    id: "credentialSchema.attributeType.fileDownload",
    defaultMessage: "File download",
  },
  [AttributeType.Mail]: {
    id: "credentialSchema.attributeType.mail",
    defaultMessage: "Mail",
  },
  [AttributeType.Phone]: {
    id: "credentialSchema.attributeType.phone",
    defaultMessage: "Phone",
  },
};

export function getAttributeTypeMessage(type: AttributeTypeValue) {
  return attributeTypeMessages[type];
}
