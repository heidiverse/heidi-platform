// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { format, formatISO } from "date-fns";
import { AttributeType, type AttributeType as AttributeTypeValue } from "@/types/credential-schema";

/** Sensible values used when a flow needs to populate every attribute type. */
export const defaultValuesForType: Partial<Record<AttributeTypeValue, string>> = {
  [AttributeType.Boolean]: "true",
  [AttributeType.Number]: "1",
  [AttributeType.Date]: format(new Date(), "yyyy-MM-dd"),
  [AttributeType.DateOfBirth]: "1990-08-15",
  [AttributeType.DateTime]: formatISO(new Date()),
  [AttributeType.Time]: format(new Date(), "HH:mm:ssXXX"),
};
