// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.data.sentry.config;

import org.heidiverse.heidi.verifier.data.sentry.SentryUtil;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.SentryException;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SentryConfig implements SentryOptions.BeforeSendCallback {

    private final List<String> exceptionsToIgnore =
            List.of(
                    "NoSuchElementException",
                    "NoResourceFoundException",
                    "VerificationResponseNotFoundException");

    private final List<String> loggersToIgnore =
            List.of("org.springframework.web.servlet.PageNotFound");

    private static boolean eventContainsSentryIgnoreTag(SentryEvent event) {
        if (event.getMessage() == null) {
            return false;
        }

        if (event.getMessage().getFormatted() != null
                && event.getMessage().getFormatted().contains(SentryUtil.SENTRY_IGNORE_TAG)) {
            return true;
        }

        return event.getMessage().getMessage() != null
                && event.getMessage().getMessage().contains(SentryUtil.SENTRY_IGNORE_TAG);
    }

    @Override
    public SentryEvent execute(final SentryEvent event, final Hint hint) {
        final List<SentryException> exceptions = event.getExceptions();
        if (containsExceptionToIgnore(exceptions)
                || containsLoggerToIgnore(event)
                || eventContainsSentryIgnoreTag(event)) {
            return null;
        }
        return event;
    }

    private boolean containsLoggerToIgnore(SentryEvent event) {
        return event.getLogger() != null && loggersToIgnore.contains(event.getLogger());
    }

    private boolean containsExceptionToIgnore(List<SentryException> exceptions) {
        return exceptions != null
                && exceptions.stream().anyMatch(e -> exceptionsToIgnore.contains(e.getType()));
    }
}
