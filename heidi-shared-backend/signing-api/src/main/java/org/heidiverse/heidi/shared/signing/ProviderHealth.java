// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

/**
 * Result of a provider liveness probe.
 *
 * <p>Reported rather than thrown: a degraded provider should surface in an operator view without
 * taking down the caller, and several providers are probed together.
 */
public record ProviderHealth(boolean healthy, Long latencyMillis, String error) {

    public static ProviderHealth up(long latencyMillis) {
        return new ProviderHealth(true, latencyMillis, null);
    }

    public static ProviderHealth down(String error) {
        return new ProviderHealth(false, null, error);
    }
}
