package com.jdp.checkout.bff.config;

import com.jdp.checkout.bff.controller.OperationsController;
import com.jdp.checkout.bff.dto.request.CheckoutRequest;
import com.jdp.checkout.bff.dto.request.CheckoutRequest.CardInput;
import com.jdp.checkout.bff.dto.request.CheckoutRequest.PaymentMethodInput;
import com.jdp.checkout.bff.dto.request.PreCheckoutRequest;
import com.jdp.checkout.bff.dto.request.PreCheckoutRequest.ProductRef;
import com.jdp.checkout.bff.dto.response.ErrorResponse;
import com.jdp.checkout.bff.exception.ErrorCode;
import com.jdp.checkout.bff.exception.FrontendErrorResolver;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static com.jdp.checkout.bff.exception.ErrorCode.*;

/**
 * OpenAPI / Swagger UI. Error responses and their examples are generated from {@link FrontendErrorResolver},
 * the same source the {@code GlobalExceptionHandler} uses, so the documentation cannot drift from the API.
 */
@Configuration
class OpenApiConfig {

    private static final String JSON = org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
    private static final Locale DOCS_LOCALE = Locale.forLanguageTag("pt-BR");
    private static final UUID SAMPLE_CART = UUID.fromString("3f2b8c1e-7d4a-4f6b-9c2e-1a5d8e7f6b40");

    /** Errors each operation can return. INVALID_REQUEST and INTERNAL_ERROR never carry a cartId. */
    private static final Map<String, List<ErrorCode>> ERRORS = Map.of(
            OperationsController.PRE_CHECKOUT, List.of(INVALID_REQUEST, PRODUCT_NOT_FOUND, SERVICE_UNAVAILABLE, INTERNAL_ERROR),
            OperationsController.CHECKOUT, List.of(INVALID_REQUEST, INVALID_PAYMENT_DATA, CART_NOT_FOUND, CART_ALREADY_PAID,
                    PAYMENT_DECLINED, PAYMENT_METHOD_NOT_ALLOWED, INTERNAL_ERROR));

    @Bean
    OpenAPI checkoutOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Globo Checkout BFF")
                .version("v1")
                .description("""
                        Backend for Frontend of the subscription checkout: the pre-checkout builds the cart for the \
                        payment screen, the checkout pays it.

                        **Simulated payment gateway**
                        - card ending in `0002` → declined (`PAYMENT_DECLINED`)
                        - any other valid, non-expired card → approved
                        - Pix → always approved, with a simulated "copia e cola" code

                        **Errors** — every failure, of every endpoint, returns the same `ErrorResponse` shape; \
                        see the examples under each status code (shown in pt-BR).

                        **Languages** — `frontendError` texts follow `Accept-Language`: `pt-BR` (default) or `en`. \
                        Codes, statuses and routes are the same in every language."""));
    }

    @Bean
    OpenApiCustomizer checkoutExamples(FrontendErrorResolver resolver) {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            ModelConverters.getInstance().readAll(new AnnotatedType(ErrorResponse.class))
                    .forEach(openApi.getComponents()::addSchemas);

            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                addLanguageHeader(operation);
                addErrorResponses(operation, resolver);
                addRequestExamples(operation);
            }));
        };
    }

    private static void addLanguageHeader(Operation operation) {
        operation.addParametersItem(new HeaderParameter()
                .name(HttpHeaders.ACCEPT_LANGUAGE)
                .description("Language of the frontendError texts. pt-BR is the default; unsupported languages fall back to it.")
                .required(false)
                .schema(new StringSchema()._enum(List.of("pt-BR", "en"))._default("pt-BR")));
    }

    private static void addErrorResponses(Operation operation, FrontendErrorResolver resolver) {
        var byStatus = new TreeMap<Integer, MediaType>();
        for (ErrorCode code : ERRORS.getOrDefault(operation.getOperationId(), List.of())) {
            var error = resolver.resolve(code, DOCS_LOCALE);
            UUID cartId = code == INVALID_REQUEST || code == INTERNAL_ERROR
                    || OperationsController.PRE_CHECKOUT.equals(operation.getOperationId()) ? null : SAMPLE_CART;
            byStatus.computeIfAbsent(error.httpStatus(),
                            s -> new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))
                    .addExamples(code.name(), new Example()
                            .summary(error.frontendError().title())
                            .value(new ErrorResponse(error.displayType(), code.name(), cartId, error.frontendError())));
        }
        byStatus.forEach((status, media) -> operation.getResponses().addApiResponse(String.valueOf(status),
                new ApiResponse().description(String.join(", ", media.getExamples().keySet()))
                        .content(new Content().addMediaType(JSON, media))));
    }

    private static void addRequestExamples(Operation operation) {
        if (operation.getRequestBody() == null || operation.getRequestBody().getContent().get(JSON) == null) {
            return;
        }
        var media = operation.getRequestBody().getContent().get(JSON);
        switch (operation.getOperationId()) {
            case OperationsController.PRE_CHECKOUT -> {
                media.addExamples("premium", example("Premium plan",
                        new PreCheckoutRequest(List.of(new ProductRef("premium")))));
                media.addExamples("two-plans", example("Two plans",
                        new PreCheckoutRequest(List.of(new ProductRef("familia"), new ProductRef("basico")))));
                media.addExamples("unknown-plan", example("Unknown plan → 422 PRODUCT_NOT_FOUND",
                        new PreCheckoutRequest(List.of(new ProductRef("ultra")))));
            }
            case OperationsController.CHECKOUT -> {
                media.addExamples("card-approved", example("Credit card, approved",
                        card("Cartão de Crédito", "4111111111114242")));
                media.addExamples("card-declined", example("Card ending in 0002 → 422 PAYMENT_DECLINED",
                        card("Cartão de Crédito", "4111111111110002")));
                media.addExamples("debit-approved", example("Debit card, approved",
                        card("Cartão de Débito", "5555555555554444")));
                media.addExamples("pix", example("Pix, approved with a pixCode",
                        new CheckoutRequest(SAMPLE_CART, new PaymentMethodInput("Pix", null))));
            }
            default -> {
            }
        }
    }

    private static CheckoutRequest card(String method, String number) {
        return new CheckoutRequest(SAMPLE_CART,
                new PaymentMethodInput(method, new CardInput("Ana Souza", number, "12/28", "123")));
    }

    private static Example example(String summary, Object value) {
        return new Example().summary(summary).value(value);
    }
}
