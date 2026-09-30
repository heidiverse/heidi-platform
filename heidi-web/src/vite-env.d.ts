// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

/// <reference types="vite/client" />

declare module "virtual:heidi-web-extension";

interface ImportMetaEnv {
  readonly VITE_HEIDI_API_BASE_URL: string;
  readonly VITE_HEIDI_WEB_APP_NAME: string;
  readonly VITE_HEIDI_DEFAULT_NAMESPACE: string;
  readonly VITE_HEIDI_DEFAULT_DOCTYPE: string;

  // set in package.json
  readonly VITE_APP_VERSION: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
