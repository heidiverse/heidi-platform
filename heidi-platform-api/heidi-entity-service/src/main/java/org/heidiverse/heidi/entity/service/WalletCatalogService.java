// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import org.heidiverse.heidi.entity.model.tenant.WalletCatalogEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Imports the public wallet catalog and keeps the last-known-good snapshot in memory. */
@Service
public class WalletCatalogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WalletCatalogService.class);
    private static final int MAX_ENTRIES = 100;
    private static final int MAX_FIELD_LENGTH = 256;
    private static final int MAX_ICON_LENGTH = 512_000;
    private static final Pattern RASTER_DATA_ICON =
            Pattern.compile("^data:image/(?:png|jpeg|webp);base64,[A-Za-z0-9+/=]+$");

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final String catalogUrl;
    private final boolean includeDevelopment;
    private volatile List<WalletCatalogEntry> wallets = List.of();
    private volatile boolean refreshAttempted;

    public WalletCatalogService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${heidi.platform.wallet-catalog.url:https://open-it.in/.well-known/web-components-config.json}")
                    String catalogUrl,
            @Value("${heidi.platform.wallet-catalog.include-development:false}")
                    boolean includeDevelopment) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.catalogUrl = requireSupportedCatalogUrl(catalogUrl);
        this.includeDevelopment = includeDevelopment;
    }

    /** Returns the current snapshot, refreshing lazily when the process has no snapshot yet. */
    public List<WalletCatalogEntry> getWallets() {
        if (!refreshAttempted) {
            refresh();
        }
        return wallets;
    }

    /** Builds the platform-level client configuration used when an organisation has no override. */
    public JsonNode getDefaultClientConfiguration() {
        ObjectNode configuration = objectMapper.createObjectNode();
        ObjectNode wallet = configuration.putObject("wallet");
        wallet.set("supportedWallets", objectMapper.valueToTree(getWallets()));
        return configuration;
    }

    @Scheduled(fixedDelayString = "${heidi.platform.wallet-catalog.refresh-interval-ms:21600000}")
    public synchronized void refresh() {
        refreshAttempted = true;
        if (catalogUrl.isBlank()) {
            wallets = List.of();
            LOGGER.info("Wallet catalog import is disabled");
            return;
        }
        try {
            JsonNode root = restClientBuilder.clone().build().get().uri(catalogUrl).retrieve().body(JsonNode.class);
            List<WalletCatalogEntry> imported = parse(root);
            wallets = imported;
            LOGGER.info("Imported {} wallet entries from the configured catalog", imported.size());
        } catch (Exception exception) {
            LOGGER.warn(
                    "Could not refresh wallet catalog; retaining the last-known-good snapshot of {} entries",
                    wallets.size(),
                    exception);
        }
    }

    List<WalletCatalogEntry> parse(JsonNode root) {
        if (root == null || !root.isObject() || !root.path("supportedWallets").isArray()) {
            throw new IllegalArgumentException("Wallet catalog must contain a supportedWallets array");
        }

        List<WalletCatalogEntry> imported = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (JsonNode candidate : root.path("supportedWallets")) {
            if (imported.size() >= MAX_ENTRIES || !candidate.isObject()) {
                break;
            }
            String name = text(candidate, "name");
            String displayName = text(candidate, "displayName");
            String appIconUri = text(candidate, "appIconUri");
            String universalLink = text(candidate, "universalLink");
            if (!isValidField(name)
                    || !isValidField(displayName)
                    || !isValidIcon(appIconUri)
                    || !isValidUniversalLink(universalLink)
                    || (!includeDevelopment && isDevelopmentLink(universalLink))) {
                continue;
            }

            String uniqueName = uniqueName(name, names, isDevelopmentLink(universalLink));
            if (uniqueName == null) {
                continue;
            }
            names.add(uniqueName);
            imported.add(new WalletCatalogEntry(uniqueName, displayName, appIconUri, universalLink));
        }

        if (imported.isEmpty()) {
            throw new IllegalArgumentException("Wallet catalog contains no valid wallet entries");
        }
        return List.copyOf(imported);
    }

    private String uniqueName(String name, Set<String> names, boolean development) {
        if (!names.contains(name)) {
            return name;
        }
        String suffix = development ? "_dev" : "_2";
        String candidate = name + suffix;
        return names.contains(candidate) ? null : candidate;
    }

    private String text(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value != null && value.isString() ? value.asString().trim() : "";
    }

    private boolean isValidField(String value) {
        return !value.isBlank() && value.length() <= MAX_FIELD_LENGTH;
    }

    private boolean isValidIcon(String value) {
        if (value.length() > MAX_ICON_LENGTH) {
            return false;
        }
        if (RASTER_DATA_ICON.matcher(value).matches()) {
            return true;
        }
        return isHttpsUrl(value);
    }

    private boolean isValidUniversalLink(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isDevelopmentLink(String value) {
        try {
            String host = URI.create(value).getHost();
            return host != null && host.toLowerCase(Locale.ROOT).contains("-dev");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String requireSupportedCatalogUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(value);
            boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                    && Set.of("localhost", "127.0.0.1", "::1").contains(uri.getHost());
            if (!(isHttpsUrl(value) || localHttp)) {
                throw new IllegalArgumentException(
                        "Wallet catalog URL must use HTTPS (or HTTP on localhost)");
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid wallet catalog URL", exception);
        }
    }
}
