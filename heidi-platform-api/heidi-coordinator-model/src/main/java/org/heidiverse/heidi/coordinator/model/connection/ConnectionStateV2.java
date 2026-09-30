// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.connection;

public record ConnectionStateV2(
        String connectionId,
        ConnectionStateEnum state,
        Object disclosures,
        Boolean validationResult,
        boolean terminal) {

    public ConnectionStateV2(
            final String connectionId, final ConnectionStateEnum state, final Object disclosures) {
        this(connectionId, state, disclosures, null, isTerminal(state));
    }

    public ConnectionStateV2(
            final String connectionId,
            final ConnectionStateEnum state,
            final Object disclosures,
            final Boolean validationResult) {
        this(connectionId, state, disclosures, validationResult, isTerminal(state));
    }

    private static boolean isTerminal(final ConnectionStateEnum state) {
        return switch (state) {
            case CREDENTIAL_ACCEPTED,
                    CREDENTIAL_VALIDATION_FAILED,
                    ERROR_POLL_CREDENTIAL_VERIFICATION,
                    CREDENTIAL_REJECTED ->
                    true;
            case NOT_STARTED, STARTED -> false;
        };
    }

    public enum ConnectionStateEnum {
        NOT_STARTED,
        STARTED,
        // Additional states for presentation
        CREDENTIAL_REJECTED,
        CREDENTIAL_ACCEPTED,
        CREDENTIAL_VALIDATION_FAILED,
        ERROR_POLL_CREDENTIAL_VERIFICATION
    }
}
