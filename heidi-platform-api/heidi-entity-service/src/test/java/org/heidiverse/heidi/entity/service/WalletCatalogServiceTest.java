// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.heidiverse.heidi.entity.model.tenant.WalletCatalogEntry;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

import java.util.List;

class WalletCatalogServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void importsProductionWalletsAndRejectsUnsafeEntries() throws Exception {
        var service =
                new WalletCatalogService(
                        mock(RestClient.Builder.class),
                        objectMapper,
                        "https://open-it.in/.well-known/web-components-config.json",
                        false);

        List<WalletCatalogEntry> wallets =
                service.parse(
                        objectMapper.readTree(
                                """
                                {
                                  "supportedWallets": [
                                    {
                                      "name": "heidi_wallet",
                                      "displayName": "Heidi",
                                      "appIconUri": "data:image/jpeg;base64,/9j/4AAQ",
                                      "universalLink": "https://now.open-it.in/heidi-wallet"
                                    },
                                    {
                                      "name": "heidi_wallet",
                                      "displayName": "Heidi DEV",
                                      "appIconUri": "https://example.org/icon.png",
                                      "universalLink": "https://now-dev.open-it.in/heidi-wallet"
                                    },
                                    {
                                      "name": "unsafe",
                                      "displayName": "Unsafe",
                                      "appIconUri": "javascript:alert(1)",
                                      "universalLink": "https://example.org/open"
                                    }
                                  ]
                                }
                                """));

        assertEquals(1, wallets.size());
        assertEquals("heidi_wallet", wallets.getFirst().name());
        assertTrue(wallets.getFirst().appIconUri().startsWith("data:image/jpeg"));
    }

    @Test
    void givesDevelopmentDuplicatesAStableUniqueIdentifierWhenEnabled() throws Exception {
        var service =
                new WalletCatalogService(
                        mock(RestClient.Builder.class),
                        objectMapper,
                        "https://open-it.in/.well-known/web-components-config.json",
                        true);

        List<WalletCatalogEntry> wallets =
                service.parse(
                        objectMapper.readTree(
                                """
                                {
                                  "supportedWallets": [
                                    {
                                      "name": "heidi_wallet",
                                      "displayName": "Heidi",
                                      "appIconUri": "https://example.org/icon.png",
                                      "universalLink": "https://now.open-it.in/heidi-wallet"
                                    },
                                    {
                                      "name": "heidi_wallet",
                                      "displayName": "Heidi DEV",
                                      "appIconUri": "https://example.org/icon.png",
                                      "universalLink": "https://now-dev.open-it.in/heidi-wallet"
                                    }
                                  ]
                                }
                                """));

        assertEquals(List.of("heidi_wallet", "heidi_wallet_dev"),
                wallets.stream().map(WalletCatalogEntry::name).toList());
    }
}
