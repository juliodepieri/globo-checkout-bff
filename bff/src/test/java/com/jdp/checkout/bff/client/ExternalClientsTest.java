package com.jdp.checkout.bff.client;

import com.jdp.checkout.bff.client.PaymentMethodsClient;
import com.jdp.checkout.bff.exception.ErrorCode;
import com.jdp.checkout.bff.exception.ExternalServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static com.jdp.checkout.bff.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** HTTP clients: translation of the upstream contract and upstream failures. */
class ExternalClientsTest {

    @Test
    void mapsCatalogProductsAndSkipsInactiveOnes() {
        var builder = RestClient.builder().baseUrl("http://catalog");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://catalog/catalog/products")).andRespond(withSuccess("""
                [
                  {"sku":"premium","title":"Plano Premium","summary":"Assinatura mensal com catálogo completo",
                   "price":39.90,"currency":"BRL","active":true},
                  {"sku":"legado","title":"Plano Legado","summary":"x","price":9.90,"currency":"BRL","active":false}
                ]
                """, MediaType.APPLICATION_JSON));

        var products = new ProductCatalogClient(builder.build()).findAll();

        assertThat(products).containsExactly(PREMIUM);
        server.verify();
    }

    @Test
    void upstreamFailureBecomesServiceUnavailable() {
        var builder = RestClient.builder().baseUrl("http://catalog");
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo("http://catalog/catalog/products")).andRespond(withServerError());
        var client = new ProductCatalogClient(builder.build());

        assertThatThrownBy(client::findAll)
                .isInstanceOf(ExternalServiceException.class)
                .extracting(e -> ((ExternalServiceException) e).errorCode())
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void mapsPaymentMethodsAndIgnoresUnknownCodes() {
        var builder = RestClient.builder().baseUrl("http://payments");
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo("http://payments/payment-methods")).andRespond(withSuccess("""
                        [
                          {"code":"CREDIT_CARD","displayName":"Cartão de Crédito","enabled":true},
                          {"code":"DEBIT_CARD","displayName":"Cartão de Débito","enabled":true},
                          {"code":"PIX","displayName":"Pix","enabled":true},
                          {"code":"BOLETO","displayName":"Boleto","enabled":true},
                          {"code":"PIX","displayName":"Pix antigo","enabled":false}
                        ]
                        """, MediaType.APPLICATION_JSON));

        var methods = new PaymentMethodsClient(builder.build()).findAvailable();

        assertThat(methods).containsExactly(CREDIT, DEBIT, PIX);
    }
}
