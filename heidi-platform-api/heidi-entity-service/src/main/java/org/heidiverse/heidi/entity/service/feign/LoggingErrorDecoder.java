// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service.feign;

import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class LoggingErrorDecoder implements ErrorDecoder {

    private static final Logger log = LoggerFactory.getLogger(LoggingErrorDecoder.class);
    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        String body = "";
        try (Reader reader = response.body().asReader(StandardCharsets.UTF_8)) {
            body = Util.toString(reader);
        } catch (IOException ignored) {
        }
        log.error(
                "[DE Trust Registry] Feign error on {} → status={} headers={} body={}",
                methodKey,
                response.status(),
                response.headers(),
                body);
        return defaultDecoder.decode(methodKey, response);
    }
}
