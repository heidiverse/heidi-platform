// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { createFormHook, createFormHookContexts } from "@tanstack/react-form";
import { Checkbox } from "./components/checkbox";
import { TextInput } from "./components/input";
import { Select } from "./components/select";

export const { fieldContext, formContext, useFieldContext } =
  createFormHookContexts();

export const { useAppForm, withForm } = createFormHook({
  fieldContext,
  formContext,
  fieldComponents: {
    TextInput,
    Select,
    Checkbox,
  },
  formComponents: {},
});
