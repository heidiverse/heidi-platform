// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.ws.advice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.Test;
import org.heidiverse.heidi.shared.signing.SigningProviderException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WsExceptionHandlingTest {

    @Test
    void logsRequestBodyValidationFailureAtWarnWithoutRejectedValue() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new ValidationRequest(), "request");
        bindingResult.addError(
                new FieldError(
                        "request",
                        "title",
                        "secret-value",
                        false,
                        null,
                        null,
                        "must not be blank"));
        var method = WsExceptionHandlingTest.class.getDeclaredMethod(
                "validated", ValidationRequest.class);
        var exception = new MethodArgumentNotValidException(
                new MethodParameter(method, 0), bindingResult);
        var request = new MockHttpServletRequest("POST", "/validation");
        var logger = (Logger) LoggerFactory.getLogger(WsExceptionHandling.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);

        try {
            var problem = new WsExceptionHandling().handleMethodArgumentNotValid(exception, request);

            assertEquals(400, problem.getStatus());
            assertEquals("Request validation failed.", problem.getDetail());
            assertTrue(appender.list.stream().anyMatch(event ->
                    event.getLevel() == Level.WARN
                            && event.getFormattedMessage().contains("POST /validation")
                            && event.getFormattedMessage().contains("title: must not be blank")));
            assertFalse(appender.list.stream().anyMatch(event ->
                    event.getFormattedMessage().contains("secret-value")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void mapsIllegalArgumentExceptionToRfc9457Problem() throws Exception {
        var mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new WsExceptionHandling())
                .build();

        mockMvc.perform(get("/failure").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("invalid certificate"));
    }

    @Test
    void resolvesLanguageValidationMessageCodes() throws Exception {
        var mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new WsExceptionHandling())
                .build();

        mockMvc.perform(get("/language-failure").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid content language."));
    }

    @Test
    void mapsSigningProviderFailureToBadGateway() throws Exception {
        var mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new WsExceptionHandling())
                .build();

        mockMvc.perform(get("/provider-failure").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Bad Gateway"))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.detail").value("provider unavailable"));
    }

    @Test
    void mapsDuplicateIdentityDataToConflict() throws Exception {
        var mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new WsExceptionHandling())
                .build();

        mockMvc.perform(get("/duplicate").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "A resource with the same unique values already exists."));
    }

    @RestController
    private static class FailingController {

        @GetMapping("/failure")
        void fail() {
            throw new IllegalArgumentException("invalid certificate");
        }

        @GetMapping("/language-failure")
        void languageFail() {
            throw new IllegalArgumentException("tenant.languages.invalid");
        }

        @GetMapping("/provider-failure")
        void providerFail() {
            throw new SigningProviderException("provider unavailable");
        }

        @GetMapping("/duplicate")
        void duplicate() {
            throw new DataIntegrityViolationException("duplicate key");
        }
    }

    private void validated(ValidationRequest request) {}

    private static class ValidationRequest {}
}
