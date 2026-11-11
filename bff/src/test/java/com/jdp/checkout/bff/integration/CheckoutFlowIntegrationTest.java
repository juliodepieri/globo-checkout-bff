package com.jdp.checkout.bff.integration;

import com.jdp.checkout.bff.client.PaymentMethodsClient;
import com.jdp.checkout.bff.client.ProductCatalogClient;
import com.jdp.checkout.bff.model.CartStatus;
import com.jdp.checkout.bff.repository.CartRepository;
import com.jdp.checkout.bff.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static com.jdp.checkout.bff.Fixtures.CATALOG;
import static com.jdp.checkout.bff.Fixtures.METHODS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full flow against real PostgreSQL and RabbitMQ (Testcontainers). The external product and
 * payment-method clients are mocked; their HTTP mapping has its own test (ExternalClientsTest).
 * Skipped automatically when Docker is not available. Each test creates its own cart, so tests are independent.
 *
 * <p>Business rules of the challenge and the test that proves each one:
 * <ol>
 *   <li>Checkout only for a cartId created by the pre-checkout: {@link Checkout#unknownCartIsRejected}</li>
 *   <li>Payment method must be one returned for that cart: {@link Checkout#paymentMethodNotOfferedForTheCartIsRejected}</li>
 *   <li>Unknown product returns the frontendError format: {@link PreCheckout#unknownProductReturnsFrontendError}</li>
 *   <li>Card ending in 0002 is declined: {@link Checkout#cardEndingIn0002IsDeclined}</li>
 *   <li>An approved cart cannot be paid again: {@link Checkout#approvedCartCannotBePaidAgain},
 *       {@link Checkout#concurrentCheckoutsOfTheSameCartApproveOnlyOnce}</li>
 *   <li>Full card number and CVV are never stored; success returns only the last digits:
 *       {@link Checkout#approvedCardReturnsOnlyTheLastDigits}, {@link CardData}</li>
 * </ol>
 */
@SpringBootTest(properties = {"spring.cache.type=none", "checkout.outbox.relay-interval=200ms"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CheckoutFlowIntegrationTest {

    private static final String APPROVED_CARD = "4111111111114242";
    private static final String DECLINED_CARD = "4111111111110002";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management-alpine");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CartRepository carts;
    @Autowired OutboxEventRepository outbox;
    @MockitoBean ProductCatalogClient productCatalog;
    @MockitoBean PaymentMethodsClient paymentMethods;

    @BeforeEach
    void externalServices() {
        when(productCatalog.findAll()).thenReturn(CATALOG);
        when(paymentMethods.findAvailable()).thenReturn(METHODS);
    }

    @Nested
    class PreCheckout {

        @Test
        void createsCartWithProductsAndPaymentMethods() throws Exception {
            preCheckoutRequest("premium")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cartId").isNotEmpty())
                    .andExpect(jsonPath("$.products[0].slug").value("premium"))
                    .andExpect(jsonPath("$.paymentMethods.length()").value(METHODS.size()));
        }

        @Test
        void unknownProductReturnsFrontendError() throws Exception {
            // Regra: "Produto inexistente no pré-checkout devolve erro no padrão frontendError."
            preCheckoutRequest("ultra")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"))
                    .andExpect(jsonPath("$.frontendError.code").value("P-1002"))
                    .andExpect(jsonPath("$.frontendError.title").isNotEmpty())
                    .andExpect(jsonPath("$.frontendError.primaryButton.label").isNotEmpty());
        }
    }

    @Nested
    class Checkout {

        @Test
        void approvedCardReturnsOnlyTheLastDigits() throws Exception {
            UUID cartId = preCheckout("premium");

            // Regra: "No sucesso, devolva só os dados necessários para a tela, como os últimos dígitos."
            payWithCard(cartId, APPROVED_CARD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APROVADO"))
                    .andExpect(jsonPath("$.payment.lastDigits").value("4242"))
                    .andExpect(jsonPath("$.payment.amount").value(39.90));
        }

        @Test
        void pixIsApprovedWithAPixCode() throws Exception {
            UUID cartId = preCheckout("familia", "basico");

            payWith(cartId, "Pix")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.payment.method").value("Pix"))
                    .andExpect(jsonPath("$.payment.pixCode").isNotEmpty())
                    .andExpect(jsonPath("$.payment.amount").value(79.80));
        }

        @Test
        void cardEndingIn0002IsDeclined() throws Exception {
            UUID cartId = preCheckout("premium");

            // Regra: "Cartão terminado em 0002 deve ser recusado, para o frontend conseguir demonstrar a tela de erro."
            payWithCard(cartId, DECLINED_CARD)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errorCode").value("PAYMENT_DECLINED"))
                    .andExpect(jsonPath("$.frontendError.code").value("P-1001"));
        }

        @Test
        void declinedCartCanBeRetriedWithAnotherMethod() throws Exception {
            UUID cartId = preCheckout("premium");
            payWithCard(cartId, DECLINED_CARD).andExpect(status().isUnprocessableContent());

            // A declined cart is not "approved", so it can still be paid; the declined attempt is kept.
            assertThat(cartStatus(cartId)).isEqualTo(CartStatus.DECLINED);
            payWith(cartId, "Pix").andExpect(status().isOk());
            assertThat(paymentStatuses(cartId)).containsExactly("DECLINED", "APPROVED");
        }

        @Test
        void approvedCartCannotBePaidAgain() throws Exception {
            UUID cartId = preCheckout("premium");
            payWithCard(cartId, APPROVED_CARD).andExpect(status().isOk());

            // Regra: "Um carrinho já aprovado não pode ser pago de novo."
            payWith(cartId, "Pix")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CART_ALREADY_PAID"))
                    .andExpect(jsonPath("$.cartId").value(cartId.toString()));
        }

        @Test
        void concurrentCheckoutsOfTheSameCartApproveOnlyOnce() throws Exception {
            // Regra: "Um carrinho já aprovado não pode ser pago de novo." — even with 5 checkouts at the same time.
            UUID cartId = preCheckout("premium");

            Callable<Integer> attempt = () -> payWithCard(cartId, APPROVED_CARD).andReturn().getResponse().getStatus();
            try (var executor = Executors.newFixedThreadPool(5)) {
                var statuses = executor.invokeAll(List.of(attempt, attempt, attempt, attempt, attempt)).stream()
                        .map(f -> {
                            try {
                                return f.get();
                            } catch (Exception e) {
                                throw new IllegalStateException(e);
                            }
                        })
                        .toList();

                assertThat(statuses).containsOnlyOnce(200).filteredOn(s -> s != 200).containsOnly(409);
            }
            assertThat(approvedPayments(cartId)).isEqualTo(1);
        }

        @Test
        void unknownCartIsRejected() throws Exception {
            // Regra: "O checkout só acontece para um cartId criado no pré-checkout."
            payWith(UUID.randomUUID(), "Pix")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("CART_NOT_FOUND"));
        }

        @Test
        void paymentMethodNotOfferedForTheCartIsRejected() throws Exception {
            UUID cartId = preCheckout("basico");

            // Regra: "O meio de pagamento precisa ser um dos devolvidos no pré-checkout daquele carrinho."
            payWith(cartId, "Boleto")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errorCode").value("PAYMENT_METHOD_NOT_ALLOWED"))
                    .andExpect(jsonPath("$.cartId").value(cartId.toString()));
        }
    }

    // Regra: "Não persista o número completo do cartão nem o CVV."
    @Nested
    class CardData {

        @Test
        void fullNumberAndCvvAreNeverStored() throws Exception {
            UUID cartId = preCheckout("premium");
            payWithCard(cartId, APPROVED_CARD).andExpect(status().isOk());

            // Raw SQL on purpose: the check is about the physical table, which a JPA read would not show.
            var row = paymentRow(cartId);
            assertThat(row.get("last_digits")).isEqualTo("4242");
            assertThat(row.values().toString()).doesNotContain(APPROVED_CARD);
            assertThat(row.keySet()).containsExactlyInAnyOrder("id", "cart_id", "method_type", "method_name", "status",
                    "amount", "currency", "last_digits", "pix_code", "decline_reason", "processed_at");
        }

        @Test
        void publishedEventHasNoCardData() throws Exception {
            UUID cartId = preCheckout("premium");
            payWithCard(cartId, APPROVED_CARD).andExpect(status().isOk());

            // The event goes through the outbox and reaches RabbitMQ without the card data.
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertThat(outbox.findByAggregateId(cartId))
                            .singleElement()
                            .satisfies(e -> {
                                assertThat(e.eventType()).isEqualTo("payment.approved");
                                assertThat(e.publishedAt()).isNotNull();
                                assertThat(e.payload()).doesNotContain(APPROVED_CARD).doesNotContainIgnoringCase("cvv");
                            }));
        }
    }

    @Nested
    class OpenApi {

        @Test
        void documentsEveryErrorWithTheRealFrontendError() throws Exception {
            String checkout = "$.paths['/operations/checkout'].post.responses";
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info.title").value("Globo Checkout BFF"))
                    .andExpect(jsonPath(checkout + "['200']").exists())
                    .andExpect(jsonPath(checkout + "['409'].content['application/json'].examples.CART_ALREADY_PAID.value.frontendError.code")
                            .value("P-1006"))
                    .andExpect(jsonPath(checkout + "['422'].content['application/json'].examples.PAYMENT_DECLINED.value.frontendError.secondaryButton.label")
                            .value("Trocar meio de pagamento"))
                    .andExpect(jsonPath("$.paths['/operations/pre-checkout'].post.responses['503']").exists())
                    .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists())
                    .andExpect(jsonPath("$.components.schemas.CheckoutResponse.properties.status.enum").value(contains("APROVADO")));
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }
    }

    // --- API calls -------------------------------------------------------------------------------------------

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions preCheckoutRequest(String... slugs) throws Exception {
        var products = String.join(",", Arrays.stream(slugs).map(s -> "{\"slug\":\"" + s + "\"}").toList());
        return postJson("/operations/pre-checkout", "{\"products\":[" + products + "]}");
    }

    private UUID preCheckout(String... slugs) throws Exception {
        var json = preCheckoutRequest(slugs).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(json, "$.cartId"));
    }

    private ResultActions payWithCard(UUID cartId, String number) throws Exception {
        return postJson("/operations/checkout", """
                {"cartId":"%s","paymentMethod":{"name":"Cartão de Crédito",
                 "card":{"holderName":"Ana Souza","number":"%s","expiry":"12/28","cvv":"123"}}}
                """.formatted(cartId, number));
    }

    private ResultActions payWith(UUID cartId, String methodName) throws Exception {
        return postJson("/operations/checkout",
                "{\"cartId\":\"" + cartId + "\",\"paymentMethod\":{\"name\":\"" + methodName + "\"}}");
    }

    // --- Database reads: repository for domain state, SQL only where the stored data itself is the point ----------

    private CartStatus cartStatus(UUID cartId) {
        return carts.findById(cartId).orElseThrow().status();
    }

    private Map<String, Object> paymentRow(UUID cartId) {
        return jdbc.queryForMap("select * from payment where cart_id = ?", cartId);
    }

    private List<String> paymentStatuses(UUID cartId) {
        return jdbc.queryForList("select status from payment where cart_id = ? order by processed_at", String.class, cartId);
    }

    private long approvedPayments(UUID cartId) {
        return jdbc.queryForObject("select count(*) from payment where cart_id = ? and status = 'APPROVED'", Long.class, cartId);
    }
}
