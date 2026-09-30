// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { queryOptions } from "@tanstack/react-query";
import {
  getTemplate,
  getTemplateLibraries,
  getTemplates,
} from "@/lib/api/templates/api";

export function templateLibraryListOptions() {
  return queryOptions({
    queryKey: ["template-libraries"],
    queryFn: getTemplateLibraries,
  });
}

export function templateListOptions() {
  return queryOptions({
    queryKey: ["templates"],
    queryFn: getTemplates,
  });
}

export function templateOptions(templateId: string) {
  return queryOptions({
    queryKey: ["template", templateId],
    queryFn: () => getTemplate(templateId),
  });
}
