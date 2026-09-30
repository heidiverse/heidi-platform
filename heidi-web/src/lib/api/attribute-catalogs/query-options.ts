// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import { getAttributeCatalogs } from "@/lib/api/attribute-catalogs/api";

export function attributeCatalogListOptions() {
  return {
    queryKey: ["attribute-catalogs"],
    queryFn: getAttributeCatalogs,
  };
}
