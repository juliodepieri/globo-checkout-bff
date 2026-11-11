package com.jdp.checkout.bff.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Contract guard: every error the BFF can emit renders the same, complete frontendError, in every language. */
class FrontendErrorResolverTest {

    static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final FrontendErrorResolver resolver = new FrontendErrorResolver(messageSource());

    /** Same setup as Spring Boot's (spring.messages.* in application.yml). */
    static ResourceBundleMessageSource messageSource() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void everyErrorCodeHasACompleteFrontendErrorInBothLanguages(ErrorCode code) {
        for (Locale locale : new Locale[]{PT_BR, Locale.ENGLISH}) {
            var resolved = resolver.resolve(code, locale);
            var error = resolved.frontendError();

            assertThat(resolved.httpStatus()).isBetween(400, 599);
            assertThat(resolved.displayType()).isNotNull();
            assertThat(error.code()).matches("P-\\d{4}");
            assertThat(error.title()).isNotBlank();
            assertThat(error.description()).isNotBlank();
            assertThat(error.primaryButton().label()).isNotBlank();
            assertThat(error.primaryButton().url()).isNotBlank();
        }
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void onlyTheTextsChangeWithTheLanguage(ErrorCode code) {
        var pt = resolver.resolve(code, PT_BR);
        var en = resolver.resolve(code, Locale.ENGLISH);

        assertThat(en.httpStatus()).isEqualTo(pt.httpStatus());
        assertThat(en.displayType()).isEqualTo(pt.displayType());
        assertThat(en.frontendError().code()).isEqualTo(pt.frontendError().code());
        assertThat(en.frontendError().primaryButton().url()).isEqualTo(pt.frontendError().primaryButton().url());
        assertThat(en.frontendError().title()).isNotEqualTo(pt.frontendError().title());
    }

    @Test
    void englishBundleTranslatesEveryKey() throws IOException {
        // A key missing in English would silently fall back to Portuguese; this catches it.
        assertThat(load("messages_en.properties").stringPropertyNames())
                .isEqualTo(load("messages.properties").stringPropertyNames());
    }

    @Test
    void unsupportedLanguageFallsBackToPortuguese() {
        assertThat(resolver.resolve(ErrorCode.PAYMENT_DECLINED, Locale.FRENCH).frontendError().title())
                .isEqualTo("Pagamento não aprovado");
    }

    @Test
    void frontendCodesAreUnique() {
        var codes = Arrays.stream(ErrorCode.values())
                .map(c -> resolver.resolve(c, PT_BR).frontendError().code()).toList();

        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    void businessFailuresUse4xx() {
        for (var code : new ErrorCode[]{ErrorCode.PRODUCT_NOT_FOUND, ErrorCode.CART_NOT_FOUND,
                ErrorCode.PAYMENT_DECLINED, ErrorCode.CART_ALREADY_PAID, ErrorCode.PAYMENT_METHOD_NOT_ALLOWED,
                ErrorCode.INVALID_PAYMENT_DATA, ErrorCode.INVALID_REQUEST}) {
            assertThat(resolver.resolve(code, PT_BR).httpStatus()).as(code.name()).isBetween(400, 499);
        }
    }

    private static Properties load(String name) throws IOException {
        var properties = new Properties();
        try (var in = FrontendErrorResolverTest.class.getClassLoader().getResourceAsStream(name)) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
